package jp.co.translacat.global.paging;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Positive;
import jp.co.translacat.global.dto.OrderDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Pagination {
    @Positive
    @JsonProperty(value = "size")
    private Integer perPage;
    @Positive
    private Integer page;
    private transient List<OrderDto> orders;
    private boolean pagingYn;
}
