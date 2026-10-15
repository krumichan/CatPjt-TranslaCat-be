package jp.co.translacat.novel;

import jp.co.translacat.domain.novel.dictionary.dto.DictionaryRegisterDto;
import jp.co.translacat.domain.novel.dictionary.entity.JapaneseDictionary;
import jp.co.translacat.domain.novel.dictionary.repository.JapaneseDictionaryRepository;
import jp.co.translacat.domain.novel.dictionary.service.DictionaryService;
import jp.co.translacat.infrastructure.japanese.FuriganaProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DictionaryServiceTest {
    @Test
    void newReadingIsStoredAndPublishedWithoutChangingEpisodeContent() {
        // 준비: 아직 사전에 없는 단어다.
        var repository = mock(JapaneseDictionaryRepository.class);
        var processor = mock(FuriganaProcessor.class);
        var service = new DictionaryService(repository, processor);
        var request = request("猫", "まお");
        when(repository.findBySurface("猫")).thenReturn(Optional.empty());
        when(repository.findAll()).thenReturn(List.of(JapaneseDictionary.create("猫", "まお")));

        // 실행: 등록 직후 사용자 사전 snapshot을 갱신한다.
        boolean registered = service.register(request);

        // 검증: 신규 저장과 발음 cache 갱신만 일어난다.
        assertThat(registered).isTrue();
        verify(repository).save(any(JapaneseDictionary.class));
        verify(processor).syncCachedDict(Map.of("猫", "まお"));
    }

    @Test
    void existingReadingCanBeCorrectedWithoutDuplicateInsert() {
        // 준비: 동일 표면형이 이미 등록되어 있다.
        var repository = mock(JapaneseDictionaryRepository.class);
        var processor = mock(FuriganaProcessor.class);
        var service = new DictionaryService(repository, processor);
        var existing = JapaneseDictionary.create("猫", "ねこ");
        when(repository.findBySurface("猫")).thenReturn(Optional.of(existing));
        when(repository.findAll()).thenReturn(List.of(existing));

        // 실행: 같은 단어의 발음만 교정한다.
        boolean registered = service.register(request("猫", "まお"));

        // 검증: 중복 삽입 없이 DB entity와 새 화면 cache가 같은 발음을 사용한다.
        assertThat(registered).isTrue();
        assertThat(existing.getReading()).isEqualTo("まお");
        verify(repository, never()).save(any(JapaneseDictionary.class));
        verify(processor).syncCachedDict(Map.of("猫", "まお"));
    }

    @Test
    void rolledBackRegistrationDoesNotPublishUncommittedReading() {
        // 준비: 저장 도중 트랜잭션이 롤백되는 경우를 제어한다.
        var repository = mock(JapaneseDictionaryRepository.class);
        var processor = mock(FuriganaProcessor.class);
        var service = new DictionaryService(repository, processor);
        when(repository.findBySurface("猫")).thenReturn(Optional.empty());
        when(repository.findAll()).thenReturn(List.of(JapaneseDictionary.create("猫", "まお")));
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        try {
            // 실행: register가 예약한 commit 후 callback에 롤백을 전달한다.
            service.register(request("猫", "まお"));
            verify(processor, never()).syncCachedDict(any());
            for (var callback : TransactionSynchronizationManager.getSynchronizations()) {
                callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            }

            // 검증: DB에 없는 발음이 ruby cache에 반영되지 않는다.
            verify(processor, never()).syncCachedDict(any());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    void committedRegistrationPublishesReadingBeforeReturningToCaller() {
        // 준비: 실제 트랜잭션의 commit 완료 callback을 제어한다.
        var repository = mock(JapaneseDictionaryRepository.class);
        var processor = mock(FuriganaProcessor.class);
        var service = new DictionaryService(repository, processor);
        when(repository.findBySurface("猫")).thenReturn(Optional.empty());
        when(repository.findAll()).thenReturn(List.of(JapaneseDictionary.create("猫", "まお")));
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        try {
            // 실행: 등록 중에는 cache를 바꾸지 않고 commit 후 발행한다.
            service.register(request("猫", "まお"));
            verify(processor, never()).syncCachedDict(any());
            for (var callback : TransactionSynchronizationManager.getSynchronizations()) {
                callback.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
            }

            // 검증: 성공한 POST 다음 reader GET에는 새 발음 사전이 준비된다.
            verify(processor).syncCachedDict(Map.of("猫", "まお"));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    void surroundingSpacesAreRemovedBeforeDictionaryLookupAndStorage() {
        // 준비: 선택 복사에서 앞뒤 공백이 들어왔으나 DB에는 정규화된 단어가 없다.
        var repository = mock(JapaneseDictionaryRepository.class);
        var processor = mock(FuriganaProcessor.class);
        var service = new DictionaryService(repository, processor);
        when(repository.findBySurface("猫")).thenReturn(Optional.empty());
        when(repository.findAll()).thenReturn(List.of(JapaneseDictionary.create("猫", "まお")));

        // 실행
        service.register(request("  猫  ", "  まお  "));

        // 검증: 원문과 정확히 일치하는 표면형을 등록하고 캐시에 게시한다.
        var saved = org.mockito.ArgumentCaptor.forClass(JapaneseDictionary.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getSurface()).isEqualTo("猫");
        assertThat(saved.getValue().getReading()).isEqualTo("まお");
        verify(processor).syncCachedDict(Map.of("猫", "まお"));
    }

    private static DictionaryRegisterDto request(String surface, String reading) {
        var request = new DictionaryRegisterDto();
        ReflectionTestUtils.setField(request, "surface", surface);
        ReflectionTestUtils.setField(request, "reading", reading);
        return request;
    }
}
