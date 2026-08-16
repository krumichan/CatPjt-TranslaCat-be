package jp.co.translacat.domain.languagelearning.keyword.mapper;

import jp.co.translacat.domain.languagelearning.common.enums.KeywordSource;
import jp.co.translacat.domain.languagelearning.keyword.dto.response.KeywordResponseDto;
import jp.co.translacat.domain.languagelearning.keyword.entity.CustomKeyword;
import jp.co.translacat.domain.languagelearning.keyword.entity.SystemKeyword;
import jp.co.translacat.domain.languagelearning.keyword.model.KeywordDisplayName;

import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class KeywordResponseMapper {

    public KeywordResponseDto fromSystem(
            SystemKeyword keyword,
            boolean selected,
            LocalDate pendingEffectiveDate
    ) {
        return fromSystem(
                keyword,
                selected,
                pendingEffectiveDate,
                null
        );
    }

    public KeywordResponseDto fromSystem(
            SystemKeyword keyword,
            boolean selected,
            LocalDate pendingEffectiveDate,
            KeywordDisplayName displayName
    ) {
        SystemKeyword parent = keyword.getParentKeyword();
        return new KeywordResponseDto(
                keyword.getId(),
                keyword.getText(),
                displayName == null
                        ? keyword.getText()
                        : displayName.primary(),
                displayName == null
                        ? null
                        : displayName.secondary(),
                KeywordSource.SYSTEM,
                keyword.getType(),
                keyword.getCanonicalKey(),
                parent == null ? null : parent.getId(),
                parent == null ? null : parent.getCanonicalKey(),
                keyword.getSortOrder(),
                keyword.isActive(),
                selected,
                pendingEffectiveDate
        );
    }

    public KeywordResponseDto fromCustom(CustomKeyword keyword) {
        SystemKeyword parent = keyword.desiredParentSystemKeyword();
        return new KeywordResponseDto(
                keyword.getId(),
                keyword.desiredText(),
                null,
                null,
                KeywordSource.CUSTOM,
                keyword.desiredType(),
                keyword.desiredCanonicalKey(),
                parent == null ? null : parent.getId(),
                parent == null ? null : parent.getCanonicalKey(),
                0,
                keyword.desiredActive(),
                keyword.desiredActive(),
                keyword.getPendingEffectiveDate()
        );
    }
}
