package jp.co.translacat.infrastructure.chat.core.directory;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import jp.co.translacat.domain.user.block.service.UserBlockService;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.friend.request.repository.FriendRequestRepository;
import jp.co.translacat.domain.user.friend.service.FriendService;
import jp.co.translacat.domain.user.profile.dto.UserSummaryProfileResponseDto;
import jp.co.translacat.domain.user.profile.entity.UserProfile;
import jp.co.translacat.domain.user.profile.repository.UserProfileRepository;
import jp.co.translacat.domain.user.profile.storage.service.UserProfileImageUrlResolver;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.domain.user.search.service.UserFriendStatusResolver;
import jp.co.translacat.infrastructure.chat.core.ChatCoreServicePrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.*;

@RestController
@Transactional(readOnly = true)
public class ChatCoreDirectoryController {
    private static final int MAXIMUM_TARGETS = 100;
    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final UserProfileImageUrlResolver urls;
    private final FriendService friends;
    private final UserBlockService blocks;
    private final FriendRequestRepository requests;

    public ChatCoreDirectoryController(UserRepository users, UserProfileRepository profiles, UserProfileImageUrlResolver urls,
                                       FriendService friends, UserBlockService blocks, FriendRequestRepository requests) {
        this.users = users;
        this.profiles = profiles;
        this.urls = urls;
        this.friends = friends;
        this.blocks = blocks;
        this.requests = requests;
    }

    @PostMapping(value = "/internal/v1/chat/accounts/lookup", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> accounts(@AuthenticationPrincipal ChatCoreServicePrincipal caller, HttpServletRequest request) {
        // 인증된 CHAT 서비스의 제한된 projection만 제공한다. 비밀번호/role/social ID와 전체 목록 API는 없다.
        if (caller == null || !"chat:accounts:read".equals(caller.scope())) return ChatCoreJson.rejected(403);
        JsonNode body;
        List<Long> ids;
        List<String> publicIds = new ArrayList<>();
        try {
            body = ChatCoreJson.read(request, 32 * 1024);
            if (body.size() != 2 || !body.has("userIds") || !body.path("publicIds").isArray()) throw new IOException();
            ids = ids(body.path("userIds"));
            for (JsonNode value : body.path("publicIds")) {
                if (!value.isTextual() || value.textValue().isBlank() || value.textValue().length() > 255) throw new IOException();
                publicIds.add(value.textValue().trim());
            }
            if (ids.size() + publicIds.size() < 1 || ids.size() + publicIds.size() > MAXIMUM_TARGETS) throw new IOException();
        } catch (IOException ignored) {
            return ChatCoreJson.rejected(400);
        }

        // 사용자·프로필 원본을 읽기만 한다. profile 부재의 기본 summary도 저장하지 않는다.
        Map<Long, User> found = new LinkedHashMap<>();
        List<Long> missingIds = new ArrayList<>();
        List<String> missingPublicIds = new ArrayList<>();
        for (Long id : new LinkedHashSet<>(ids)) {
            users.findById(id).ifPresentOrElse(user -> found.put(user.getId(), user), () -> missingIds.add(id));
        }
        for (String publicId : new LinkedHashSet<>(publicIds)) {
            users.findByPublicId(publicId).ifPresentOrElse(user -> found.put(user.getId(), user), () -> missingPublicIds.add(publicId));
        }
        Map<Long, UserProfile> byUser = new HashMap<>();
        if (!found.isEmpty()) {
            for (UserProfile profile : profiles.findByUserIdInAndDeletedFalse(found.keySet())) {
                byUser.put(profile.getUser().getId(), profile);
            }
        }
        List<Account> accounts = found.values().stream().map(user -> project(user, byUser.get(user.getId()))).toList();
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(new Accounts(accounts, missingIds, missingPublicIds));
    }

    @PostMapping(value = "/internal/v1/chat/relations/query", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> relations(@AuthenticationPrincipal ChatCoreServicePrincipal caller, HttpServletRequest request) {
        // 사용자/방의 접근 권한은 CHAT use case가 확인한다. Core는 기존 관계 정책만 계산한다.
        if (caller == null || !"chat:relations:read".equals(caller.scope())) return ChatCoreJson.rejected(403);
        JsonNode body;
        long requester;
        List<Long> targets;
        try {
            body = ChatCoreJson.read(request, 32 * 1024);
            if (body.size() != 2 || !body.path("requesterUserId").isIntegralNumber()
                    || !body.path("requesterUserId").canConvertToLong()) throw new IOException();
            requester = body.path("requesterUserId").longValue();
            targets = ids(body.path("targetUserIds"));
            if (requester <= 0 || targets.isEmpty()) throw new IOException();
        } catch (IOException ignored) {
            return ChatCoreJson.rejected(400);
        }
        if (!users.existsById(requester)) return ChatCoreJson.rejected(404);
        List<Relation> result = new ArrayList<>();
        List<Long> missing = new ArrayList<>();
        for (Long target : new LinkedHashSet<>(targets)) {
            Optional<User> user = users.findById(target);
            if (user.isEmpty()) {
                missing.add(target);
                continue;
            }
            result.add(new Relation(target, friends.areFriends(requester, target), blocks.isBlockedBetween(requester, target),
                    UserFriendStatusResolver.resolve(requester, user.get(), blocks, friends, requests).name()));
        }
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(new Relations(result, missing));
    }

    private Account project(User user, UserProfile profile) {
        // 실제 profile nullable과 default summary를 분리해 notification/direct/message의 다른 fallback을 보존한다.
        Profile actual = profile == null ? null : new Profile(profile.getNickname(), urls.resolveProfileImageUrl(profile),
                urls.resolveProfileBackgroundImageUrl(profile), profile.getBio());
        UserProfile summary = profile == null ? UserProfile.createDefault(user) : profile;
        return new Account(user.getId(), user.getEmail(), user.getUsername(), user.getPublicId(), actual,
                UserSummaryProfileResponseDto.from(summary, urls.resolveProfileImageUrl(summary), urls.resolveProfileBackgroundImageUrl(summary)));
    }

    private static List<Long> ids(JsonNode values) throws IOException {
        if (!values.isArray() || values.size() > MAXIMUM_TARGETS) throw new IOException();
        List<Long> result = new ArrayList<>();
        for (JsonNode value : values) {
            if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() <= 0) throw new IOException();
            result.add(value.longValue());
        }
        return result;
    }

    public record Profile(String nickname, String profileImageUrl, String profileBackgroundImageUrl, String bio) { }
    public record Account(long userId, String email, String username, String publicId, Profile profile, UserSummaryProfileResponseDto summary) { }
    public record Accounts(List<Account> accounts, List<Long> missingUserIds, List<String> missingPublicIds) { }
    public record Relation(long targetUserId, boolean friends, boolean blocked, String friendStatus) { }
    public record Relations(List<Relation> relations, List<Long> missingUserIds) { }
}
