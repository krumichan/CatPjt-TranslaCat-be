package jp.co.translacat.domain.cameratranslation;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** 공통 OCR v1의 모바일 소비자 조건을 검증한다. */
final class CameraOcrResult {
    private static final Set<String> STATUSES = Set.of("OK", "NO_TEXT", "UNCERTAIN");
    private static final Set<String> WRITING_MODES =
            Set.of("horizontal-ltr", "horizontal-rtl", "vertical-rtl", "vertical-ltr", "unknown");

    private CameraOcrResult() {
    }

    /** 입력 귀속과 픽셀 경계를 확인하고 잘못된 공통 결과는 공개하지 않는다. */
    static void verify(UUID imageId, JsonNode result) {
        if (result == null || !result.isObject() || !result.path("schemaVersion").isIntegralNumber()
                || result.path("schemaVersion").asInt() != 1 || !imageId.toString().equals(result.path("imageId").asText())
                || !"image-pixels-top-left".equals(result.path("coordinateSpace").asText())
                || !STATUSES.contains(result.path("status").asText())) {
            throw CameraTranslationProblem.unavailable();
        }

        // 공통 좌표계에서 사용할 수 있는 크기와 가역 행렬을 확인한다.
        JsonNode image = result.path("image");
        int width = image.path("widthPx").asInt();
        int height = image.path("heightPx").asInt();
        var matrix = image.path("inputToImage");
        if (!image.path("widthPx").isIntegralNumber() || !image.path("heightPx").isIntegralNumber()
                || width < 1 || height < 1 || width > 3_072 || height > 3_072
                || !matrix.isArray() || matrix.size() != 6) {
            throw CameraTranslationProblem.unavailable();
        }
        for (JsonNode entry : matrix) {
            if (!entry.isNumber() || !Double.isFinite(entry.asDouble())) {
                throw CameraTranslationProblem.unavailable();
            }
        }
        double determinant = matrix.get(0).asDouble() * matrix.get(4).asDouble()
                - matrix.get(1).asDouble() * matrix.get(3).asDouble();
        if (!Double.isFinite(determinant) || Math.abs(determinant) < 1e-12) {
            throw CameraTranslationProblem.unavailable();
        }

        // 정답이 비어 있는 상태를 오류와 구분하고 각 결과 안의 ID와 읽기 순서를 확인한다.
        var regions = result.path("regions");
        String outcome = result.path("status").asText();
        if (!regions.isArray() || regions.size() > 1_000 || ("OK".equals(outcome) && regions.isEmpty())
                || ("NO_TEXT".equals(outcome) && !regions.isEmpty())) {
            throw CameraTranslationProblem.unavailable();
        }
        var ids = new HashSet<String>();
        var order = new HashSet<Integer>();
        for (JsonNode region : regions) {
            var index = region.path("readingOrder");
            var confidence = region.path("confidence");
            if (!hasText(region.path("segmentId")) || !ids.add(region.path("segmentId").asText())
                    || !index.isIntegralNumber() || index.asInt() < 0 || index.asInt() >= regions.size()
                    || !order.add(index.asInt()) || !hasText(region.path("sourceText"))
                    || !WRITING_MODES.contains(region.path("writingMode").asText())
                    || !(confidence.isNull() || inRange(confidence, 1))) {
                throw CameraTranslationProblem.unavailable();
            }
            verifyPolygon(region.path("polygon"), width, height);
        }
        var engine = result.path("engine");
        if (!hasText(engine.path("provider")) || !hasText(engine.path("model")) || !hasText(engine.path("version"))) {
            throw CameraTranslationProblem.unavailable();
        }
    }

    // 픽셀 모서리 기준의 네 좌표와 시작점 및 화면 시계 방향을 확인한다.
    private static void verifyPolygon(JsonNode polygon, int width, int height) {
        if (!polygon.isArray() || polygon.size() != 4) {
            throw CameraTranslationProblem.unavailable();
        }
        for (JsonNode point : polygon) {
            if (!point.isArray() || point.size() != 2 || !inRange(point.get(0), width)
                    || !inRange(point.get(1), height)) {
                throw CameraTranslationProblem.unavailable();
            }
        }

        // 회전된 영역도 y가 가장 작고 x가 가장 작은 점부터 네 변이 볼록해야 한다.
        var first = polygon.get(0);
        for (int i = 0; i < 4; i++) {
            var a = polygon.get(i);
            var b = polygon.get((i + 1) % 4);
            var c = polygon.get((i + 2) % 4);
            double x = a.get(0).asDouble();
            double y = a.get(1).asDouble();
            double cross = (b.get(0).asDouble() - x) * (c.get(1).asDouble() - y)
                    - (b.get(1).asDouble() - y) * (c.get(0).asDouble() - x);
            if (cross <= 0 || y < first.get(1).asDouble()
                    || (y == first.get(1).asDouble() && x < first.get(0).asDouble())) {
                throw CameraTranslationProblem.unavailable();
            }
        }
    }

    // 경계의 0과 최대값을 허용하고 유한한 숫자만 받는다.
    private static boolean inRange(JsonNode value, int maximum) {
        return value.isNumber() && Double.isFinite(value.asDouble())
                && value.asDouble() >= 0 && value.asDouble() <= maximum;
    }

    // 공통 계약에서 사람이 읽을 수 있는 필수 문자열을 식별한다.
    private static boolean hasText(JsonNode value) {
        return value.isTextual() && !value.asText().isBlank();
    }
}
