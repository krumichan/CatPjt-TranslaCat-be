package jp.co.translacat.infrastructure.client.ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class SpeakingEvaluationExecutorConfig {
    @Bean(name = "speakingEvaluationExecutor")
    public ThreadPoolTaskExecutor speakingEvaluationExecutor(
            @Value("${language-learning.speaking.evaluation-job.workers:4}") int workers) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Math.max(1, workers));
        executor.setMaxPoolSize(Math.max(1, workers));
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("Speaking-Evaluation-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(25);
        return executor;
    }
}
