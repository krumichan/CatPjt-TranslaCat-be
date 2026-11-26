package jp.co.translacat.domain.cameratranslation;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** OCR 대기가 다른 업무의 스케줄러를 점유하지 않게 한다. */
@Configuration(proxyBeanMethods = false)
class CameraOcrScheduling {
    /** 기본 작업 실행기와 분리된 단일 OCR 실행기를 등록한다. */
    @Bean(defaultCandidate = false)
    ThreadPoolTaskScheduler cameraOcrScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("camera-ocr-");
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }
}
