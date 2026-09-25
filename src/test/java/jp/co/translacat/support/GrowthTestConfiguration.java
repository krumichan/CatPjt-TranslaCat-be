package jp.co.translacat.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.infrastructure.languagelearning.growth.CoreGrowthCollector;
import jp.co.translacat.infrastructure.languagelearning.growth.GrowthOutboxStore;
import jp.co.translacat.infrastructure.languagelearning.growth.GrowthProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.time.Clock;

/**
 * 테스트에서는 HTTP를 켜지 않는다. 실제 JPA와 JDBC outbox의 동일 커밋을 검사한다.
 */
@TestConfiguration
public class GrowthTestConfiguration {
    @Bean
    JdbcTemplate growthJdbc(DataSource source) {
        return new JdbcTemplate(source);
    }

    @Bean
    GrowthProperties growthProperties() {
        var p = new GrowthProperties();
        p.setEnabled(true);
        p.setSourceInstanceId("1ae93ac7-179b-4700-9edb-eb001461f033");
        return p;
    }

    @Bean
    GrowthOutboxStore growthOutboxStore(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        jdbc.execute(
                "CREATE TABLE IF NOT EXISTS language_learning_growth_outbox_stream(source_instance_id VARCHAR(36) NOT NULL,user_id BIGINT NOT NULL,last_sequence BIGINT NOT NULL,PRIMARY KEY(source_instance_id,user_id))");
        jdbc.execute(
                "CREATE TABLE IF NOT EXISTS language_learning_growth_outbox(event_id VARCHAR(36) PRIMARY KEY,schema_version INT NOT NULL,source_instance_id VARCHAR(36) NOT NULL,user_id BIGINT NOT NULL,stream_sequence BIGINT NOT NULL,occurred_at VARCHAR(40) NOT NULL,payload_json CLOB NOT NULL,payload_sha256 VARCHAR(64) NOT NULL,attempts INT NOT NULL,blocked BOOLEAN NOT NULL,next_attempt_at TIMESTAMP(6) NOT NULL,claim_token VARCHAR(36),lease_until TIMESTAMP(6),delivered_at TIMESTAMP(6),last_error_code VARCHAR(64),UNIQUE(source_instance_id,user_id,stream_sequence),FOREIGN KEY(source_instance_id,user_id) REFERENCES language_learning_growth_outbox_stream(source_instance_id,user_id))");
        return new GrowthOutboxStore(jdbc, manager, Clock.systemUTC());
    }

    @Bean
    CoreGrowthCollector coreGrowthCollector(GrowthOutboxStore store, GrowthProperties properties, ObjectMapper mapper,
                                            JdbcTemplate jdbc) {
        return new CoreGrowthCollector(store, properties, mapper, jdbc);
    }
}
