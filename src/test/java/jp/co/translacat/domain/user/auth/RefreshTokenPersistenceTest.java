package jp.co.translacat.domain.user.auth;

import jakarta.persistence.EntityManagerFactory;
import jp.co.translacat.domain.user.entity.RefreshToken;
import jp.co.translacat.global.security.JWTService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenPersistenceTest {
    @Test
    void signedRefreshTokenLongerThanDefaultColumnSurvivesPersistence() {
        // 준비: 실제 JWT 발급기로 정상적인 사용자 claim과 서명을 포함한 토큰을 생성한다.
        String secret = Base64.getEncoder().encodeToString(
                "isolated-refresh-token-persistence-test-secret-20261012".getBytes(StandardCharsets.UTF_8));
        var jwt = new JWTService(secret, 86400000, 604800000);
        String signedToken = jwt.generateRefreshToken(1L, "voice-e2e-20261012@example.test");
        assertThat(signedToken.length()).isGreaterThan(255).isLessThanOrEqualTo(8192);

        try (var context = new AnnotationConfigApplicationContext(Database.class);
             var entityManager = context.getBean(EntityManagerFactory.class).createEntityManager()) {
            RefreshToken row = new RefreshToken();
            row.setUserId(1L);
            row.setToken(signedToken);
            row.setExpireDate(LocalDateTime.now().plusDays(7));

            // 실행: 매핑으로 생성한 실제 DB 열에 저장하고 영속성 캐시를 비운다.
            entityManager.getTransaction().begin();
            entityManager.persist(row);
            entityManager.getTransaction().commit();
            entityManager.clear();

            // 검증: 잘림 없이 재조회되며 갱신 용도의 서명 검증도 그대로 통과한다.
            RefreshToken stored = entityManager.find(RefreshToken.class, 1L);
            assertThat(stored.getToken()).isEqualTo(signedToken);
            assertThat(jwt.getRefreshId(stored.getToken())).isEqualTo(1L);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class Database {
        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource(
                    "jdbc:h2:mem:refresh_token_length;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setManagedTypes(PersistenceManagedTypes.of(RefreshToken.class.getName()));
            factory.setJpaPropertyMap(Map.of(
                    "hibernate.hbm2ddl.auto", "create-drop",
                    "hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
            return factory;
        }
    }
}
