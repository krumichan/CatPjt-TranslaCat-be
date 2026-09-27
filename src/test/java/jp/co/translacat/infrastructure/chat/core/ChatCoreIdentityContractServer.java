package jp.co.translacat.infrastructure.chat.core;

import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.domain.user.repository.UserRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Optional;

import static org.mockito.Mockito.when;

/**
 * CHAT의 실제 HTTP adapter와 조합하는 로컬 테스트 fixture이며 production scan 대상이 아니다.
 */
public final class ChatCoreIdentityContractServer {
    public static void main(String[] args) throws Exception {
        // 명시적인 격리 실행과 자식 process 환경에서 받은 임시 키만 허용한다. 비밀은 출력/파일 저장하지 않는다.
        if (!"true".equals(System.getenv("CHAT_IDENTITY_CONTRACT_TEST"))) {
            throw new IllegalStateException("Explicit local identity contract test is required.");
        }
        byte[] key = Base64.getDecoder().decode(System.getenv("CHAT_IDENTITY_CONTRACT_KEY"));
        if (key.length != 64) throw new IllegalArgumentException("Invalid synthetic test key.");
        Path manifest = Path.of(System.getenv("CHAT_IDENTITY_CONTRACT_MANIFEST"));
        var context = ChatCoreIdentityHttpTest.createServer(key);
        var users = context.getBean(UserRepository.class);
        User user =
                User.createLocalUser("synthetic@example.invalid", "unused-synthetic-password", "synthetic", Role.ADMIN,
                        "synthetic-public-id");
        user.setId(73L);
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        // manifest에는 loopback origin만 남긴다. 종료 시점은 실행 script가 소유하는 이 process에 한정한다.
        Files.writeString(manifest, "{\"origin\":\"http://127.0.0.1:" + context.getWebServer().getPort() + "\"}");
    }
}
