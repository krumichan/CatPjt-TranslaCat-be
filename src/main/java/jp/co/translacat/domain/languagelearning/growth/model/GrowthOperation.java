package jp.co.translacat.domain.languagelearning.growth.model;

import java.util.Map;

/**
 * 전송할 최소 업무 사실이다. 프롬프트·답변 원문·인증정보를 넣지 않는다.
 */
public record GrowthOperation(String key, String kind, Map<String, Object> payload) {
    public GrowthOperation {
        if (key == null || !key.matches("[A-Za-z0-9_.:-]{1,160}") || kind == null || payload == null)
            throw new IllegalArgumentException("성장 명령의 식별자와 본문이 필요합니다.");
    }

    @Override
    public String toString() {
        return "GrowthOperation(kind=" + kind + ", payload=<redacted>)";
    }
}
