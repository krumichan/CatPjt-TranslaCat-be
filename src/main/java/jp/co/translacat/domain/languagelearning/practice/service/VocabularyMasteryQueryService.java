package jp.co.translacat.domain.languagelearning.practice.service;

import jp.co.translacat.domain.languagelearning.practice.dto.response.VocabularyMasterySummaryResponseDto;
import jp.co.translacat.domain.languagelearning.practice.port.PracticeGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VocabularyMasteryQueryService {
    private final PracticeGateway gateway;

    public VocabularyMasterySummaryResponseDto get(Long userId) {
        return gateway.mastery(userId);
    }
}
