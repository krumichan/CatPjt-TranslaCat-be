package jp.co.translacat.infrastructure.languagelearning.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.languagelearning.setting.dto.request.AdminSettingUpdateRequestDto;
import jp.co.translacat.domain.languagelearning.setting.dto.request.UserSettingUpdateRequestDto;
import jp.co.translacat.domain.languagelearning.setting.dto.response.AdminSettingResponseDto;
import jp.co.translacat.domain.languagelearning.setting.dto.response.UserSettingResponseDto;
import jp.co.translacat.domain.languagelearning.listening.setting.model.ListeningPolicySnapshot;
import jp.co.translacat.infrastructure.languagelearning.client.dto.*;
import jp.co.translacat.infrastructure.languagelearning.client.security.LanguageLearningInternalJwtProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import java.io.IOException;

/** Ktor가 Settings의 유일한 저장/정책 소유자다. 응용 수준 재시도·옛 DB fallback·dual-write는 없다. */
public class LanguageLearningSettingsClient {
    private static final String USER_SETTINGS_PATH = "/internal/v1/language-learning/settings";
    private static final String ADMIN_SETTINGS_PATH = "/internal/v1/admin/language-learning/settings";
    private static final String SERVICE_SETTINGS_PATH = "/internal/v1/service/language-learning/settings";
    private final RestClient restClient;
    private final LanguageLearningInternalJwtProvider jwtProvider;
    private final ObjectMapper objectMapper;

    public LanguageLearningSettingsClient(RestClient restClient, LanguageLearningInternalJwtProvider jwtProvider,
                                          ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.jwtProvider = jwtProvider;
        // 다른 BE API의 JSON 정책을 변경하지 않는다. 누락된 primitive를 0/false로 받아들이지 않는다.
        this.objectMapper = objectMapper.copy()
                .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public UserSettingResponseDto getUserSettings(Long userId) {
        return read(() -> restClient.get().uri(USER_SETTINGS_PATH)
                .header(HttpHeaders.AUTHORIZATION, bearer(jwtProvider.issueUserToken(userId)))
                .retrieve().body(byte[].class), UserSettingResponseDto.class);
    }
    public UserSettingResponseDto updateUserSettings(Long userId, UserSettingUpdateRequestDto request) {
        return read(() -> restClient.patch().uri(USER_SETTINGS_PATH)
                .header(HttpHeaders.AUTHORIZATION, bearer(jwtProvider.issueUserToken(userId)))
                .body(request).retrieve().body(byte[].class), UserSettingResponseDto.class);
    }
    public AdminSettingResponseDto getAdminSettings(Long adminUserId) {
        return read(() -> restClient.get().uri(ADMIN_SETTINGS_PATH)
                .header(HttpHeaders.AUTHORIZATION, bearer(jwtProvider.issueAdminToken(adminUserId)))
                .retrieve().body(byte[].class), AdminSettingResponseDto.class);
    }
    public AdminSettingResponseDto updateAdminSettings(Long adminUserId, AdminSettingUpdateRequestDto request) {
        return read(() -> restClient.patch().uri(ADMIN_SETTINGS_PATH)
                .header(HttpHeaders.AUTHORIZATION, bearer(jwtProvider.issueAdminToken(adminUserId)))
                .body(request).retrieve().body(byte[].class), AdminSettingResponseDto.class);
    }
    public UserSettingsSnapshotDto getUserSnapshot(Long userId) {
        requireUserId(userId);
        return serviceGet("/users/" + userId, UserSettingsSnapshotDto.class);
    }
    public LearningDateResponseDto resolveLearningDate(Long userId) {
        requireUserId(userId);
        return serviceGet("/users/" + userId + "/learning-date", LearningDateResponseDto.class);
    }
    public AdminSettingResponseDto getAdminPolicy() { return serviceGet("/admin", AdminSettingResponseDto.class); }
    public ListeningPolicySnapshot getListeningPolicy() { return serviceGet("/listening-policy", ListeningPolicySnapshot.class); }
    public ConfiguredLanguagePairsDto configuredLanguagePairs() { return serviceGet("/language-pairs", ConfiguredLanguagePairsDto.class); }

    public SelectionDeliveryResponseDto rememberListeningSelection(Long userId, SelectionDeliveryRequestDto request) {
        return read(() -> restClient.post().uri(USER_SETTINGS_PATH + "/listening-selection")
                .header(HttpHeaders.AUTHORIZATION, bearer(jwtProvider.issueUserToken(userId)))
                .body(request).retrieve().body(byte[].class), SelectionDeliveryResponseDto.class);
    }

    private <T> T serviceGet(String path, Class<T> type) {
        return read(() -> restClient.get().uri(SERVICE_SETTINGS_PATH + path)
                .header(HttpHeaders.AUTHORIZATION, bearer(jwtProvider.issueSettingsServiceToken()))
                .retrieve().body(byte[].class), type);
    }

    private <T> T read(ClientCall call, Class<T> type) {
        try {
            byte[] bytes = call.execute();
            if (bytes == null || bytes.length == 0) throw failure("LL_EMPTY_RESPONSE", "Language Learning 서비스가 빈 응답을 반환했습니다.");
            if (bytes.length > 1_048_576) throw failure("LL_SETTINGS_CONTRACT_ERROR", "Language Learning 설정 응답이 너무 큽니다.");
            T value = objectMapper.readValue(bytes, type);
            if (value == null) throw failure("LL_EMPTY_RESPONSE", "Language Learning 서비스가 빈 응답을 반환했습니다.");
            validateResponse(value);
            return value;
        } catch (RestClientResponseException e) {
            throw responseException(e);
        } catch (ResourceAccessException e) {
            throw new LanguageLearningServiceException(HttpStatus.BAD_GATEWAY, "LL_SERVICE_UNAVAILABLE",
                    "Language Learning 서비스에 연결할 수 없습니다.", e);
        } catch (IOException | RestClientException | IllegalArgumentException e) {
            throw new LanguageLearningServiceException(HttpStatus.BAD_GATEWAY, "LL_SERVICE_RESPONSE_ERROR",
                    "Language Learning 서비스 응답을 처리할 수 없습니다.", e);
        }
    }

    private static void validateResponse(Object value) {
        if (value instanceof UserSettingsSnapshotDto(
                Long userId, java.time.LocalDate learningDate, java.time.LocalDateTime revision,
                UserSettingResponseDto settings
        )) {
            if (userId == null || userId <= 0 || learningDate == null || revision == null || settings == null) {
                throw failure("LL_SETTINGS_CONTRACT_ERROR", "사용자 설정 snapshot 계약이 유효하지 않습니다.");
            }
            validateResponse(settings);
        } else if (value instanceof UserSettingResponseDto dto) {
            if (dto.timezone() == null || dto.timezone().isBlank() || dto.speakingVoiceId() == null
                    || dto.speakingPlaybackSpeed() == null || dto.defaultListeningTaskTypes() == null
                    || dto.configured() != (dto.originLanguage() != null && dto.learningLanguage() != null)) {
                throw failure("LL_SETTINGS_CONTRACT_ERROR", "사용자 설정 응답이 유효하지 않습니다.");
            }
        } else if (value instanceof LearningDateResponseDto(java.time.LocalDate date) && date == null) {
            throw failure("LL_SETTINGS_CONTRACT_ERROR", "학습 날짜가 없습니다.");
        } else if (value instanceof ConfiguredLanguagePairsDto(
                java.util.List<jp.co.translacat.domain.languagelearning.setting.model.ConfiguredLanguagePair> pairs
        )) {
            if (pairs == null || pairs.stream().anyMatch(pair -> pair == null || pair.originLanguage() == null
                    || pair.learningLanguage() == null || pair.originLanguage().isBlank() || pair.learningLanguage().isBlank())) {
                throw failure("LL_SETTINGS_CONTRACT_ERROR", "언어쌍 응답이 유효하지 않습니다.");
            }
        } else if (value instanceof ListeningPolicySnapshot dto && (dto.profilePolicyVersion() == null || dto.modelConfigVersion() == null)) {
            throw failure("LL_SETTINGS_CONTRACT_ERROR", "Listening 정책 버전이 없습니다.");
        } else if (value instanceof SelectionDeliveryResponseDto(
                String status
        ) && (status == null || !java.util.Set.of("APPLIED", "DUPLICATE", "SUPERSEDED").contains(status))) {
            throw failure("LL_SETTINGS_CONTRACT_ERROR", "설정 전달 결과가 유효하지 않습니다.");
        }
    }

    private LanguageLearningServiceException responseException(RestClientResponseException e) {
        InternalApiErrorDto error = readError(e.getResponseBodyAsByteArray());
        String code = error == null || error.code() == null ? "LL_SERVICE_ERROR" : error.code();
        String message = error == null || error.message() == null ? "Language Learning 서비스 요청에 실패했습니다." : error.message();
        return new LanguageLearningServiceException(resolveStatus(e.getStatusCode()), code, message, e);
    }
    private InternalApiErrorDto readError(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > 65536) return null;
        try { return objectMapper.readValue(bytes, InternalApiErrorDto.class); }
        catch (IOException | IllegalArgumentException ignored) { return null; }
    }
    private HttpStatus resolveStatus(HttpStatusCode code) {
        HttpStatus status = HttpStatus.resolve(code.value());
        return status == null ? HttpStatus.BAD_GATEWAY : status;
    }
    private static LanguageLearningServiceException failure(String code, String message) {
        return new LanguageLearningServiceException(HttpStatus.BAD_GATEWAY, code, message);
    }
    private static void requireUserId(Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("userId는 양수여야 합니다.");
    }
    private String bearer(String token) { return "Bearer " + token; }
    @FunctionalInterface private interface ClientCall { byte[] execute(); }
}
