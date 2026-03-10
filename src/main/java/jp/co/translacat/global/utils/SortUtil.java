package jp.co.translacat.global.utils;

import jp.co.translacat.global.dto.OrderDto;
import lombok.experimental.UtilityClass;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Objects;

/**
 * SortUtility 클래스
 *
 * Spring Data JPA에서 사용하는 Sort 객체를 생성하는 유틸리티 클래스입니다.
 * Order 객체 리스트를 받아 Sort로 변환하며, 기본 정렬 컬럼과 방향도 제공합니다.
 */
@UtilityClass
public class SortUtil {

    // 기본 정렬 컬럼
    private final String DEF_SORT_COLUMN = "created_at";

    // 기본 정렬 방향 (내림차순)
    private final Sort.Direction DEF_DIRECTION = Sort.Direction.DESC;

    /**
     * Order 리스트를 기반으로 Spring Data Sort 객체 생성
     *
     * @param list 정렬 정보 리스트 (Order 객체)
     * @return Sort 객체, 리스트가 비어있으면 기본 정렬 적용
     */
    public Sort getSort(List<OrderDto> list) {

        if (Objects.isNull(list) || list.isEmpty()) {
            return Sort.by(DEF_DIRECTION, DEF_SORT_COLUMN);
        }

        // Order 리스트를 Spring Data의 Sort.Order 객체 리스트로 변환
        List<Sort.Order> orders = list
                .stream()
                .map(a -> new Sort.Order(a.getDirection(), a.getColumn()))
                .toList();

        return Sort.by(orders);
    }
}
