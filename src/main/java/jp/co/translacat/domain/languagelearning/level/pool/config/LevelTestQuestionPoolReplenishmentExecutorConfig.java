package jp.co.translacat.domain.languagelearning.level.pool.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class LevelTestQuestionPoolReplenishmentExecutorConfig {

    @Bean(name = "levelTestPoolReplenishmentExecutor")
    public Executor levelTestPoolReplenishmentExecutor(
            @Value("${language-learning.level-test.question-pool.replenish-parallelism:4}")
            int parallelism
    ) {
        int size = Math.max(1, Math.min(10, parallelism));
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(size);
        executor.setMaxPoolSize(size);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("LL-Level-Pool-Refill-");
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        executor.initialize();
        return executor;
    }
}
