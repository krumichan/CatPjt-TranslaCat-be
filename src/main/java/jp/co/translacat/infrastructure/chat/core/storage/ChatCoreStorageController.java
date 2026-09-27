package jp.co.translacat.infrastructure.chat.core.storage;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import jp.co.translacat.domain.user.profile.storage.model.ImageStorageUpload;
import jp.co.translacat.domain.user.profile.storage.model.ProfileImageType;
import jp.co.translacat.domain.user.profile.storage.model.ProfileImageUploadFile;
import jp.co.translacat.domain.user.profile.storage.model.ValidatedImage;
import jp.co.translacat.domain.user.profile.storage.port.ImageStoragePort;
import jp.co.translacat.domain.user.profile.storage.service.ProfileImageValidator;
import jp.co.translacat.global.exception.BusinessException;
import jp.co.translacat.infrastructure.chat.core.ChatCoreServicePrincipal;
import jp.co.translacat.infrastructure.chat.core.directory.ChatCoreJson;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

@RestController
public class ChatCoreStorageController {
    private static final int MAXIMUM_BYTES = 10 * 1024 * 1024;
    private static final Pattern GENERATED_KEY = Pattern.compile(
            "(?:open-chat-profiles/[1-9][0-9]{0,18}/|chat-ai/[1-9][0-9]{0,18}/(?:profile|background)/)[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\\.(?:jpg|png|webp)");
    private final ImageStoragePort storage;
    private final ProfileImageValidator validator;

    public ChatCoreStorageController(ImageStoragePort storage, ProfileImageValidator validator) {
        this.storage = storage;
        this.validator = validator;
    }

    @PostMapping(value = "/internal/v1/chat/storage/urls", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> urls(@AuthenticationPrincipal ChatCoreServicePrincipal caller,
                                  HttpServletRequest request) {
        if (caller == null || !"chat:storage:read".equals(caller.scope())) return ChatCoreJson.rejected(403);
        Set<String> keys = new LinkedHashSet<>();
        try {
            JsonNode body = ChatCoreJson.read(request, 64 * 1024);
            if (body.size() != 1 || !body.path("objectKeys").isArray() || body.path("objectKeys").size() < 1
                    || body.path("objectKeys").size() > 100) throw new IOException();
            for (JsonNode value : body.path("objectKeys")) {
                if (!value.isTextual() || !safeKey(value.textValue())) throw new IOException();
                keys.add(value.textValue());
            }
        } catch (IOException ignored) {
            return ChatCoreJson.rejected(400);
        }

        // 일반 사용자 image namespace는 이 서비스 권한에 포함하지 않는다. URL 정책은 공통 storage가 소유한다.
        List<ObjectUrl> urls = keys.stream().map(key -> new ObjectUrl(key, storage.resolvePublicUrl(key))).toList();
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(Map.of("objects", urls));
    }

    @PutMapping("/internal/v1/chat/storage/objects")
    public ResponseEntity<?> store(@AuthenticationPrincipal ChatCoreServicePrincipal caller,
                                   HttpServletRequest request) {
        if (caller == null || !"chat:storage:write".equals(caller.scope())) return ChatCoreJson.rejected(403);
        List<String> keys = Collections.list(request.getHeaders("X-Chat-Object-Key"));
        if (keys.size() != 1 || !GENERATED_KEY.matcher(keys.getFirst()).matches()) return ChatCoreJson.rejected(400);

        // CHAT이 생성한 새 key와 제한된 이미지 byte만 받는다. 외부 URL 다운로드나 overwrite 재시도는 하지 않는다.
        ValidatedImage image;
        try {
            byte[] bytes = request.getInputStream().readNBytes(MAXIMUM_BYTES + 1);
            image = validator.validate(new ProfileImageUploadFile(null, request.getContentType(), bytes),
                    ProfileImageType.BACKGROUND);
            if (!keys.getFirst().endsWith("." + image.extension())) return ChatCoreJson.rejected(400);
        } catch (IOException | BusinessException ignored) {
            return ChatCoreJson.rejected(400);
        }

        // 유효한 입력의 storage 실패를 입력 오류로 바꾸지 않는다. 호출자는 결과 불명 상태를 보존한다.
        storage.store(new ImageStorageUpload(keys.getFirst(), image.contentType(), image.bytes()));
        return ResponseEntity.noContent().header("Cache-Control", "no-store").build();
    }

    @DeleteMapping(value = "/internal/v1/chat/storage/objects", consumes = "application/json")
    public ResponseEntity<?> delete(@AuthenticationPrincipal ChatCoreServicePrincipal caller,
                                    HttpServletRequest request) {
        if (caller == null || !"chat:storage:delete".equals(caller.scope())) return ChatCoreJson.rejected(403);
        try {
            JsonNode body = ChatCoreJson.read(request, 2048);
            if (body.size() != 1 || !body.path("objectKey").isTextual() || !safeKey(body.path("objectKey").textValue()))
                throw new IOException();
            // 삭제 시점은 CHAT의 rollback/commit 경계가 결정한다. Core는 지정된 CHAT object만 삭제한다.
            storage.delete(body.path("objectKey").textValue());
            return ResponseEntity.noContent().header("Cache-Control", "no-store").build();
        } catch (IOException ignored) {
            return ChatCoreJson.rejected(400);
        }
    }

    public static boolean safeKey(String key) {
        if (key == null || key.length() > 500 || !(key.startsWith("open-chat-profiles/") || key.startsWith("chat-ai/"))
                || key.indexOf('\\') >= 0 || key.indexOf('%') >= 0 || key.chars().anyMatch(Character::isISOControl))
            return false;
        return Arrays.stream(key.split("/", -1))
                .noneMatch(part -> part.isBlank() || part.equals(".") || part.equals(".."));
    }

    public record ObjectUrl(String objectKey, String url) {
    }
}
