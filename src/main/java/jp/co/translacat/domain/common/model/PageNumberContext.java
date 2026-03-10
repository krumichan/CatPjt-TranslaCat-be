package jp.co.translacat.domain.common.model;

import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

@Getter
@Builder
public class PageNumberContext {
    private Integer first;
    private Integer prev;
    private Integer next;
    private Integer last;

    private Integer current;
    private List<Integer> pages;

    public static PageNumberContext empty() {
        return PageNumberContext.builder().pages(Collections.emptyList()).build();
    }
}
