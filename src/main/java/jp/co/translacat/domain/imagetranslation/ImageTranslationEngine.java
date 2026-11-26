package jp.co.translacat.domain.imagetranslation;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import jp.co.translacat.infrastructure.client.legacy.ExternalApiClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.codec.CodecException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static jp.co.translacat.domain.imagetranslation.ImageTranslationTypes.EngineResult;
import static jp.co.translacat.domain.imagetranslation.ImageTranslationTypes.ImageInfo;
import static jp.co.translacat.domain.imagetranslation.ImageTranslationTypes.PageState;
import static jp.co.translacat.domain.imagetranslation.ImageTranslationTypes.Segment;
import static jp.co.translacat.domain.imagetranslation.ImageTranslationTypes.Stage;
import static jp.co.translacat.domain.imagetranslation.ImageTranslationTypes.WorkFailure;

/** 検証済みの原本画像一枚を既存の AI サーバーへ送り、保存可能な表示結果に変換する。 */
@Component
@ConditionalOnProperty(prefix = "translacat.image-translation", name = "enabled", havingValue = "true")
final class ImageTranslationEngine {
    private static final String PAGE_ROUTE = "/internal/v1/image-translation/page";
    private static final int OCR_CONTRACT_VERSION = 1;
    private static final int MAX_SEGMENTS = 300;
    private static final int MAX_TEXT_LENGTH = 16_384;
    private static final Set<String> WRITING_MODES = Set.of(
            "horizontal-ltr", "horizontal-rtl", "vertical-rtl", "vertical-ltr", "unknown");

    private final ExternalApiClient client;
    private final String baseUrl;
    private final String apiKey;

    // 共有 HTTP クライアントと既存の内部サービス認証を使う。
    ImageTranslationEngine(ExternalApiClient client, @Value("${ai-server.url:}") String baseUrl,
            @Value("${ai-server.api-key:}") String apiKey) {
        this.client = client;
        this.baseUrl = baseUrl.strip().replaceAll("/+$", "");
        this.apiKey = apiKey;
    }

    // 一枚だけ実行し、方向補正後の原本と対応する応答だけを受け入れる。
    EngineResult process(byte[] bytes, ImageInfo image, String sourceLanguage, String targetLanguage) {
        if (baseUrl.isEmpty() || apiKey.isBlank()) {
            throw new WorkFailure("OCR_UNAVAILABLE", Stage.OCR, false);
        }

        // 原本の EXIF は保持し、ファイル名やユーザーのヘッダーを内部サービスへ渡さない。
        var body = new MultipartBodyBuilder();
        body.part("file", new ByteArrayResource(bytes))
                .filename("page")
                .contentType(MediaType.parseMediaType(image.contentType()));
        body.part("sourceLanguage", sourceLanguage).contentType(MediaType.TEXT_PLAIN);
        body.part("targetLanguage", targetLanguage).contentType(MediaType.TEXT_PLAIN);

        // 再試行はページワーカーだけが所有し、この HTTP 呼び出しは一度だけ送る。
        JsonNode response;
        try {
            response = client.postMultipartOnce(
                    baseUrl + PAGE_ROUTE, body.build(), Map.of("X-API-KEY", apiKey), JsonNode.class);
        } catch (RuntimeException error) {
            throw classify(error);
        }

        return parse(response, image);
    }

    // 元画像と座標契約、終端状態、領域順序がすべて整合することを確認する。
    private static EngineResult parse(JsonNode response, ImageInfo image) {
        if (response == null || !response.isObject()
                || requiredInt(response.path("widthPx")) != image.widthPx()
                || requiredInt(response.path("heightPx")) != image.heightPx()
                || requiredInt(response.path("ocrContractVersion")) != OCR_CONTRACT_VERSION) {
            throw invalidResponse();
        }
        PageState state = switch (requireText(response.path("state"), 16)) {
            case "READY" -> PageState.READY;
            case "NO_TEXT" -> PageState.NO_TEXT;
            case "FAILED" -> PageState.FAILED;
            default -> throw invalidResponse();
        };

        // 巨大な結果と不一致の ID を保存せず、表示側に渡す順序を固定する。
        JsonNode regions = response.path("segments");
        if (!regions.isArray() || regions.size() > MAX_SEGMENTS) {
            throw invalidResponse();
        }
        List<Segment> segments = new ArrayList<>(regions.size());
        Set<String> identifiers = new HashSet<>();
        for (JsonNode region : regions) {
            Segment segment = readSegment(region, image, segments.size());
            if (!identifiers.add(segment.segmentId())) {
                throw invalidResponse();
            }
            segments.add(segment);
        }

        // 部分結果は保持するが、成功状態とエラー状態を混在させない。
        String error = null;
        boolean retryable = false;
        if (state == PageState.FAILED) {
            error = requireText(response.path("errorCode"), 48);
            retryable = requiredBoolean(response.path("retryable"));
            if (!(error.equals("OCR_UNCERTAIN") && !retryable)
                    && !(error.equals("TRANSLATION_TEMPORARY") && retryable)) {
                throw invalidResponse();
            }
        } else if (response.hasNonNull("errorCode")
                || (response.hasNonNull("retryable") && requiredBoolean(response.path("retryable")))
                || (state == PageState.NO_TEXT && !segments.isEmpty())
                || (state == PageState.READY && !allTranslated(segments))) {
            throw invalidResponse();
        }
        return new EngineResult(state, image.widthPx(), image.heightPx(), OCR_CONTRACT_VERSION,
                List.copyOf(segments), error, retryable);
    }

    // 各領域の必須項目と nullable 値を検証し、座標を原本の範囲内に限定する。
    private static Segment readSegment(JsonNode region, ImageInfo image, int order) {
        if (!region.isObject() || requiredInt(region.path("readingOrder")) != order) {
            throw invalidResponse();
        }
        String id = requireText(region.path("segmentId"), 128);
        String source = requireText(region.path("sourceText"), MAX_TEXT_LENGTH);
        boolean needsReview = requiredBoolean(region.path("needsReview"));
        JsonNode translatedNode = region.path("translatedText");
        String translated = translatedNode.isNull() ? null : requireText(translatedNode, MAX_TEXT_LENGTH);
        if (!needsReview && translated == null) {
            throw invalidResponse();
        }

        // OCR の方向と信頼度は共通契約の値だけを受け入れる。
        String mode = requireText(region.path("writingMode"), 20);
        if (!WRITING_MODES.contains(mode)) {
            throw invalidResponse();
        }
        JsonNode confidenceNode = region.path("confidence");
        Double confidence = confidenceNode.isNull() ? null : boundedNumber(confidenceNode, 1.0);
        return new Segment(id, order, source, translated, readPolygon(region.path("polygon"), image),
                confidence, mode, needsReview);
    }

    // 画面の上端から時計回りに並んだ凸四角形を読み取る。
    private static List<List<Double>> readPolygon(JsonNode polygon, ImageInfo image) {
        if (!polygon.isArray() || polygon.size() != 4) {
            throw invalidResponse();
        }
        List<List<Double>> points = new ArrayList<>(4);
        for (JsonNode point : polygon) {
            if (!point.isArray() || point.size() != 2) {
                throw invalidResponse();
            }
            points.add(List.of(
                    boundedNumber(point.get(0), image.widthPx()),
                    boundedNumber(point.get(1), image.heightPx())));
        }

        // 一点目の位置と隣接辺の外積を照合し、重複点や自己交差を拒否する。
        List<Double> first = points.getFirst();
        for (int index = 0; index < points.size(); index++) {
            List<Double> a = points.get(index);
            List<Double> b = points.get((index + 1) % 4);
            List<Double> c = points.get((index + 2) % 4);
            double cross = (b.get(0) - a.get(0)) * (c.get(1) - b.get(1))
                    - (b.get(1) - a.get(1)) * (c.get(0) - b.get(0));
            double aY = a.get(1);
            double firstY = first.get(1);
            if (cross <= 0 || aY < firstY || (aY == firstY && a.get(0) < first.get(0))) {
                throw invalidResponse();
            }
        }
        return List.copyOf(points);
    }

    // 空ページは別状態を用い、READY では全領域の翻訳を要求する。
    private static boolean allTranslated(List<Segment> segments) {
        return !segments.isEmpty()
                && segments.stream().noneMatch(segment -> segment.needsReview() || segment.translatedText() == null);
    }

    // JSON の型を文字列や浮動小数から暗黙に変換しない。
    private static int requiredInt(JsonNode node) {
        if (!node.isIntegralNumber() || !node.canConvertToInt()) {
            throw invalidResponse();
        }
        return node.intValue();
    }

    // 欠損と偽を区別する。
    private static boolean requiredBoolean(JsonNode node) {
        if (!node.isBoolean()) {
            throw invalidResponse();
        }
        return node.booleanValue();
    }

    // 意味のある文字列だけを指定された上限まで保存する。
    private static String requireText(JsonNode node, int maxLength) {
        String value = node.textValue();
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw invalidResponse();
        }
        return value;
    }

    // NaN、無限大、画像外の座標や信頼度は受け付けない。
    private static double boundedNumber(JsonNode node, double maximum) {
        double value = node.doubleValue();
        if (!node.isNumber() || !Double.isFinite(value) || value < 0 || value > maximum) {
            throw invalidResponse();
        }
        return value;
    }

    // 上流の本文や例外メッセージを公開せず、HTTP 状態と失敗種別だけを採用する。
    private static WorkFailure classify(RuntimeException error) {
        Throwable cause = error;
        for (int depth = 0; cause != null && depth < 8; depth++, cause = cause.getCause()) {
            if (cause instanceof WebClientResponseException response) {
                int status = response.getStatusCode().value();
                if (status == 400 || status == 413 || status == 415 || status == 422) {
                    return new WorkFailure("INVALID_IMAGE", Stage.VALIDATE, false);
                }
                if (status >= 400) {
                    boolean retryable = status == 408 || status == 429 || status >= 500;
                    return new WorkFailure("OCR_UNAVAILABLE", Stage.OCR, retryable);
                }
                return invalidResponse();
            }
            if (cause instanceof CodecException || cause instanceof DataBufferLimitException) {
                return invalidResponse();
            }
            if (cause instanceof WebClientRequestException || cause instanceof CallNotPermittedException) {
                return new WorkFailure("OCR_UNAVAILABLE", Stage.OCR, true);
            }
        }
        return new WorkFailure("OCR_UNAVAILABLE", Stage.OCR, false);
    }

    private static WorkFailure invalidResponse() {
        return new WorkFailure("DISPLAY_DATA_INVALID", Stage.RENDER, false);
    }
}
