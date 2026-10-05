package jp.co.translacat.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import jp.co.translacat.domain.voice.config.VoicePolicyProperties;
import jp.co.translacat.domain.voice.enums.VoiceChannel;
import jp.co.translacat.domain.voice.enums.VoiceMode;
import jp.co.translacat.domain.voice.enums.VoiceSourceLanguageMode;
import jp.co.translacat.domain.voice.model.VoiceStreamContext;
import jp.co.translacat.domain.voice.model.VoiceTranslationRetryContext;
import jp.co.translacat.domain.voice.websocket.service.VoiceAiStreamClient;
import jp.co.translacat.infrastructure.chat.gateway.ChatGatewayProperties;
import jp.co.translacat.infrastructure.chat.gateway.ChatGatewayTarget;
import jp.co.translacat.infrastructure.client.ai.server.voice.VoiceAiTranslationRetryClient;
import jp.co.translacat.infrastructure.languagelearning.client.config.LanguageLearningClientConfiguration;
import jp.co.translacat.infrastructure.languagelearning.client.config.LanguageLearningClientProperties;
import jp.co.translacat.infrastructure.novel.client.NovelGatewayException;
import jp.co.translacat.infrastructure.novel.client.NovelGatewayProperties;
import jp.co.translacat.infrastructure.novel.client.NovelServiceClient;
import jp.co.translacat.novel.domain.EpisodeKey;
import jp.co.translacat.novel.domain.NovelProblem;
import jp.co.translacat.novel.domain.SentenceSegmenter;
import jp.co.translacat.novel.infrastructure.ai.OpenAiExecutionAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServer;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLException;
import java.net.URLClassLoader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionClientTlsTest {
    private static final String FIXTURE_PASSWORD = "local-fixture-only";
    private static final String FIXTURE_API_KEY = "synthetic-api-key";
    private static final String FIXTURE_NOVEL_AI_KEY = "synthetic-dedicated-novel-ai-key";
    private static final byte[] FIXTURE_NOVEL_KEY = "synthetic-novel-tls-signing-key-only".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    @TempDir
    Path directory;

    @Test
    void productionClientsEnforceCaHostnameAndAiAuthentication() throws Exception {
        // 준비: 일회성 CA/서버 인증서를 별도 디렉터리에 만들며 운영 CA/개인키와 process ENV는 읽지 않는다.
        createCertificates();
        var store = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(directory.resolve("server.p12"))) {
            store.load(input, FIXTURE_PASSWORD.toCharArray());
        }
        var keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keys.init(store, FIXTURE_PASSWORD.toCharArray());
        var ssl = SslContextBuilder.forServer(keys).sslProvider(SslProvider.JDK).build();
        var authenticated = new AtomicInteger();
        var rejected = new AtomicInteger();
        var voiceConnections = new AtomicInteger();
        var novelRequests = new AtomicInteger();
        var embeddedAiRequests = new AtomicInteger();
        var embeddedAiRejected = new AtomicInteger();
        var server = HttpServer.create().host("127.0.0.1").port(0).secure(spec -> spec.sslContext(ssl))
                .route(routes -> routes
                        .get("/health", (request, response) -> response.sendString(Mono.just("ok")))
                        .get("/api/v1/chat/rooms", (request, response) -> response.sendString(Mono.just("ok")))
                        .ws("/ws/chat", (inbound, outbound) -> outbound.send(inbound.receive().retain()))
                        .post("/internal/v1/model/execute", (request, response) -> {
                            // Embedded Novel도 실제 AI adapter로 전용 키를 전달하며 공용 키를 허용하지 않는다.
                            if (!FIXTURE_NOVEL_AI_KEY.equals(request.requestHeaders().get("X-API-KEY"))) {
                                embeddedAiRejected.incrementAndGet();
                                return response.status(403).send();
                            }
                            embeddedAiRequests.incrementAndGet();
                            return request.receive().then(response.header("Content-Type", "application/json")
                                    .sendString(Mono.just("{\"provider\":\"openai\",\"model\":\"expected-model\",\"providerCalls\":1,"
                                            + "\"inputTokens\":1,\"outputTokens\":1,\"output\":{\"items\":[{\"id\":\"s0\",\"text\":\"고양이\"}]}}"))
                                    .then());
                        })
                        .get("/internal/v1/novel/health", (request, response) -> {
                            novelRequests.incrementAndGet();
                            // 합성 키로 실제 Novel adapter의 위임 JWT를 검증한다. 운영 값은 읽지 않는다.
                            String authorization = request.requestHeaders().get("Authorization");
                            try {
                                var claims = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(FIXTURE_NOVEL_KEY))
                                        .requireIssuer("translacat-be").requireAudience("translacat-novel")
                                        .requireSubject("73").require("tokenUse", "novel-internal")
                                        .build().parseSignedClaims(authorization.substring("Bearer ".length()));
                                assertThat(claims.getHeader().getAlgorithm()).isEqualTo("HS256");
                                assertThat(claims.getPayload().get("service")).isEqualTo("translacat-be");
                                assertThat(claims.getPayload().get("roles")).isEqualTo(List.of("ADMIN"));
                                return response.header("Content-Type", "application/json")
                                        .sendString(Mono.just("{\"status\":\"ok\"}"));
                            } catch (Exception rejectedToken) {
                                return response.status(403).send();
                            }
                        })
                        .post("/internal/v1/voice/translation/retry", (request, response) -> {
                            if (!FIXTURE_API_KEY.equals(request.requestHeaders().get("X-API-KEY"))) {
                                rejected.incrementAndGet();
                                return response.status(403).send();
                            }
                            authenticated.incrementAndGet();
                            return request.receive().then(response.header("Content-Type", "application/json")
                                    .sendString(Mono.just("{\"translatedText\":\"fixture translation\",\"translationSkipped\":false}"))
                                    .then());
                        })
                        .get("/internal/v1/voice/streams", (request, response) -> {
                            if (!FIXTURE_API_KEY.equals(request.requestHeaders().get("X-API-KEY"))) {
                                return response.status(403).send();
                            }
                            voiceConnections.incrementAndGet();
                            return response.sendWebsocket((inbound, outbound) -> outbound.send(inbound.receive().retain()));
                        }))
                .bindNow(Duration.ofSeconds(10));

        try {
            // 실행: 새 JVM마다 trust 설정을 고정해 JDK/Netty 기본 SSLContext 캐시와 다른 검사를 격리한다.
            probe("trusted", "127.0.0.1", server.port(), true);
            probe("untrusted", "127.0.0.1", server.port(), false);
            probe("hostname-mismatch", "localhost", server.port(), true);

            // 검증: TLS 성공 뒤 실제 AI client가 헤더를 전송하며 잘못된 인증은 별도 403으로 거부된다.
            assertThat(authenticated.get()).isEqualTo(1);
            assertThat(rejected.get()).isEqualTo(1);
            assertThat(voiceConnections.get()).isEqualTo(1);
            assertThat(novelRequests.get()).as("Invalid TLS must not reach the Novel route").isEqualTo(1);
            assertThat(embeddedAiRequests.get()).as("Embedded AI requires verified TLS and its dedicated key").isEqualTo(1);
            assertThat(embeddedAiRejected.get()).as("The common AI key is rejected after successful TLS").isEqualTo(1);
        } finally {
            server.disposeNow(Duration.ofSeconds(10));
        }
    }

    private void createCertificates() throws Exception {
        keytool("-genkeypair", "-alias", "ca", "-dname", "CN=TranslaCat isolated test CA", "-keyalg", "RSA",
                "-keysize", "2048", "-validity", "2", "-ext", "bc:c", "-keystore", "ca.p12");
        keytool("-exportcert", "-alias", "ca", "-rfc", "-keystore", "ca.p12", "-file", "ca.pem");
        keytool("-genkeypair", "-alias", "server", "-dname", "CN=TranslaCat isolated TLS fixture", "-keyalg", "RSA",
                "-keysize", "2048", "-validity", "2", "-keystore", "server.p12");
        keytool("-certreq", "-alias", "server", "-keystore", "server.p12", "-file", "server.csr");
        keytool("-gencert", "-alias", "ca", "-keystore", "ca.p12", "-infile", "server.csr", "-outfile", "server.pem",
                "-rfc", "-validity", "2", "-ext", "SAN=ip:127.0.0.1", "-ext", "EKU=serverAuth");
        keytool("-importcert", "-alias", "ca", "-keystore", "server.p12", "-file", "ca.pem", "-noprompt");
        keytool("-importcert", "-alias", "server", "-keystore", "server.p12", "-file", "server.pem", "-noprompt");
        keytool("-importcert", "-alias", "ca", "-keystore", "trusted.p12", "-file", "ca.pem", "-noprompt");
    }

    private void keytool(String... arguments) throws Exception {
        var command = new ArrayList<String>();
        command.add(executable("keytool"));
        command.addAll(List.of("-J-Duser.language=en", "-J-Duser.country=US"));
        command.addAll(Arrays.asList(arguments));
        command.addAll(List.of("-storepass", FIXTURE_PASSWORD));
        run(command, "keytool-" + directory.toFile().list().length, 30);
    }

    private void probe(String mode, String host, int port, boolean trustFixture) throws Exception {
        var command = new ArrayList<String>();
        command.add(executable("java"));
        if (trustFixture) {
            command.add("-Djavax.net.ssl.trustStore=" + directory.resolve("trusted.p12"));
            command.add("-Djavax.net.ssl.trustStorePassword=" + FIXTURE_PASSWORD);
            command.add("-Djavax.net.ssl.trustStoreType=PKCS12");
        }
        command.addAll(List.of("-cp", classpath(), Probe.class.getName(), mode, "https://" + host + ":" + port));
        run(command, mode, 45);
    }

    private void run(List<String> command, String name, int seconds) throws Exception {
        var output = directory.resolve(name + ".log");
        // Windows 명령행 길이 제한을 피하되 classpath/옵션은 그대로 Java argument file로 전달한다.
        if (command.getFirst().equals(executable("java"))) {
            var arguments = directory.resolve(name + ".args");
            var lines = command.subList(1, command.size()).stream()
                    .map(value -> "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"").toList();
            Files.write(arguments, lines);
            command = List.of(command.getFirst(), "@" + arguments);
        }
        var process = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true)
                .redirectOutput(output.toFile()).start();
        boolean finished = process.waitFor(seconds, TimeUnit.SECONDS);
        if (!finished) process.destroyForcibly();
        assertThat(finished).as("Bounded local process %s", name).isTrue();
        assertThat(process.exitValue()).as("Local TLS fixture %s: %s", name,
                new String(Files.readAllBytes(output), java.nio.charset.StandardCharsets.UTF_8)).isZero();
    }

    private static String executable(String name) {
        return Path.of(System.getProperty("java.home"), "bin", name
                + (System.getProperty("os.name").startsWith("Windows") ? ".exe" : "")).toString();
    }

    private static String classpath() throws Exception {
        var paths = new LinkedHashSet<String>();
        paths.addAll(Arrays.asList(System.getProperty("java.class.path").split(java.io.File.pathSeparator)));
        for (var loader = Probe.class.getClassLoader(); loader != null; loader = loader.getParent()) {
            if (loader instanceof URLClassLoader urls) {
                for (var url : urls.getURLs()) paths.add(Path.of(url.toURI()).toString());
            }
        }
        return String.join(java.io.File.pathSeparator, paths);
    }

    public static final class Probe {
        public static void main(String[] arguments) throws Exception {
            String mode = arguments[0];
            String url = arguments[1];
            boolean success = mode.equals("trusted");

            // 준비: 운영 factory를 그대로 호출한다. trust-all이나 hostname 검사 해제 옵션은 없다.
            var llProperties = new LanguageLearningClientProperties();
            llProperties.setUrl(url);
            var ll = new LanguageLearningClientConfiguration().languageLearningRestClient(RestClient.builder(), llProperties);
            var gatewayProperties = new ChatGatewayProperties();
            gatewayProperties.setBaseUrl(url);
            gatewayProperties.setEnvironment("Production");
            var chat = new ChatGatewayTarget(gatewayProperties);
            var config = new WebClientConfig();
            ReflectionTestUtils.setField(config, "connectTimeoutMilli", 3000);
            ReflectionTestUtils.setField(config, "responseTimeoutSeconds", 5L);
            var web = config.webClient(WebClient.builder());
            var policy = new VoicePolicyProperties(3600000, 7200000, 6400, 3000, 100, 30,
                    3000, 10, 3, 5000, 90, 300, 250, 10000, 0.80, 0.85, 3);
            var voice = new VoiceAiStreamClient(new ObjectMapper(), policy, url, FIXTURE_API_KEY);
            var context = new VoiceStreamContext(73L, "fixture", VoiceChannel.SELF, "fixture", VoiceMode.MIC,
                    VoiceSourceLanguageMode.MANUAL, "en", "en", "ko", "{}", 0);
            var novelProperties = new NovelGatewayProperties();
            novelProperties.setBaseUrl(url);
            novelProperties.setSecretBase64(Base64.getEncoder().encodeToString(FIXTURE_NOVEL_KEY));
            novelProperties.setTimeoutSeconds(5);
            var novel = new NovelServiceClient(novelProperties, new ObjectMapper());
            var embeddedAi = new OpenAiExecutionAdapter(new ObjectMapper(), url, FIXTURE_NOVEL_AI_KEY,
                    "expected-model", "expected-speech", "LUNA");
            var segments = new SentenceSegmenter().segment(new EpisodeKey("syosyetu", "n123aa", "1"),
                    "題", List.of("猫。"), null, null).segments();

            // 실행 / 검증: HTTP와 WebSocket 각각이 신뢰 CA 및 SAN 검사를 실제 수행한다.
            check("LL_REST", success, () -> assertThat(ll.get().uri("/health").retrieve().body(String.class)).isEqualTo("ok"));
            check("CHAT_HTTP", success, () -> {
                var response = chat.httpClient().send(HttpRequest.newBuilder(chat.httpUri("/api/v1/chat/rooms", null))
                        .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(200);
            });
            check("CHAT_WEBSOCKET", success, () -> {
                var socket = chat.httpClient().newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5))
                        .buildAsync(chat.webSocketUri(), new WebSocket.Listener() {}).get(8, TimeUnit.SECONDS);
                socket.abort();
            });
            check("AI_WEBCLIENT", success, () -> assertThat(web.get().uri(url + "/health").retrieve()
                    .bodyToMono(String.class).block(Duration.ofSeconds(8))).isEqualTo("ok"));
            check("AI_VOICE_WEBSOCKET", success, () -> voice.connect(context, text -> {}, error -> {}, () -> {})
                    .get(8, TimeUnit.SECONDS).abort());
            // Novel은 공개 오류에서 cause를 숨긴다. 실제 생성한 HTTP client의 TLS 원인과 adapter 결과를 함께 확인한다.
            var novelHttp = (HttpClient) ReflectionTestUtils.getField(novel, "client");
            check("NOVEL_HTTP_FACTORY", success, () -> {
                var response = novelHttp.send(HttpRequest.newBuilder(URI.create(url + "/health"))
                        .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(200);
            });
            if (success) {
                assertThat(novel.get(73L, "/internal/v1/novel/health").path("status").asText()).isEqualTo("ok");
            } else {
                assertThatThrownBy(() -> novel.get(73L, "/internal/v1/novel/health"))
                        .isInstanceOfSatisfying(NovelGatewayException.class, failure -> {
                            assertThat(failure.status()).isEqualTo(502);
                            assertThat(failure.code()).isEqualTo("NOVEL_GATEWAY_UNAVAILABLE");
                            assertThat(failure.retryable()).isTrue();
                        });
            }
            System.out.println("TLS_CLIENT_PASS=NOVEL_ADAPTER");

            // 검증: 최종 JAR의 embedded adapter도 CA/SAN 검증을 유지하고 전송 오류를 안전하게 정규화한다.
            var embeddedHttp = (HttpClient) ReflectionTestUtils.getField(embeddedAi, "client");
            check("NOVEL_EMBEDDED_AI_HTTP", success, () -> {
                var response = embeddedHttp.send(HttpRequest.newBuilder(URI.create(url + "/health"))
                        .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(200);
            });
            if (success) {
                assertThat(embeddedAi.translate("tls-fixture", segments, List.of(), 5000).items())
                        .containsEntry(segments.getFirst().id(), "고양이");
                int status = web.post().uri(url + "/internal/v1/model/execute")
                        .header("X-API-KEY", FIXTURE_API_KEY).bodyValue("{}")
                        .exchangeToMono(response -> Mono.just(response.statusCode().value())).block(Duration.ofSeconds(8));
                assertThat(status).isEqualTo(403);
            } else {
                assertThatThrownBy(() -> embeddedAi.translate("tls-fixture", segments, List.of(), 5000))
                        .isInstanceOfSatisfying(NovelProblem.class, failure -> {
                            assertThat(failure.status()).isEqualTo(503);
                            assertThat(failure.code()).isEqualTo("AI_TRANSPORT_UNAVAILABLE");
                            assertThat(failure.retryable()).isTrue();
                        });
            }
            System.out.println("TLS_CLIENT_PASS=NOVEL_EMBEDDED_AI_ADAPTER");

            if (success) {
                // 검증: 실제 AI 요청 adapter의 X-API-KEY 전달과 인증 실패를 TLS 결과와 구별한다.
                var retry = new VoiceAiTranslationRetryClient(web);
                ReflectionTestUtils.setField(retry, "aiServerUrl", url);
                ReflectionTestUtils.setField(retry, "aiServerApiKey", FIXTURE_API_KEY);
                ReflectionTestUtils.setField(retry, "retryTimeoutMs", 5000L);
                var retryContext = new VoiceTranslationRetryContext("fixture", "en", "ko");
                assertThat(retry.retry("fixture", 1L, retryContext).translatedText()).isEqualTo("fixture translation");
                int status = web.post().uri(url + "/internal/v1/voice/translation/retry")
                        .header("X-API-KEY", "wrong-synthetic-key").bodyValue("{}")
                        .exchangeToMono(response -> Mono.just(response.statusCode().value())).block(Duration.ofSeconds(8));
                assertThat(status).isEqualTo(403);
            }
            System.out.println("TLS_PROBE_PASS=" + mode);
        }

        private static void check(String client, boolean success, Checked action) throws Exception {
            try {
                action.run();
                if (!success) throw new AssertionError(client + " accepted invalid TLS");
            } catch (Exception error) {
                if (success) throw error;
                boolean tlsFailure = false;
                for (Throwable cause = error; cause != null; cause = cause.getCause()) {
                    if (cause instanceof SSLException) tlsFailure = true;
                }
                assertThat(tlsFailure).as("%s must fail at TLS: %s", client, error.getClass().getSimpleName()).isTrue();
            }
            System.out.println("TLS_CLIENT_PASS=" + client);
        }

        private interface Checked {
            void run() throws Exception;
        }
    }

    // 운영 JRE image에서도 앱을 띄우지 않고 동일한 fixture를 실행하는 검증 진입점이다.
    public static final class RuntimeProbeRunner {
        public static void main(String[] arguments) throws Exception {
            var test = new ProductionClientTlsTest();
            test.directory = Files.createTempDirectory("translacat-tls-fixture-");
            test.productionClientsEnforceCaHostnameAndAiAuthentication();
            System.out.println("RUNTIME_CLIENT_TLS=PASS");
        }
    }
}
