package jp.co.translacat.infrastructure.chat.core.directory;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jp.co.translacat.domain.user.block.service.UserBlockService;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.domain.user.friend.request.entity.FriendRequest;
import jp.co.translacat.domain.user.friend.request.repository.FriendRequestRepository;
import jp.co.translacat.domain.user.friend.service.FriendService;
import jp.co.translacat.domain.user.profile.entity.UserProfile;
import jp.co.translacat.domain.user.profile.repository.UserProfileRepository;
import jp.co.translacat.domain.user.profile.storage.model.ImageStorageUpload;
import jp.co.translacat.domain.user.profile.storage.port.ImageStoragePort;
import jp.co.translacat.domain.user.profile.storage.service.ProfileImageValidator;
import jp.co.translacat.domain.user.profile.storage.service.UserProfileImageUrlResolver;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.infrastructure.chat.core.ChatCoreIdentitySecurity;
import jp.co.translacat.infrastructure.chat.core.storage.ChatCoreStorageController;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class ChatCoreDirectoryStorageHttpTest {
    public static final long ID = 9007199254740993L;
    private static final byte[] KEY = new byte[64];
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private static ServletWebServerApplicationContext context;
    private static URI origin;
    private UserRepository users;
    private UserProfileRepository profiles;
    private MemoryStorage storage;
    private static final String OBJECT = "open-chat-profiles/73/00000000-0000-0000-0000-000000000001.png";
    private static final byte[] PNG = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};

    @BeforeAll
    static void start() {
        new SecureRandom().nextBytes(KEY);
        context = createServer(KEY);
        origin = URI.create("http://127.0.0.1:" + context.getWebServer().getPort());
    }

    @AfterAll
    static void stop() {
        if (context != null) context.close();
    }

    public static ServletWebServerApplicationContext createServer(byte[] key) {
        // default properties보다 host 환경변수 우선순위가 높으므로 합성 설정은 process 내부 인자로 고정한다.
        // OS 명령줄에는 test main만 있고, 이 메모리 인자의 임시 key를 출력하지 않는다.
        var settings = new LinkedHashMap<String, Object>();
        settings.put("server.address", "127.0.0.1");
        settings.put("server.port", "0");
        settings.put("spring.main.banner-mode", "off");
        settings.put("logging.level.root", "OFF");
        settings.put("spring.config.location", "optional:classpath:/chat-core-directory-isolated-test.properties");
        settings.put("spring.config.additional-location",
                "optional:classpath:/chat-core-directory-isolated-test.properties");
        settings.put("spring.config.import", "optional:classpath:/chat-core-directory-isolated-test.properties");
        settings.put("spring.profiles.active", "chat-contract-test");
        settings.put("chat.core.identity.enabled", "true");
        settings.put("chat.core.identity.environment", "Development");
        settings.put("chat.core.identity.issuer", "translacat-chat");
        settings.put("chat.core.identity.audience", "translacat-be");
        settings.put("chat.core.identity.service", "translacat-chat");
        settings.put("chat.core.identity.secret-base64", Base64.getEncoder().encodeToString(key));
        String[] arguments = settings.entrySet().stream()
                .map(setting -> "--" + setting.getKey() + "=" + setting.getValue()).toArray(String[]::new);
        return (ServletWebServerApplicationContext) new SpringApplicationBuilder(TestApplication.class).run(arguments);
    }

    @BeforeEach
    void prepare() {
        users = context.getBean(UserRepository.class);
        profiles = context.getBean(UserProfileRepository.class);
        storage = context.getBean(MemoryStorage.class);
        reset(users, profiles, context.getBean(FriendService.class), context.getBean(UserBlockService.class),
                context.getBean(FriendRequestRepository.class));
        storage.values.clear();
        seed(context);
    }

    public static void seed(ServletWebServerApplicationContext server) {
        var users = server.getBean(UserRepository.class);
        User user = User.createLocalUser("synthetic@example.invalid", "never-expose-password", "username", Role.ADMIN,
                "public-large");
        user.setId(ID);
        when(users.findById(ID)).thenReturn(Optional.of(user));
        when(users.findByPublicId("public-large")).thenReturn(Optional.of(user));
        when(users.existsById(ID)).thenReturn(true);
        User target = User.createLocalUser("target@example.invalid", "unused", "target", Role.USER, "public-target");
        target.setId(74L);
        when(users.findById(74L)).thenReturn(Optional.of(target));
        when(users.existsById(74L)).thenReturn(true);
        when(server.getBean(UserProfileRepository.class).findByUserIdInAndDeletedFalse(any())).thenReturn(List.of());
    }

    @Test
    void accountProjectionPreservesLongIdDefaultSummaryAndNullableActualProfile() throws Exception {
        // 실행
        var response = json("/accounts/lookup", "chat:accounts:read", "{\"userIds\":[" + ID + ",99],\"publicIds\":[]}");

        // 검증: 합성 repository만 읽고 profile을 생성·저장하지 않는다.
        assertThat(response.statusCode()).isEqualTo(200);
        var body = JSON.readTree(response.body());
        var account = body.path("accounts").get(0);
        assertThat(account.path("userId").longValue()).isEqualTo(ID);
        assertThat(account.path("profile").isNull()).isTrue();
        assertThat(account.path("summary").path("nickname").textValue()).isEqualTo("username");
        assertThat(body.path("missingUserIds").get(0).longValue()).isEqualTo(99);
        assertThat(response.body()).doesNotContain("password", "authority", "ROLE_ADMIN", "socialId");
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        verify(profiles, never()).save(any());
        verify(users, never()).save(any());
    }

    @Test
    void actualProfileAndPublicIdLookupUseCurrentStorageUrlProjection() throws Exception {
        // 준비
        var profile = UserProfile.createDefault(users.findById(ID).orElseThrow());
        profile.updateText("별명", "상태");
        profile.replaceProfileImageObjectKey("user-profiles/73/legacy.png");
        when(profiles.findByUserIdInAndDeletedFalse(any())).thenReturn(List.of(profile));

        // 실행
        var response = json("/accounts/lookup", "chat:accounts:read",
                "{\"userIds\":[],\"publicIds\":[\"public-large\",\"absent\"]}");

        // 검증
        var body = JSON.readTree(response.body());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(body.at("/accounts/0/profile/nickname").textValue()).isEqualTo("별명");
        assertThat(body.at("/accounts/0/profile/profileImageUrl").textValue()).isEqualTo(
                "https://synthetic.invalid/user-profiles/73/legacy.png");
        assertThat(body.at("/missingPublicIds/0").textValue()).isEqualTo("absent");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"userIds\":[0],\"publicIds\":[]}",
            "{\"userIds\":[\"73\"],\"publicIds\":[]}",
            "{\"userIds\":[9223372036854775808],\"publicIds\":[]}",
            "{\"userIds\":[],\"publicIds\":[]}",
            "{\"userIds\":[73],\"userIds\":[74],\"publicIds\":[]}",
            "{\"userIds\":[73],\"publicIds\":[],\"role\":\"ADMIN\"}"
    })
    void invalidAccountBodyNeverTouchesCore(String body) throws Exception {
        // 준비
        clearInvocations(users, profiles);
        // 실행
        var response = json("/accounts/lookup", "chat:accounts:read", body);
        // 검증
        assertThat(response.statusCode()).isEqualTo(400);
        verifyNoInteractions(users, profiles);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SELF", "BLOCKED", "FRIEND", "REQUEST_SENT", "REQUEST_RECEIVED", "NONE"})
    void relationshipUsesExistingPriorityPolicy(String expected) throws Exception {
        // 준비
        long target = expected.equals("SELF") ? ID : 74;
        when(context.getBean(UserBlockService.class).isBlockedBetween(ID, target)).thenReturn(
                expected.equals("BLOCKED"));
        when(context.getBean(FriendService.class).areFriends(ID, target)).thenReturn(
                expected.equals("FRIEND") || expected.equals("BLOCKED"));
        if (expected.startsWith("REQUEST_")) {
            User source = users.findById(ID).orElseThrow();
            User other = users.findById(74L).orElseThrow();
            when(context.getBean(FriendRequestRepository.class).findBetweenUsersByStatus(any(), any(), any()))
                    .thenReturn(Optional.of(expected.equals("REQUEST_SENT") ? FriendRequest.create(source, other) :
                            FriendRequest.create(other, source)));
        }
        // 실행
        var response = json("/relations/query", "chat:relations:read",
                "{\"requesterUserId\":" + ID + ",\"targetUserIds\":[" + target + ",99]}");
        // 검증
        assertThat(response.statusCode()).isEqualTo(200);
        var body = JSON.readTree(response.body());
        assertThat(body.at("/relations/0/friendStatus").textValue()).isEqualTo(expected);
        assertThat(body.at("/missingUserIds/0").longValue()).isEqualTo(99);
    }

    @Test
    void exactOperationScopeAndServiceSubjectAreRequiredBeforeLookup() throws Exception {
        // 준비
        clearInvocations(users, profiles);
        // 실행
        var response = json("/accounts/lookup", "chat:storage:read", "{\"userIds\":[73],\"publicIds\":[]}");
        // 검증
        assertThat(response.statusCode()).isEqualTo(401);
        verifyNoInteractions(users, profiles);
    }

    @Test
    void rawImageStoreResolveAndDeleteUseOnlyTestMemoryStorage() throws Exception {
        // 실행: 실제 HTTP binary, JSON, 삭제 경로를 차례로 통과한다.
        var stored = request("PUT", "/storage/objects", "chat:storage:write", PNG, "image/png", OBJECT);
        var url = json("/storage/urls", "chat:storage:read",
                JSON.writeValueAsString(Map.of("objectKeys", List.of(OBJECT))));
        var deleted = request("DELETE", "/storage/objects", "chat:storage:delete",
                JSON.writeValueAsBytes(Map.of("objectKey", OBJECT)), "application/json", null);
        // 검증
        assertThat(stored.statusCode()).isEqualTo(204);
        assertThat(url.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(url.body()).at("/objects/0/url").textValue()).endsWith(OBJECT);
        assertThat(deleted.statusCode()).isEqualTo(204);
        assertThat(storage.values).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "user-profiles/73/a.png",
            "open-chat-profiles/../a.png",
            "chat-ai/1/%2e%2e/a.png",
            "chat-ai//a.png",
            "chat-ai/1/./a.png"
    })
    void storageCannotReadOrDeleteAnotherNamespaceOrTraversal(String key) throws Exception {
        // 실행
        var response =
                json("/storage/urls", "chat:storage:read", JSON.writeValueAsString(Map.of("objectKeys", List.of(key))));
        var deleted = request("DELETE", "/storage/objects", "chat:storage:delete",
                JSON.writeValueAsBytes(Map.of("objectKey", key)), "application/json", null);
        // 검증
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(deleted.statusCode()).isEqualTo(400);
        assertThat(storage.values).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"signature", "mime", "size", "key"})
    void invalidUploadNeverWritesStorage(String failure) throws Exception {
        // 준비
        byte[] body = failure.equals("size") ? new byte[10 * 1024 * 1024 + 1] :
                failure.equals("signature") ? new byte[]{1, 2, 3} : PNG;
        // 실행
        var response = request("PUT", "/storage/objects", "chat:storage:write", body,
                failure.equals("mime") ? "image/jpeg" : "image/png",
                failure.equals("key") ? "chat-ai/1/arbitrary.png" : OBJECT);
        // 검증
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(storage.values).isEmpty();
    }

    private HttpResponse<String> json(String path, String scope, String body) throws Exception {
        return request("POST", path, scope, body.getBytes(java.nio.charset.StandardCharsets.UTF_8), "application/json",
                null);
    }

    private HttpResponse<String> request(String method, String path, String scope, byte[] body, String type,
                                         String key) throws Exception {
        long now = Instant.now().getEpochSecond();
        var claims = Map.<String, Object>of("iss", "translacat-chat", "aud", "translacat-be", "sub", "translacat-chat",
                "service", "translacat-chat",
                "tokenUse", "chat-core-service", "environment", "Development", "scopes", List.of(scope), "iat", now,
                "exp", now + 120);
        String token = Jwts.builder().claims(claims).signWith(Keys.hmacShaKeyFor(KEY), Jwts.SIG.HS256).compact();
        var request = HttpRequest.newBuilder(origin.resolve("/internal/v1/chat" + path))
                .header("Content-Type", type)
                .header("Authorization", "Bearer " + token)
                .method(method, HttpRequest.BodyPublishers.ofByteArray(body));
        if (key != null) request.header("X-Chat-Object-Key", key);
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            UserDetailsServiceAutoConfiguration.class
    },
            excludeName = "org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatAutoConfiguration")
    @Import({
            ChatCoreIdentitySecurity.class,
            ChatCoreDirectoryController.class,
            ChatCoreStorageController.class,
            UserProfileImageUrlResolver.class,
            ProfileImageValidator.class
    })
    static class TestApplication {
        @Bean
        UserRepository users() {
            return mock(UserRepository.class);
        }

        @Bean
        UserProfileRepository profiles() {
            return mock(UserProfileRepository.class);
        }

        @Bean
        FriendService friends() {
            return mock(FriendService.class);
        }

        @Bean
        UserBlockService blocks() {
            return mock(UserBlockService.class);
        }

        @Bean
        FriendRequestRepository requests() {
            return mock(FriendRequestRepository.class);
        }

        @Bean
        MemoryStorage storage() {
            return new MemoryStorage();
        }
    }

    public static class MemoryStorage implements ImageStoragePort {
        final Map<String, byte[]> values = new ConcurrentHashMap<>();

        public void store(ImageStorageUpload upload) {
            values.put(upload.objectKey(), upload.bytes());
        }

        public void delete(String key) {
            values.remove(key);
        }

        public String resolvePublicUrl(String key) {
            return "https://synthetic.invalid/" + key;
        }
    }
}
