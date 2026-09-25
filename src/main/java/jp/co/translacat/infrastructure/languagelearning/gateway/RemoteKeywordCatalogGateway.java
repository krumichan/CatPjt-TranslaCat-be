package jp.co.translacat.infrastructure.languagelearning.gateway;

import jp.co.translacat.domain.languagelearning.keyword.dto.request.KeywordCreateRequestDto;
import jp.co.translacat.domain.languagelearning.keyword.dto.request.KeywordUpdateRequestDto;
import jp.co.translacat.domain.languagelearning.keyword.dto.response.KeywordListResponseDto;
import jp.co.translacat.domain.languagelearning.keyword.dto.response.KeywordResponseDto;
import jp.co.translacat.domain.languagelearning.keyword.model.KeywordCandidateSnapshot;
import jp.co.translacat.domain.languagelearning.keyword.port.KeywordCatalogGateway;
import jp.co.translacat.domain.languagelearning.keyword.port.KeywordLearningFacts;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.global.exception.BusinessException;
import jp.co.translacat.infrastructure.languagelearning.client.LanguageLearningKeywordClient;
import jp.co.translacat.infrastructure.languagelearning.client.LanguageLearningServiceException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class RemoteKeywordCatalogGateway implements KeywordCatalogGateway {
    private final ObjectProvider<LanguageLearningKeywordClient> clients;
    private final UserRepository users;
    private final KeywordLearningFacts facts;

    public RemoteKeywordCatalogGateway(ObjectProvider<LanguageLearningKeywordClient> clients, UserRepository users,
                                       KeywordLearningFacts facts) {
        this.clients = clients;
        this.users = users;
        this.facts = facts;
    }

    private LanguageLearningKeywordClient forUser(Long userId) {
        LanguageLearningKeywordClient client = clients.getIfAvailable();
        if (client == null) throw new LanguageLearningServiceException(HttpStatus.SERVICE_UNAVAILABLE,
                "LL_KEYWORDS_REMOTE_DISABLED", "언어학습 키워드 연결이 활성화되지 않았습니다.");
        if (userId == null || userId <= 0 || !users.existsById(userId)) {
            throw new BusinessException("사용자를 찾을 수 없습니다.", LanguageLearningErrorCode.USER_NOT_FOUND);
        }
        return client;
    }

    @Override
    public KeywordListResponseDto getKeywords(Long userId, String uiLocale) {
        return forUser(userId).list(userId, facts.hasStartedLearning(userId), uiLocale);
    }

    @Override
    public KeywordResponseDto createCustom(Long userId, KeywordCreateRequestDto request) {
        return forUser(userId).createCustom(userId, facts.hasStartedLearning(userId), request);
    }

    @Override
    public KeywordResponseDto updateCustom(Long userId, Long keywordId, KeywordUpdateRequestDto request) {
        return forUser(userId).updateCustom(userId, facts.hasStartedLearning(userId), keywordId, request);
    }

    @Override
    public void deleteCustom(Long userId, Long keywordId) {
        forUser(userId).deleteCustom(userId, facts.hasStartedLearning(userId), keywordId);
    }

    @Override
    public KeywordResponseDto selectSystem(Long userId, Long keywordId, boolean selected) {
        return forUser(userId).selectSystem(userId, facts.hasStartedLearning(userId), keywordId, selected);
    }

    @Override
    public List<KeywordResponseDto> getSystemKeywords(Long adminUserId) {
        return forUser(adminUserId).listSystem(adminUserId);
    }

    @Override
    public KeywordResponseDto createSystem(Long adminUserId, KeywordCreateRequestDto request) {
        return forUser(adminUserId).createSystem(adminUserId, request);
    }

    @Override
    public KeywordResponseDto updateSystem(Long adminUserId, Long keywordId, KeywordUpdateRequestDto request) {
        return forUser(adminUserId).updateSystem(adminUserId, keywordId, request);
    }

    @Override
    public List<KeywordCandidateSnapshot> candidates(Long userId, LocalDate learningDate) {
        return forUser(userId).candidates(userId, facts.hasStartedLearning(userId), learningDate);
    }
}
