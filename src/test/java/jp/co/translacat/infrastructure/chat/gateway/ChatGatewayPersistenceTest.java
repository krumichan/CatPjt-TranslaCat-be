package jp.co.translacat.infrastructure.chat.gateway;

import jakarta.persistence.EntityManagerFactory;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.global.config.QueryDslConfig;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.repository.support.Repositories;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;

import javax.sql.DataSource;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ChatGatewayPersistenceTest {
    @ParameterizedTest
    @ValueSource(strings = {"missing", "false", "true"})
    void actualHibernateMetadataAndRepositoryRegistrationRespectGatewayMode(String enabled) throws Exception {
        // 준비: 실제 Hibernate/Boot scanner/repository factory를 사용한다. JDBC metadata와 DDL을 테스트에서만 끈다.
        var dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(
                new IllegalStateException("Synthetic fixture prohibits database connections."));
        var runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(HibernateJpaAutoConfiguration.class,
                        JpaRepositoriesAutoConfiguration.class))
                .withUserConfiguration(RootPackage.class, QueryDslConfig.class,
                        ChatGatewayPersistenceConfiguration.class)
                .withBean(DataSource.class, () -> dataSource)
                .withBean(NamedParameterJdbcTemplate.class, () -> new NamedParameterJdbcTemplate(dataSource))
                .withPropertyValues("spring.jpa.hibernate.ddl-auto=none",
                        "spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect",
                        "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false",
                        "spring.jpa.open-in-view=false");
        if (!enabled.equals("missing")) runner = runner.withPropertyValues("chat.gateway.enabled=" + enabled);

        // 실행 / 검증: EntityManagerFactory의 실제 entity metamodel과 Spring Data repository 등록을 함께 검사한다.
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            var managed = context.getBean(PersistenceManagedTypes.class).getManagedClassNames();
            var entities = context.getBean(EntityManagerFactory.class).getMetamodel().getEntities().stream()
                    .map(entity -> entity.getJavaType().getName()).collect(Collectors.toSet());
            var repositories = new Repositories(context);
            assertThat(entities).contains(User.class.getName());
            assertThat(repositories.hasRepositoryFor(User.class)).isTrue();
            if (enabled.equals("true")) {
                assertThat(managed).noneMatch(name -> name.startsWith("jp.co.translacat.domain.chat."));
                assertThat(entities).noneMatch(name -> name.startsWith("jp.co.translacat.domain.chat."));
                assertThat(repositories.hasRepositoryFor(ChatRoom.class)).isFalse();
                repositories.forEach(domainType -> assertThat(domainType.getName()).doesNotStartWith(
                        "jp.co.translacat.domain.chat."));
            } else {
                assertThat(managed).contains(ChatRoom.class.getName());
                assertThat(entities).contains(ChatRoom.class.getName());
                assertThat(repositories.hasRepositoryFor(ChatRoom.class)).isTrue();
            }
        });
        verify(dataSource, never()).getConnection();
    }

    @Configuration(proxyBeanMethods = false)
    @AutoConfigurationPackage(basePackages = "jp.co.translacat")
    static class RootPackage {
    }
}
