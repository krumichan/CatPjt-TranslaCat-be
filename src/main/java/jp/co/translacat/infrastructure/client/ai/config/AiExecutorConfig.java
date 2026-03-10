package jp.co.translacat.infrastructure.client.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AiExecutorConfig {
    @Bean(name = "aiExecutor")
    public Executor aiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10); // 기본 풀 사이즈
        executor.setMaxPoolSize(20); // 최대 풀 사이즈
        executor.setQueueCapacity(100); // 대기열 길이
        executor.setThreadNamePrefix("AI-Thread-"); // 스레드명 조정
        executor.initialize();
        return executor;
    }
}
