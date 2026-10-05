package jp.co.translacat.novel;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.novel.application.NovelExecutionSettings;
import jp.co.translacat.novel.domain.*;
import jp.co.translacat.novel.infrastructure.ai.NovelAiProfile;
import jp.co.translacat.novel.infrastructure.ai.OpenAiExecutionAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class QualityN1ProfileTest {
    private static final String IDENTITY = "gpt-6.1-sol|sol-6.1-low-v1|2026-10-04.1|low|32768|default|progressive-a-v1";
    private static final String FINGERPRINT = "5ef72db531b91ed428a9afefe0b0d7c43844df1369bc75862bc3cb3c37b5c7a7";

    private ApplicationContextRunner runner() {
        // 준비: 실제 Spring 설정 파일을 읽되 작업자 환경의 키나 외부 설정은 가져오지 않는다.
        return new ApplicationContextRunner().withInitializer(context -> {
            context.getEnvironment().getPropertySources().remove("systemEnvironment");
            context.getEnvironment().getPropertySources().remove("systemProperties");
            new ConfigDataApplicationContextInitializer().initialize(context);
        }).withUserConfiguration(ProfileBeans.class)
                .withPropertyValues("NOVEL_AI_SERVER_API_KEY=synthetic-novel-key-for-config-only");
    }

    @Test
    void embeddedConfigurationBindsRealAiIdentityPlannerAndExistingFixedFingerprint() {
        runner().run(context -> {
            assertThat(context).hasNotFailed();
            var ai = context.getBean(OpenAiExecutionAdapter.class);
            var settings = context.getBean(NovelExecutionSettings.class);
            var options = settings.options(ai);

            // 검증: BE 단일 설정에서 기존 N1/A 점진 실행 계약을 선택한다.
            assertThat(ai.executionIdentity()).isEqualTo(IDENTITY);
            assertThat(options.fingerprint()).isEqualTo(FINGERPRINT);
            assertThat(options.lengthPolicy()).isEqualTo(TranslationOptions.LengthPolicy.FIXED);
            assertThat(options.strategy()).isEqualTo(TranslationOptions.Strategy.SEMANTIC);
            assertThat(options.context()).isEqualTo(TranslationOptions.Context.CONTIGUOUS_WIDE_V2);
            assertThat(options.responseShape()).isEqualTo(TranslationOptions.ResponseShape.KEYED_V4);
            assertThat(options.validationPolicy()).isEqualTo(TranslationOptions.ValidationPolicy.MINIMUM_LETTER_DIGIT_V2);
            assertThat(options.annotationPolicy()).isEqualTo(TranslationOptions.AnnotationPolicy.SOURCE_RUBY_V1);
            assertThat(options.segmentationPolicy()).isEqualTo(SentenceSegmenter.Policy.LEGACY_V1);
            assertThat(context.getEnvironment().getProperty("novel.translation.deadline-millis", Long.class)).isEqualTo(300000L);
            assertThat(context.getEnvironment().getProperty("novel.ai.global-concurrency", Integer.class)).isEqualTo(10);

            // 실행/검증: 실제 planner가 길이와 무관한 N1 요청을 해석하고 원문 ID를 그대로 둔다.
            for (int repetitions : List.of(10, 350)) {
                var source = new SentenceSegmenter().segment(new EpisodeKey("syosyetu", "n2604qf", "1"),
                        "設定検証", List.of("朝の川沿いを歩きながら、猫の足音に耳を澄ませた。".repeat(repetitions)), null, null);
                var before = source.segments();
                var plan = new TranslationPlanner().plan(source, options);
                assertThat(plan.requestedN()).isEqualTo(1);
                assertThat(plan.requestedC()).isEqualTo(1);
                assertThat(plan.actualN()).isEqualTo(1);
                assertThat(plan.effectiveC()).isEqualTo(1);
                assertThat(plan.policyFingerprint()).isEqualTo(FINGERPRINT);
                assertThat(plan.chunks().getFirst().targets()).containsExactlyElementsOf(before);
                assertThat(source.segments()).isEqualTo(before);
            }
        });
    }

    @Test
    void embeddedPolicyRequiresAuthenticationWithoutCreatingSeparateDatabase() {
        runner().run(context -> {
            var environment = context.getEnvironment();
            assertThat(environment.getProperty("novel.ai.enabled")).isNull();
            assertThat(environment.getProperty("novel.ai.api-key")).isEqualTo("synthetic-novel-key-for-config-only");
            assertThat(environment.getProperty("novel.internal.secret-base64")).isNull();
            assertThat(environment.getProperty("spring.datasource.url")).isNull();
            assertThat(environment.getProperty("novel.gateway.enabled")).isNull();
            assertThat(environment.getProperty("novel.source.ttl-seconds", Integer.class)).isEqualTo(600);

            // 검증: 모델 호출 없이 구성하며 필수키 누락은 시작 시점에 실패한다.
            assertThat(context).hasSingleBean(OpenAiExecutionAdapter.class);
        });
        runner().withPropertyValues("novel.ai.api-key=").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseMessage("NOVEL_AI_API_KEY_REQUIRED");
        });
    }

    @Test
    void alwaysAvailableReaderPreservesSelectedQualityPolicy() {
        runner().run(context -> {
            assertThat(context).hasNotFailed();
            var ai = context.getBean(OpenAiExecutionAdapter.class);
            var options = context.getBean(NovelExecutionSettings.class).options(ai);
            assertThat(ai.model()).isEqualTo("gpt-6.1-sol");
            assertThat(ai.maxOutputTokens()).isEqualTo(32768);
            assertThat(context.getBean(NovelAiProfile.class).id()).isEqualTo("sol-6.1-low-v1");
            assertThat(options.fingerprint()).isEqualTo(FINGERPRINT);
            assertThat(context.getEnvironment().getProperty("novel.translation.deadline-millis", Long.class)).isEqualTo(300000L);
            assertThat(context.getEnvironment().getProperty("novel.ai.enabled")).isNull();
            assertThat(context.getEnvironment().getProperty("novel.gateway.enabled")).isNull();
        });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({NovelExecutionSettings.class, NovelAiProfile.class, OpenAiExecutionAdapter.class})
    static class ProfileBeans {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
    }
}
