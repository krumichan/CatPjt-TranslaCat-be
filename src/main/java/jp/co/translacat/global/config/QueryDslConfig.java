package jp.co.translacat.global.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * QueryDSL 설정 클래스.
 * - EntityManager를 기반으로 JPAQueryFactory 빈을 등록한다.
 * - 등록된 JPAQueryFactory는 Repository 계층에서 QueryDSL 쿼리를 작성할 때 사용된다.
 */
@Configuration
public class QueryDslConfig {

    /**
     * JPA의 EntityManager를 주입받는다.
     * &#064;PersistenceContext는  JPA 표준 방식으로 EntityManager를 주입하는 애노테이션이다.
     */
    @PersistenceContext
    private EntityManager entityManager;

    /**
     * JPAQueryFactory 빈을 생성한다.
     * - QueryDSL로 쿼리를 작성하기 위해 필요한 핵심 객체다.
     * - 주입된 EntityManager를 기반으로 생성된다.
     *
     * @return JPAQueryFactory 인스턴스
     */
    @Bean
    public JPAQueryFactory jpaQueryFactory() {
        return new JPAQueryFactory(entityManager);
    }
}
