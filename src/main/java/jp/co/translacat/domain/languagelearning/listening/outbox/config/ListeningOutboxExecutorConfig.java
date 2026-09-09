package jp.co.translacat.domain.languagelearning.listening.outbox.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ListeningOutboxExecutorConfig {

    @Bean
    public ThreadPoolTaskExecutor listeningGenerationExecutor() {
        return executor("listening-generation-", 2);
    }

    @Bean
    public ThreadPoolTaskExecutor listeningAudioExecutor() {
        return executor("listening-audio-", 3);
    }

    @Bean
    public ThreadPoolTaskExecutor listeningEvaluationExecutor() {
        return executor("listening-evaluation-", 2);
    }

    private ThreadPoolTaskExecutor executor(String prefix, int workers) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix(prefix);
        executor.setCorePoolSize(workers);
        executor.setMaxPoolSize(workers);
        // Only running workers own leases; durable events are the waiting queue.
        executor.setQueueCapacity(0);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
