package jp.co.translacat.domain.languagelearning.daily.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class WritingGenerationExecutorConfig {

    @Bean(name = "writingGenerationExecutor")
    public Executor writingGenerationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(40);
        executor.setThreadNamePrefix("LL-Writing-Generation-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        // Never fall back to synchronous AI generation on a request/scheduler thread.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
