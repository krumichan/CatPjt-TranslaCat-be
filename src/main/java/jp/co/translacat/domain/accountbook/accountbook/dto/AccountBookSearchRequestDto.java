package jp.co.translacat.domain.accountbook.accountbook.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.util.StringUtils;

@Getter
@Setter
public class AccountBookSearchRequestDto {

    /**
     * 가계부명 / 설명 검색용 키워드
     */
    private String keyword;

    /**
     * 카테고리 필터
     */
    private String category;

    public String normalizedKeyword() {
        return StringUtils.hasText(keyword) ? keyword.trim() : null;
    }

    public String normalizedCategory() {
        return StringUtils.hasText(category) ? category.trim() : null;
    }
}