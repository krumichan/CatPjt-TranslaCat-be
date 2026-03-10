package jp.co.translacat.global.utils;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.ComparableExpressionBase;
import com.querydsl.core.types.dsl.EntityPathBase;
import lombok.experimental.UtilityClass;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * QueryDslUtility 클래스
 *
 * QueryDSL 관련 유틸리티 메서드를 제공하는 클래스입니다.
 */
@UtilityClass
@SuppressWarnings("rawtypes")
public class QueryDslUtil {

    // EntityPathBase 객체별로 필드 이름 -> ComparableExpressionBase를 캐싱
    // 매번 Reflection으로 필드를 가져오는 비용을 줄이기 위해 사용
    private final Map<EntityPathBase, Map<String, ComparableExpressionBase>> cache = new ConcurrentHashMap<>();

    /**
     * PageRequest의 Sort 정보를 기반으로 OrderSpecifier 배열을 생성합니다.
     *
     * @param pageRequest 페이지 요청 정보 (Sort 포함)
     * @param value       Q타입 EntityPathBase 객체 (예: QExample)
     * @param cls         value 객체의 클래스
     * @param <T>         EntityPathBase를 상속한 Q타입
     * @return OrderSpecifier 배열 (QueryDSL 정렬용)
     */
    public <T extends EntityPathBase> OrderSpecifier[] orders(PageRequest pageRequest, T value, Class<T> cls) {
        List<OrderSpecifier> orderSpecifiers = new ArrayList<>();
        Sort sorts = pageRequest.getSort();

        // cache에서 value(Q타입)를 키로 필드 이름 -> ComparableExpressionBase 맵 가져오기
        // 없으면 ConcurrentHashMap으로 새로 생성
        Map<String, ComparableExpressionBase> exprMap =
                cache.computeIfAbsent(value, k -> new ConcurrentHashMap<>());

        // 정렬 정보 순회
        for (Sort.Order order : sorts) {
            String column = order.getProperty();

            ComparableExpressionBase expressionBase = exprMap.get(column);

            // Cache에 없으면 Reflection으로 Q타입 객체에서 필드 가져오기
            if (expressionBase == null) {
                try {
                    Field field = cls.getDeclaredField(column);

                    // 조건: public, final, static이 아닌 필드만 처리
                    int mods = field.getModifiers();
                    if (!Modifier.isFinal(mods) || Modifier.isStatic(mods) || !Modifier.isPublic(mods)) {
                        continue;
                    }

                    expressionBase = (ComparableExpressionBase) field.get(value);
                    exprMap.put(column, expressionBase);
                } catch (Exception ignored) {
                    continue;
                }
            }
            orderSpecifiers.add(
                    order.getDirection() == Sort.Direction.ASC ?
                            expressionBase.asc() : expressionBase.desc());
        }

        // OrderSpecifier 배열로 변환 후 반환
        return orderSpecifiers.toArray(new OrderSpecifier[0]);
    }
}
