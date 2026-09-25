package jp.co.translacat.infrastructure.languagelearning.growth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.languagelearning.growth.model.GrowthOperation;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.global.config.QueryDslConfig;
import jp.co.translacat.support.GrowthTestConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties="spring.datasource.url=jdbc:h2:mem:growth-cutover;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER")
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
@Import({QueryDslConfig.class,GrowthTestConfiguration.class,GrowthOutboxJpaIntegrationTest.JsonConfiguration.class})
class GrowthOutboxJpaIntegrationTest {
    private static final String SOURCE="1ae93ac7-179b-4700-9edb-eb001461f033";
    @Autowired PlatformTransactionManager manager;
    @Autowired UserRepository users;
    @Autowired CoreGrowthCollector collector;
    @Autowired GrowthOutboxStore store;
    @Autowired JdbcTemplate jdbc;
    private TransactionTemplate tx;
    @BeforeEach void setup(){tx=new TransactionTemplate(manager);}
    private User user(){String u=UUID.randomUUID().toString().replace("-","");return users.saveAndFlush(User.createLocalUser(u+"@growth.test","pw","growth",Role.USER,u.substring(0,20)));}
    private GrowthOperation operation(String key){return new GrowthOperation(key,"SIGNALS_TOUCHED",Map.of("type","STRENGTH","values",List.of("명확한 전달")));}
    private long count(long id){return jdbc.queryForObject("SELECT COUNT(*) FROM language_learning_growth_outbox WHERE user_id=?",Long.class,id);}
    private long append(){return tx.execute(s->{long id=user().getId();collector.append(id,operation("signal:1"));assertThat(count(id)).isZero();return id;});}
    private GrowthAcknowledgement ack(GrowthOutboxStore.Claim c){var e=c.event();return new GrowthAcknowledgement(SOURCE,e.eventId(),e.userId(),e.sequence(),e.payloadSha256(),"APPLIED");}

    @Test void commitPersistsJpaAndOutboxTogether(){long id=append();assertThat(users.findById(id)).isPresent();assertThat(count(id)).isEqualTo(1);assertThat(store.committedSequence(SOURCE,id)).isEqualTo(1);}
    @Test void outerRollbackPersistsNeitherUserNorOutbox(){long[] id={0};assertThatThrownBy(()->tx.executeWithoutResult(s->{id[0]=user().getId();collector.append(id[0],operation("signal:1"));throw new IllegalStateException("rollback");})).isInstanceOf(IllegalStateException.class);assertThat(users.findById(id[0])).isEmpty();assertThat(count(id[0])).isZero();}
    @Test void invalidOutboundContractFailsBeforeCommitAndRollsBackJpa(){long[] id={0};assertThatThrownBy(()->tx.executeWithoutResult(s->{id[0]=user().getId();collector.append(id[0],new GrowthOperation("bad:1","UNKNOWN_KIND",Map.of()));})).isInstanceOf(IllegalArgumentException.class);assertThat(users.findById(id[0])).isEmpty();assertThat(count(id[0])).isZero();}
    @Test void snapshotIsIndependentOfMutableDraftAfterCapture(){tx.executeWithoutResult(s->{long id=user().getId();Map<String,Object> p=new HashMap<>();p.put("type","STRENGTH");p.put("values",new ArrayList<>(List.of("처음")));collector.stage(id,"signal:1",()->new GrowthOperation("signal:1","SIGNALS_TOUCHED",p));var snap=collector.preview(id);p.put("values",List.of("나중"));assertThat(snap.getFirst().payload().get("values")).isEqualTo(List.of("처음"));s.setRollbackOnly();});}
    @Test void suspendedTransactionKeepsItsPendingCommandsSeparate(){tx.executeWithoutResult(s->{long id=user().getId();collector.append(id,operation("outer:1"));var suspended=new TransactionTemplate(manager);suspended.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);suspended.executeWithoutResult(n->{assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();assertThat(collector.preview(id)).isEmpty();assertThat(store.committedSequence(SOURCE,id)).isZero();});assertThat(collector.preview(id)).hasSize(1);s.setRollbackOnly();});}
    @Test void appendOutsideCoreTransactionIsForbidden(){assertThatThrownBy(()->collector.append(123,operation("x"))).isInstanceOf(IllegalStateException.class);assertThatThrownBy(()->store.append(SOURCE,123,"{}" )).isInstanceOf(IllegalStateException.class);}
    @Test void readOnlyTransactionCannotStageWrites(){var r=new TransactionTemplate(manager);r.setReadOnly(true);assertThatThrownBy(()->r.executeWithoutResult(s->collector.append(123,operation("x")))).isInstanceOf(IllegalStateException.class);}
    @Test void pendingHeadBlocksFollowingEventUntilMatchingAck(){long id=append();tx.executeWithoutResult(s->collector.append(id,operation("signal:2")));var first=store.claimForUser(SOURCE,id,60,10).orElseThrow();assertThat(store.claimForUser(SOURCE,id,60,10)).isEmpty();assertThat(store.acknowledge(first,ack(first))).isTrue();var second=store.claimForUser(SOURCE,id,60,10).orElseThrow();assertThat(second.event().sequence()).isEqualTo(2);assertThat(store.acknowledge(second,ack(second))).isTrue();}
    @Test void expiredClaimCannotAcknowledgeAnotherWorkersClaim(){long id=append();var first=store.claimForUser(SOURCE,id,60,10).orElseThrow();jdbc.update("UPDATE language_learning_growth_outbox SET lease_until=? WHERE event_id=?",Timestamp.from(Instant.EPOCH),first.event().eventId());var second=store.claimForUser(SOURCE,id,60,10).orElseThrow();assertThat(first.token()).isNotEqualTo(second.token());assertThat(store.acknowledge(first,ack(first))).isFalse();assertThat(store.fail(first,"REMOTE_UNAVAILABLE",false,10)).isFalse();assertThat(store.acknowledge(second,ack(second))).isTrue();}
    @Test void mismatchedAckDoesNotAdvanceWatermark(){long id=append();var c=store.claimForUser(SOURCE,id,60,10).orElseThrow();var e=c.event();assertThatThrownBy(()->store.acknowledge(c,new GrowthAcknowledgement(SOURCE,e.eventId(),id,2,e.payloadSha256(),"APPLIED"))).isInstanceOf(IllegalArgumentException.class);assertThat(store.deliveredSequence(SOURCE,id)).isZero();}
    @Test void exhaustedHeadIsNotSkippedAndDoesNotBlockOtherUser(){long id=append();tx.executeWithoutResult(s->collector.append(id,operation("signal:2")));var c=store.claimForUser(SOURCE,id,60,1).orElseThrow();store.fail(c,"REMOTE_UNAVAILABLE",false,1);assertThat(store.claimForUser(SOURCE,id,60,1)).isEmpty();long other=append();assertThat(store.claimForUser(SOURCE,other,60,1)).isPresent();assertThat(store.deliveredSequence(SOURCE,id)).isZero();}
    @Test void sourceUuidCannotBeChangedAfterFirstCommit(){append();assertThatThrownBy(()->store.verifyOrigin("b6f527ce-b78e-469c-90b8-4a2781ef97b7")).isInstanceOf(IllegalStateException.class);}
    @TestConfiguration static class JsonConfiguration{@Bean ObjectMapper mapper(){return new ObjectMapper().findAndRegisterModules();}}
}
