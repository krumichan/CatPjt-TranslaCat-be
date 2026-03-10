package jp.co.translacat.global.utils;

import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
public class BatchProcessor {
    private final Executor aiExecutor;

    /**
     * 리스트를 분할하여 병렬 처리하고, 특정 키를 기준으로 정렬하여 반환합니다.
     *
     * @param source         원본 리스트
     * @param batchSize      분할할 크기
     * @param task           병렬로 수행할 작업 (비즈니스 로직)
     * @param sortComparator 정렬 기준
     */
    public <T, R> List<R> processParallel(
            List<T> source,
            int batchSize,
            Function<List<T>, List<R>> task,
            Comparator<R> sortComparator
    ) {
        if (Objects.isNull(source) || source.isEmpty()) {
            return Collections.emptyList();
        }

        // 1. List 분할
        List<List<T>> batches = Lists.partition(source, batchSize);

        // 2. 병렬 실행
        List<CompletableFuture<List<R>>> futures = batches.stream()
            .map(batch -> CompletableFuture.supplyAsync(() -> task.apply(batch), aiExecutor))
            .toList();

        // 3. 취합 및 정렬
        Stream<R> stream = futures.stream()
            .map(CompletableFuture::join)
            .flatMap(List::stream);

        return (Objects.isNull(sortComparator))
            ? stream.toList()
            : stream.sorted(sortComparator).toList();
    }

    /**
     * 리스트를 분할하여 병렬 처리한다.
     *
     * @param source         원본 리스트
     * @param batchSize      분할할 크기
     * @param task           병렬로 수행할 작업 (비즈니스 로직)
     */
    public <T, R> List<R> processParallel(
            List<T> source,
            int batchSize,
            Function<List<T>, List<R>> task
    ) {
        return processParallel(source, batchSize, task, null);
    }
}
