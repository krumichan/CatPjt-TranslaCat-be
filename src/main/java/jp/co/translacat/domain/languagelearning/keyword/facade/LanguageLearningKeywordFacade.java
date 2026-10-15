package jp.co.translacat.domain.languagelearning.keyword.facade;

import jp.co.translacat.domain.languagelearning.keyword.dto.request.KeywordCreateRequestDto;
import jp.co.translacat.domain.languagelearning.keyword.dto.request.KeywordUpdateRequestDto;
import jp.co.translacat.domain.languagelearning.keyword.dto.response.EffectiveKeywordsResponseDto;
import jp.co.translacat.domain.languagelearning.keyword.dto.response.KeywordListResponseDto;
import jp.co.translacat.domain.languagelearning.keyword.dto.response.KeywordResponseDto;
import jp.co.translacat.domain.languagelearning.keyword.port.KeywordCatalogGateway;
import jp.co.translacat.domain.languagelearning.setting.port.UserSettingsGateway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * 외부 FE 계약은 유지한다. 키워드 Entity/Repository를 조회하거나 저장하지 않는다.
 */
@Service
public class LanguageLearningKeywordFacade {
    private final KeywordCatalogGateway keywords;
    private final UserSettingsGateway settings;
    private final Clock clock;

    @Autowired
    public LanguageLearningKeywordFacade(KeywordCatalogGateway keywords, UserSettingsGateway settings) {
        this(keywords, settings, Clock.systemUTC());
    }

    LanguageLearningKeywordFacade(KeywordCatalogGateway keywords, UserSettingsGateway settings, Clock clock) {
        this.keywords = keywords;
        this.settings = settings;
        this.clock = clock;
    }

    public KeywordListResponseDto getKeywords(Long userId, String uiLocale) {
        return keywords.getKeywords(userId, uiLocale);
    }

    public EffectiveKeywordsResponseDto getEffectiveKeywords(Long userId) {
        // 설정 조회가 승격한 활성 시간대로 오늘을 결정한다. 예약 시간대와 클라이언트 날짜는 사용하지 않는다.
        var setting = settings.get(userId);
        LocalDate learningDate = LocalDate.now(clock.withZone(learningZone(setting.timezone())));

        // 편집용 희망 상태 목록 대신 LL이 해당 학습일에 적용하는 후보만 반환한다.
        return new EffectiveKeywordsResponseDto(keywords.candidates(userId, learningDate));
    }

    private static ZoneId learningZone(String timezone) {
        try {
            return ZoneId.of(timezone == null ? "Asia/Tokyo" : timezone);
        } catch (DateTimeException ignored) {
            // LL의 기존 저장 시간대 보정 규칙과 같은 기본값을 유지한다.
            return ZoneId.of("Asia/Tokyo");
        }
    }

    public KeywordResponseDto createCustomKeyword(Long userId, KeywordCreateRequestDto request) {
        return keywords.createCustom(userId, request);
    }

    public KeywordResponseDto updateCustomKeyword(Long userId, Long keywordId, KeywordUpdateRequestDto request) {
        return keywords.updateCustom(userId, keywordId, request);
    }

    public void deleteCustomKeyword(Long userId, Long keywordId) {
        keywords.deleteCustom(userId, keywordId);
    }

    public KeywordResponseDto updateSystemKeywordSelection(Long userId, Long keywordId, boolean selected) {
        return keywords.selectSystem(userId, keywordId, selected);
    }

    public List<KeywordResponseDto> getSystemKeywordsForAdmin(Long adminUserId) {
        return keywords.getSystemKeywords(adminUserId);
    }

    public KeywordResponseDto createSystemKeyword(Long adminUserId, KeywordCreateRequestDto request) {
        return keywords.createSystem(adminUserId, request);
    }

    public KeywordResponseDto updateSystemKeyword(Long adminUserId, Long keywordId, KeywordUpdateRequestDto request) {
        return keywords.updateSystem(adminUserId, keywordId, request);
    }
}
