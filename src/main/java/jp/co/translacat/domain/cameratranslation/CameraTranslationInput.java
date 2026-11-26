package jp.co.translacat.domain.cameratranslation;

import jp.co.translacat.domain.cameratranslation.CameraTranslationDtos.Segment;
import jp.co.translacat.domain.cameratranslation.CameraTranslationDtos.TranslationRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** 사용자 입력 제한을 AI 호출 전에 검사한다. */
final class CameraTranslationInput {
    static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024;
    private static final int MAX_IMAGE_EDGE = 3_072;
    private static final int MAX_IMAGE_PIXELS = 12_000_000;
    private static final Pattern SEGMENT_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final Set<String> LANGUAGE_PAIRS = Set.of("ja>ko", "ko>ja", "en>ko");
    private static final Set<String> LANGUAGES = Set.of("ja", "ko", "en");

    private CameraTranslationInput() {
    }

    /** 표현별 길이와 ID 고유성, 언어 방향, 전체 요청 크기를 검증한다. */
    static void validate(TranslationRequest request) {
        if (request == null || request.requestId() == null || request.mode() == null
                || !LANGUAGE_PAIRS.contains(request.sourceLanguage() + ">" + request.targetLanguage())
                || request.segments() == null || request.segments().isEmpty() || request.segments().size() > 12) {
            throw CameraTranslationProblem.invalid();
        }

        // AI 호출량은 배열 크기와 각 표현 및 전체 글자 수로 제한한다.
        var ids = new HashSet<String>();
        int characters = 0;
        for (Segment segment : request.segments()) {
            if (segment == null || segment.id() == null || !SEGMENT_ID.matcher(segment.id()).matches()
                    || !ids.add(segment.id()) || !validText(segment.text())
                    || (segment.context() != null && !validText(segment.context()))) {
                throw CameraTranslationProblem.invalid();
            }
            characters += length(segment.text()) + length(segment.context());
        }
        if (characters > 4_000) {
            throw CameraTranslationProblem.invalid();
        }
    }

    /** 앱이 고른 서버 OCR 원어를 검증한다. */
    static void validateOcrLanguage(String language) {
        if (language == null || !LANGUAGES.contains(language)) {
            throw CameraTranslationProblem.invalid();
        }
    }

    /** 선언한 이미지 타입과 실제 헤더를 함께 확인하고 사유별 오류로 반환한다. */
    static Crop validateCrop(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw CameraTranslationProblem.invalid();
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw imageTooLarge();
        }
        String contentType = file.getContentType();
        if (!"image/jpeg".equals(contentType) && !"image/png".equals(contentType)) {
            throw unsupportedImage();
        }

        // 메타데이터를 메모리에서 읽어 모델에 넘기기 전에 픽셀 한도를 확인한다.
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length > MAX_IMAGE_BYTES) {
                throw imageTooLarge();
            }
            try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) {
                    throw unsupportedImage();
                }
                var reader = readers.next();
                try {
                    reader.setInput(input, true, true);
                    String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                    if (!contentType.equals("image/" + format)) {
                        throw unsupportedImage();
                    }
                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);
                    if (width <= 0 || height <= 0 || width > MAX_IMAGE_EDGE || height > MAX_IMAGE_EDGE
                            || (long) width * height > MAX_IMAGE_PIXELS) {
                        throw imageTooLarge();
                    }
                } finally {
                    reader.dispose();
                }
            }
            return new Crop(contentType, bytes);
        } catch (IOException failure) {
            throw unsupportedImage();
        }
    }

    // 일반 텍스트는 공백만인 입력과 계약을 초과한 입력을 거부한다.
    private static boolean validText(String text) {
        return text != null && !text.isBlank() && length(text) <= 500;
    }

    // Unicode 코드 포인트 단위로 글자 수를 센다.
    private static int length(String text) {
        return text == null ? 0 : text.codePointCount(0, text.length());
    }

    // 치수나 용량 한도를 초과한 경우의 공개 오류.
    private static CameraTranslationProblem imageTooLarge() {
        return new CameraTranslationProblem(HttpStatus.PAYLOAD_TOO_LARGE, "IMAGE_TOO_LARGE");
    }

    // 지원 포맷이 아니거나 읽을 수 없는 경우의 공개 오류.
    private static CameraTranslationProblem unsupportedImage() {
        return new CameraTranslationProblem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_IMAGE");
    }

    record Crop(String contentType, byte[] bytes) {
    }
}
