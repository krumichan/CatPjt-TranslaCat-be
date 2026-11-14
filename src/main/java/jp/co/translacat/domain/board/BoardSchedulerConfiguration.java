package jp.co.translacat.domain.board;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
class BoardSchedulerConfiguration {

    // AI 응답을 기다리는 동안 다른 도메인의 예약 작업이 지연되지 않도록 번역용 스레드를 둔다.
    @Bean(defaultCandidate = false)
    ThreadPoolTaskScheduler boardTranslationScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("board-translation-");
        return scheduler;
    }
}
