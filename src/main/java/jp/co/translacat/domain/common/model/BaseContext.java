package jp.co.translacat.domain.common.model;

import jp.co.translacat.domain.novel.translation.model.TranslationUnit;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseContext<E> {

    @Getter
    @Builder
    public static class TranslationComparison {
        private TranslationUnit unit;

        private String dbRawJa;
        private String dbJa;
        private String dbKo;

        // 원문이 비어있는지 확인
        public boolean isSourceEmpty() {
            return unit.getRawJa().isBlank();
        }

        // 원문 내용이 변경되었는지 확인
        public boolean isSourceChanged() {
            return !unit.getRawJa().equals(this.dbRawJa);
        }

        // DB에 기존 번역 데이터가 누락되었는지 확인
        public boolean isHistoryMissing() {
            return dbJa == null || dbJa.isBlank() || dbKo == null || dbKo.isBlank();
        }
    }

    protected abstract List<TranslationComparison> getTranslationComparisons(E existing);

    public List<TranslationUnit> getAllUnit() {
        List<TranslationComparison> translationComparisons = this.getTranslationComparisons(null);
        return translationComparisons.stream()
            .map(TranslationComparison::getUnit)
            .toList();
    }

    /**
     * DB 데이터와 비교하여 원문(Ja)이 다른 필드(Dirty)만 리스트로 반환합니다.
     * 원문이 같다면 기존 번역(Ko)을 즉시 채워넣습니다.
     */
    public List<TranslationUnit> compareAndGetDirtyUnits(E existing) {
        List<TranslationUnit> dirtyUnits = new ArrayList<>();
        List<TranslationComparison> translationComparisons = this.getTranslationComparisons(existing);

        for (TranslationComparison translationComparison : translationComparisons) {
            this.syncOrCollect(dirtyUnits, translationComparison);
        }

        return dirtyUnits;
    }

    // 헬퍼: 원문이 같으면 DB 값을 쓰고, 다르면 번역 큐에 추가
    private void syncOrCollect(List<TranslationUnit> dirtyUnits, TranslationComparison tc) {

        // 1. 원문이 애초에 공백이면 번역할 필요 없음 (그대로 유지)
        if (tc.isSourceEmpty()) {
            return;
        }

        // 2. DB에 데이터가 없거나, 원문(RawJa) 자체가 변했다면 무조건 새로 번역해야 함!
        if (tc.isHistoryMissing() || tc.isSourceChanged()) {
            dirtyUnits.add(tc.getUnit());
            return;
        }

        // 3. 위 조건에 걸리지 않았다면(내용이 같고 DB 데이터도 온전함), 기존 데이터 이식
        tc.getUnit().setJa(tc.getDbJa());
        tc.getUnit().setKo(tc.getDbKo());
    }
}
