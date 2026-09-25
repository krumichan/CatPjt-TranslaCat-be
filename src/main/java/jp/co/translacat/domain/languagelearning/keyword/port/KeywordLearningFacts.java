package jp.co.translacat.domain.languagelearning.keyword.port;

/**
 * 아직 Core에 남아 있는 Writing/Speaking 시작 사실만 제공한다. 적용일 정책은 Ktor가 계산한다.
 */
public interface KeywordLearningFacts {
    boolean hasStartedLearning(Long userId);
}
