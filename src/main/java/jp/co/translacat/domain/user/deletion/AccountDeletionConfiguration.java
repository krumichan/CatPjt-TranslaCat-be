package jp.co.translacat.domain.user.deletion;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AccountDeletionProperties.class)
class AccountDeletionConfiguration { }
