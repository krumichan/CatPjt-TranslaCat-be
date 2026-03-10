package jp.co.translacat.global.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Sort;

@Getter
@Setter
@NoArgsConstructor
public class OrderDto {
    private String column;
    private Sort.Direction direction;
}
