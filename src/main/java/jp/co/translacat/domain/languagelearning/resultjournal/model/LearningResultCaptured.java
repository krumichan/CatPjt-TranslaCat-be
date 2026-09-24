package jp.co.translacat.domain.languagelearning.resultjournal.model;

/** 평가 성공 경로에서 발행하는 불변 사실이다. Entity, 원격 호출 또는 집계 동작을 보유하지 않는다. */
public record LearningResultCaptured(long userId, String kind, String referenceId, String payloadJson) {
    public LearningResultCaptured {
        if (userId <= 0 || referenceId == null || !referenceId.matches("[1-9][0-9]{0,18}")) {
            throw new IllegalArgumentException("학습 결과의 사용자와 참조 ID가 필요합니다.");
        }
        if (referenceId.length() == 19 && referenceId.compareTo(Long.toString(Long.MAX_VALUE)) > 0) {
            throw new IllegalArgumentException("결과 참조 ID가 Long 범위를 초과했습니다.");
        }
        if (kind == null || !java.util.Set.of("WRITING_SCORED", "SPEAKING_SCORED", "SPEAKING_INSUFFICIENT").contains(kind)) {
            throw new IllegalArgumentException("지원하지 않는 학습 결과 종류입니다. FREE 코칭은 점수 원장에 기록하지 않습니다.");
        }
        if (payloadJson == null || payloadJson.isBlank()) {
            throw new IllegalArgumentException("학습 결과 본문이 필요합니다.");
        }
    }
    // 본문은 사용자 발화·교정 내용을 포함할 수 있으므로 기본 record의 toString을 사용하지 않는다.
    @Override public String toString() {
        return "LearningResultCaptured(kind=" + kind + ", payload=<redacted>)";
    }
}
