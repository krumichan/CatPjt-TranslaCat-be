package jp.co.translacat.infrastructure.chat.core.directory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * 실제 CHAT adapter와 조합할 합성 계정/메모리 storage 서버. production에는 포함되지 않는다.
 */
public final class ChatCoreDirectoryContractServer {
    public static void main(String[] args) throws Exception {
        // 명시적인 테스트 실행과 자식 process에 주입한 임시 key만 받는다.
        if (!"true".equals(System.getenv("CHAT_CORE_CONTRACT_TEST"))) {
            throw new IllegalStateException("Explicit local Core contract test is required.");
        }
        byte[] key = Base64.getDecoder().decode(System.getenv("CHAT_CORE_CONTRACT_KEY"));
        if (key.length != 64) throw new IllegalArgumentException("Invalid synthetic test key.");
        var context = ChatCoreDirectoryStorageHttpTest.createServer(key);
        ChatCoreDirectoryStorageHttpTest.seed(context);

        // 파일에는 실제 비밀/계정 자료 없이 이번 loopback origin만 기록한다.
        Files.writeString(Path.of(System.getenv("CHAT_CORE_CONTRACT_MANIFEST")),
                "{\"origin\":\"http://127.0.0.1:" + context.getWebServer().getPort() + "\"}");
    }
}
