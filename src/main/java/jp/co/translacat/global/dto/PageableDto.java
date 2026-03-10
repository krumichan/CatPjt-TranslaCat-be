package jp.co.translacat.global.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jp.co.translacat.global.paging.Pagination;
import jp.co.translacat.global.utils.SortUtil;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.PageRequest;

@Getter
@Setter
@NoArgsConstructor
public class PageableDto {
    @Schema(hidden = true)
    private PageRequest pageRequest;
    private Pagination pagination;

    public void setPagination(Pagination pagination) {
        this.pagination = pagination;

        if (pagination.isPagingYn()) {
            this.pageRequest = PageRequest.of(pagination.getPage()
                    , pagination.getPerPage()
                    , SortUtil.getSort(pagination.getOrders()));
        } else {
            this.pageRequest = PageRequest.of(1
                    , 1
                    , SortUtil.getSort(pagination.getOrders()));
        }
    }

    @Schema(hidden = true)
    public int getPage() {
        return pageRequest.getPageNumber();
    }

    @Schema(hidden = true)
    public int getPageSize() {
        return pageRequest.getPageSize();
    }

    @Schema(hidden = true)
    public long getOffset() {
        return getPage() <= 0 ? 0 : (long) getPage() * getPageSize();
    }

    @Schema(hidden = true)
    public boolean isSorted() {
        return pageRequest.getSort().isSorted();
    }
}