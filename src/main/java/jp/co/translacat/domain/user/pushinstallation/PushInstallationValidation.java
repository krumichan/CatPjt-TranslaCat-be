package jp.co.translacat.domain.user.pushinstallation;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;

final class PushInstallationValidation {
    static final long MAX_SAFE_INTEGER = 9_007_199_254_740_991L;
    private static final ObjectMapper JSON = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private PushInstallationValidation() {}

    static boolean isUuid(String value) {
        return value != null && value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    static String uuid(String value) {
        if (!isUuid(value)) throw PushInstallationException.invalid();
        return value;
    }

    static String proof(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) throw PushInstallationException.invalid();
        return value;
    }

    static long generation(long value) {
        if (value < 1 || value > MAX_SAFE_INTEGER) throw PushInstallationException.invalid();
        return value;
    }

    static void put(PushInstallationDtos.Put request) {
        if (request == null || !"expo".equals(request.provider())
                || request.expoPushToken() == null
                || !request.expoPushToken().matches("(?:ExpoPushToken|ExponentPushToken)\\[[A-Za-z0-9_-]{1,256}\\]")
                || !Set.of("android", "ios").contains(request.platform() == null ? "" : request.platform())
                || !Set.of("development", "preview", "production").contains(request.appEnvironment() == null ? "" : request.appEnvironment())) {
            throw PushInstallationException.invalid();
        }
        uuid(request.projectId());
        proof(request.installationProof());
        generation(request.generation());
    }

    static JsonNode body(HttpServletRequest request, Set<String> fields) {
        // parser 예외에는 원문 일부가 들어갈 수 있으므로 cause/message를 밖으로 전달하지 않는다.
        try {
            if (request.getContentType() == null || !request.getContentType().split(";", 2)[0].trim().equalsIgnoreCase("application/json")) {
                throw PushInstallationException.invalid();
            }
            byte[] bytes = request.getInputStream().readNBytes(16 * 1024 + 1);
            if (bytes.length > 16 * 1024) throw PushInstallationException.invalid();
            JsonNode node = JSON.readTree(bytes);
            if (node == null || !node.isObject() || node.size() != fields.size()) throw PushInstallationException.invalid();
            var names = node.fieldNames();
            while (names.hasNext()) if (!fields.contains(names.next())) throw PushInstallationException.invalid();
            return node;
        } catch (IOException ignored) {
            throw PushInstallationException.invalid();
        }
    }

    static String text(JsonNode node, String field) {
        if (!node.path(field).isTextual()) throw PushInstallationException.invalid();
        return node.path(field).textValue();
    }

    static long number(JsonNode node, String field) {
        if (!node.path(field).isIntegralNumber() || !node.path(field).canConvertToLong()) throw PushInstallationException.invalid();
        return generation(node.path(field).longValue());
    }

    static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 required");
        }
    }

    static boolean sameHash(String left, String right) {
        return left != null && right != null && MessageDigest.isEqual(left.getBytes(StandardCharsets.US_ASCII), right.getBytes(StandardCharsets.US_ASCII));
    }
}
