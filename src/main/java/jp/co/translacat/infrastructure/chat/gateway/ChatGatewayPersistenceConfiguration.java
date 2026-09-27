package jp.co.translacat.infrastructure.chat.gateway;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.persistenceunit.ManagedClassNameFilter;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "chat.gateway", name = "enabled", havingValue = "true")
@EnableJpaRepositories(basePackages = "jp.co.translacat", excludeFilters =
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "jp\\.co\\.translacat\\.domain\\.chat\\..*"))
public class ChatGatewayPersistenceConfiguration {
    @Bean
    ManagedClassNameFilter chatGatewayManagedClassNameFilter() {
        // Boot의 PersistenceManagedTypesScanner도 같은 Chat 경계를 제외해야 Hibernate DDL 대상에 남지 않는다.
        // 기존 ddl-auto/DB/schema는 바꾸지 않으며, gateway=false에서는 기본 전체 검색을 그대로 사용한다.
        return name -> !name.startsWith("jp.co.translacat.domain.chat.");
    }
}
