package jp.co.translacat.global.utils;

import jp.co.translacat.global.dto.PageableDto;
import lombok.experimental.UtilityClass;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;

import java.util.List;
import java.util.Objects;

@UtilityClass
public class PagingUtil {

    public <T> Page<T> toPage(List<T> contents, long total, PageableDto pageableDto) {
        Pageable pageable = Objects.isNull(pageableDto.getPageRequest())
                ? Pageable.unpaged()
                : pageableDto.getPageRequest();

        return new PageImpl<>(contents, pageable, total);
    }

    public <T> PagedModel<T> toPagedModel(Page<T> page) {
        return new PagedModel<>(page);
    }
}