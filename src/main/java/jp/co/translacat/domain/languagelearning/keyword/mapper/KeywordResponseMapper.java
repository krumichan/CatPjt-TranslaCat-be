package jp.co.translacat.domain.languagelearning.keyword.mapper;

import jp.co.translacat.domain.languagelearning.common.enums.KeywordSource;
import jp.co.translacat.domain.languagelearning.keyword.dto.response.KeywordResponseDto;
import jp.co.translacat.domain.languagelearning.keyword.entity.CustomKeyword;
import jp.co.translacat.domain.languagelearning.keyword.entity.SystemKeyword;

import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class KeywordResponseMapper {

    public KeywordResponseDto fromSystem(
            SystemKeyword keyword,
            boolean selected,
            LocalDate pendingEffectiveDate
    ) {
        return new KeywordResponseDto(
                keyword.getId(),
                keyword.getText(),
                KeywordSource.SYSTEM,
                keyword.getType(),
                keyword.getCanonicalKey(),
                keyword.isActive(),
                selected,
                pendingEffectiveDate
        );
    }

    public KeywordResponseDto fromCustom(CustomKeyword keyword) {
        return new KeywordResponseDto(
                keyword.getId(),
                keyword.desiredText(),
                KeywordSource.CUSTOM,
                keyword.desiredType(),
                keyword.desiredCanonicalKey(),
                keyword.desiredActive(),
                keyword.desiredActive(),
                keyword.getPendingEffectiveDate()
        );
    }
}
