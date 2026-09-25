package jp.co.translacat.infrastructure.languagelearning.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import io.jsonwebtoken.security.Keys;
import jp.co.translacat.domain.languagelearning.common.enums.KeywordType;
import jp.co.translacat.domain.languagelearning.keyword.dto.request.KeywordCreateRequestDto;
import jp.co.translacat.domain.languagelearning.keyword.dto.request.KeywordUpdateRequestDto;
import jp.co.translacat.infrastructure.languagelearning.client.config.LanguageLearningClientProperties;
import jp.co.translacat.infrastructure.languagelearning.client.security.LanguageLearningInternalJwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LanguageLearningKeywordClientTest {
    private static final String USER = "http://ll.test/internal/v1/language-learning/keywords";
    private static final String ADMIN = "http://ll.test/internal/v1/admin/language-learning/system-keywords";
    private static final Instant NOW = Instant.parse("2026-09-24T01:00:00Z");
    private static final byte[] KEY = new byte[32];
    private static final String ROW = """
            {"id":10,"text":"IT","displayName":"IT","secondaryDisplayName":null,
            "source":"SYSTEM","type":"TOPIC","canonicalKey":"it","parentKeywordId":null,
            "parentCanonicalKey":null,"sortOrder":0,"active":true,"selected":false,"pendingEffectiveDate":null}
            """;
    private MockRestServiceServer server;
    private LanguageLearningKeywordClient client;

    @BeforeEach
    void setup() {
        var properties = new LanguageLearningClientProperties();
        properties.getInternalJwt().setSecretBase64(Encoders.BASE64.encode(KEY));
        var jwt = new LanguageLearningInternalJwtProvider(properties, Clock.fixed(NOW, ZoneOffset.UTC));
        var builder = RestClient.builder().baseUrl("http://ll.test").defaultHeader("Content-Type", "application/json");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new LanguageLearningKeywordClient(builder.build(), jwt, new ObjectMapper().findAndRegisterModules());
    }

    private Claims claims(String authorization) {
        assertNotNull(authorization);
        assertTrue(authorization.startsWith("Bearer "));
        return Jwts.parser().clock(() -> Date.from(NOW)).verifyWith(Keys.hmacShaKeyFor(KEY))
                .requireIssuer("translacat-be").requireAudience("translacat-ll")
                .build().parseSignedClaims(authorization.substring(7)).getPayload();
    }

    @Test
    void getUsesKeywordPurposeLocaleAndStartedFact() {
        server.expect(requestTo(USER))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-TranslaCat-Locale", "learning"))
                .andExpect(request -> {
                    var value = claims(request.getHeaders().getFirst("Authorization"));
                    assertEquals("123", value.getSubject());
                    assertEquals("ll-keywords", value.get("tokenUse"));
                    assertEquals(Boolean.TRUE, value.get("keywordLearningStarted", Boolean.class));
                })
                .andRespond(withSuccess("{\"systemKeywords\":[" + ROW + "],\"customKeywords\":[]}",
                        MediaType.APPLICATION_JSON));
        assertEquals(10L, client.list(123L, true, "learning").systemKeywords().get(0).id().longValue());
        server.verify();
    }

    @Test
    void customPostAndPatchKeepTheExternalRecordFieldNames() {
        server.expect(requestTo(USER + "/custom"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.text").value("IT"))
                .andExpect(jsonPath("$.type").value("TOPIC"))
                .andRespond(withSuccess(ROW, MediaType.APPLICATION_JSON));
        server.expect(requestTo(USER + "/custom/10"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(jsonPath("$.active").value(false))
                .andRespond(withSuccess(ROW, MediaType.APPLICATION_JSON));
        client.createCustom(123L, false, new KeywordCreateRequestDto("IT", KeywordType.TOPIC, null, null, null));
        client.updateCustom(123L, true, 10L, new KeywordUpdateRequestDto(null, null, null, false, null, null));
        server.verify();
    }

    @Test
    void selectionUsesPutAndDeleteChecksAcknowledgement() {
        server.expect(requestTo(USER + "/system/10/selection")).andExpect(method(HttpMethod.PUT))
                .andExpect(jsonPath("$.selected").value(true)).andRespond(withSuccess(ROW, MediaType.APPLICATION_JSON));
        server.expect(requestTo(USER + "/custom/11")).andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess("true", MediaType.APPLICATION_JSON));
        client.selectSystem(123L, false, 10L, true);
        client.deleteCustom(123L, false, 11L);
        server.verify();
    }

    @Test
    void administratorUsesRealSubjectAndReturnsPlainList() {
        server.expect(requestTo(ADMIN)).andExpect(request -> {
            var value = claims(request.getHeaders().getFirst("Authorization"));
            assertEquals("900", value.getSubject());
            assertEquals(List.of("ADMIN"), value.get("roles", List.class));
        }).andRespond(withSuccess("[" + ROW + "]", MediaType.APPLICATION_JSON));
        assertEquals(1, client.listSystem(900L).size());
        server.verify();
    }

    @Test
    void candidateQueryUsesDateAndLeavesMasteryOutOfTheContract() {
        server.expect(requestTo(USER + "/candidates?learningDate=2026-09-24"))
                .andRespond(withSuccess("""
                        {"candidates":[{"key":"CUSTOM:10","text":"IT","source":"CUSTOM","type":"TOPIC",
                         "canonicalKey":"it","availableFrom":"2026-09-24"}]}
                        """, MediaType.APPLICATION_JSON));
        var rows = client.candidates(123L, true, LocalDate.of(2026, 9, 24));
        assertEquals("CUSTOM:10", rows.get(0).key());
        assertEquals(LocalDate.of(2026, 9, 24), rows.get(0).availableFrom());
        server.verify();
    }

    @Test
    void missingMandatoryFieldIsNotSilentlyConvertedToDefaultValue() {
        server.expect(requestTo(ADMIN))
                .andRespond(withSuccess("[" + ROW.replace("\"sortOrder\":0,", "") + "]", MediaType.APPLICATION_JSON));
        assertEquals(HttpStatus.BAD_GATEWAY,
                assertThrows(LanguageLearningServiceException.class, () -> client.listSystem(900L)).getStatus());
        server.verify();
    }

    @Test
    void malformedResponseIsNotReturnedAsEmptyCatalog() {
        server.expect(requestTo(USER))
                .andRespond(withSuccess("{\"systemKeywords\":null,\"customKeywords\":[]}", MediaType.APPLICATION_JSON));
        assertEquals(HttpStatus.BAD_GATEWAY,
                assertThrows(LanguageLearningServiceException.class, () -> client.list(123L, false, "ko")).getStatus());
        server.verify();
    }

    @Test
    void remoteBusinessErrorIsPreservedAndNotRetried() {
        server.expect(requestTo(USER + "/custom"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"KEYWORD_DUPLICATED\",\"message\":\"이미 등록된 키워드입니다.\"}"));
        var error = assertThrows(LanguageLearningServiceException.class,
                () -> client.createCustom(123L, false,
                        new KeywordCreateRequestDto("IT", KeywordType.TOPIC, null, null, null)));
        assertEquals("KEYWORD_DUPLICATED", error.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        server.verify();
    }

    @Test
    void serverErrorIsNotAutomaticallyRetried() {
        server.expect(requestTo(USER))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"TEST_UNAVAILABLE\",\"message\":\"테스트 장애\"}"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                assertThrows(LanguageLearningServiceException.class, () -> client.list(123L, true, null)).getStatus());
        server.verify();
    }
}
