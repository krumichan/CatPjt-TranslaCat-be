package jp.co.translacat.domain.chat.presence.scheduler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@ConditionalOnProperty(
        prefix = "translacat.chat.presence",
        name = "enabled",
        havingValue = "true"
)
@Configuration
public class ChatPresenceTaskSchedulerConfiguration {

    @Bean(name = "chatPresenceTaskScheduler")
    public TaskScheduler chatPresenceTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("Chat-Presence-");
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.initialize();
        return scheduler;
    }
}
