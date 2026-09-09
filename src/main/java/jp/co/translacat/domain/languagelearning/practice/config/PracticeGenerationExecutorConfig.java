package jp.co.translacat.domain.languagelearning.practice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class PracticeGenerationExecutorConfig {
    @Bean(name = "practiceGenerationExecutor")
    public ThreadPoolTaskExecutor practiceGenerationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("practice-generation-");
        // Default rejection is caught by the dispatcher; the persisted job remains pending.
        executor.initialize();
        return executor;
    }
}
