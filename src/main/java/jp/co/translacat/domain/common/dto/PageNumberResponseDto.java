package jp.co.translacat.domain.common.dto;

import jp.co.translacat.domain.common.model.PageNumberContext;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class PageNumberResponseDto {
    private Integer firstPage;
    private Integer prevPage;
    private Integer nextPage;
    private Integer lastPage;

    private Integer current;
    private List<Integer> pages;

    public static PageNumberResponseDto of(PageNumberContext context) {
        PageNumberResponseDto response = new PageNumberResponseDto();
        response.setFirstPage(context.getFirst());
        response.setPrevPage(context.getPrev());
        response.setNextPage(context.getNext());
        response.setLastPage(context.getLast());
        response.setCurrent(context.getCurrent());
        response.setPages(context.getPages());
        return response;
    }
}
