package jp.co.translacat.novel;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.novel.novel.model.NovelContext;
import jp.co.translacat.domain.novel.novel.model.RawEpisodeContext;
import jp.co.translacat.domain.novel.novel.service.NovelTocTranslationService;
import jp.co.translacat.domain.novel.translation.model.TranslationUnit;
import jp.co.translacat.novel.application.FairAiScheduler;
import jp.co.translacat.novel.application.NovelExecutionSettings;
import jp.co.translacat.novel.application.NovelPorts;
import jp.co.translacat.novel.application.NovelRepository;
import jp.co.translacat.novel.application.RequestTrace;
import jp.co.translacat.novel.domain.EpisodeKey;
import jp.co.translacat.novel.domain.NovelProblem;
import jp.co.translacat.novel.domain.SourceEpisode;
import jp.co.translacat.novel.domain.TranslationOptions;
import jp.co.translacat.novel.infrastructure.persistence.NovelTocStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NovelTocTranslationTest {
    private final EpisodeKey key = new EpisodeKey("kakuyomu", "11054822662896759076", "1");
    private JdbcTemplate jdbc;
    private NovelTocStore store;
    private NovelRepository repository;
    private FairAiScheduler scheduler;
    private FakeAi ai;
    private NovelTocTranslationService service;

    @BeforeEach void prepare() {
        var data = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("novel-toc-h2.sql")).execute(data);
        jdbc = new JdbcTemplate(data);
        store = new NovelTocStore(jdbc, new ObjectMapper(), new DataSourceTransactionManager(data));
        repository = mock(NovelRepository.class);
        when(repository.glossary(any(), anyLong(), eq("ko"))).thenReturn(new NovelRepository.Glossary("empty", Map.of()));
        scheduler = new FairAiScheduler(2, 16);
        ai = new FakeAi();
        service = service(ai);
    }

    @AfterEach void close() { scheduler.close(); RequestTrace.clear(); }

    @Test void legacyReviewPatchesOnlyTheInconsistentTitleAndKeepsOriginalStorageAcrossAccounts() {
        // 준비: 기존 50개 목차 중 한 제목만 소개의 인명과 다르다. 정답은 실행 코드가 아닌 fixture에 있다.
        var titles = titles(50);
        titles.get(25).getTitle().setRawJa("真神幸一郎の帰還");
        titles.get(25).getTitle().setKo("진신 코이치로의 귀환");
        ai.review = ignored -> Map.of("26", "마가미 코이치로의 귀환");

        // 실행: 최초 검토 후 같은 legacy 원문/번역을 다른 계정이 다시 조회한다.
        var saved = service.resolve(key, 11, work(), titles, List.of());
        var other = titles(50);
        other.get(25).getTitle().setRawJa("真神幸一郎の帰還");
        other.get(25).getTitle().setKo("진신 코이치로의 귀환");
        service.resolve(key, 22, work(), other, List.of());

        // 검증: 정합성 검토 1회, 신규 번역 0회이며 정상 49개와 기존 오역 저장 사본을 보존한다.
        assertThat(ai.reviews).hasValue(1);
        assertThat(ai.generations).hasValue(0);
        assertThat(ai.lastTitles).hasSize(50);
        assertThat(ai.lastReferences).contains(new NovelPorts.TitleReference("真神幸一郎が帰還した。", "마가미 코이치로가 귀환했다."));
        assertThat(titles.get(25).getTitle().getKo()).isEqualTo("마가미 코이치로의 귀환");
        assertThat(other.get(25).getTitle().getKo()).isEqualTo("마가미 코이치로의 귀환");
        assertThat(saved.get(25).getTitle().getKo()).isEqualTo("진신 코이치로의 귀환");
        for (int index = 0; index < titles.size(); index++) {
            if (index != 25) assertThat(titles.get(index).getTitle().getKo()).isEqualTo(saved.get(index).getTitle().getKo());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM novel_toc_translation WHERE state='READY'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT input_json FROM novel_toc_translation", String.class)).contains("진신 코이치로의 귀환");
    }

    @Test void noChangeReviewIsDurableAcrossServiceRestartWithoutAnotherProviderCall() {
        // 준비
        service.resolve(key, 11, work(), titles(2), List.of());
        var restartedAi = new FakeAi();
        var restarted = service(restartedAi);

        // 실행
        restarted.resolve(key, 22, work(), titles(2), List.of());

        // 검증: 변경 0개도 정상 검토 결과로 영속화하며 재시작을 재검토 사유로 삼지 않는다.
        assertThat(ai.reviews).hasValue(1);
        assertThat(restartedAi.reviews).hasValue(0);
        assertThat(jdbc.queryForObject("SELECT result_json FROM novel_toc_translation", String.class)).isEqualTo("{}");
    }

    @Test void sourceExistingTranslationReferencesGlossaryAndPolicyEachSeparateReviewIdentity() {
        // 준비
        service.resolve(key, 11, work(), titles(2), List.of());

        // 실행: 같은 내용/용어집은 계정·버전이 달라도 공유하고 실제 입력 변경만 검토를 분리한다.
        when(repository.glossary(any(), eq(22L), eq("ko"))).thenReturn(new NovelRepository.Glossary("another-version", Map.of()));
        service.resolve(key, 22, work(), titles(2), List.of());
        var changedSource = titles(2);
        changedSource.getFirst().getTitle().setRawJa("別の題名");
        service.resolve(key, 11, work(), changedSource, List.of());
        var changedKo = titles(2);
        changedKo.getFirst().getTitle().setKo("기존 다른 번역");
        service.resolve(key, 11, work(), changedKo, List.of());
        var changedReference = work();
        changedReference.getSynopsis().setKo("확정된 다른 소개");
        service.resolve(key, 11, changedReference, titles(2), List.of());
        when(repository.glossary(any(), eq(33L), eq("ko"))).thenReturn(new NovelRepository.Glossary("terms", Map.of("術式", "술식")));
        service.resolve(key, 33, work(), titles(2), List.of());
        ai.identity = "another-policy";
        service.resolve(key, 11, work(), titles(2), List.of());

        // 검증
        assertThat(ai.reviews).hasValue(6);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM novel_toc_translation", Integer.class)).isEqualTo(6);
    }

    @Test void newDirtyTitleUsesWorkContextAndGlossaryAndItsNextGetNeedsNoReview() {
        // 준비: 최초 생성도 작품 소개의 확정 번역을 참고한다.
        when(repository.glossary(any(), anyLong(), eq("ko"))).thenReturn(new NovelRepository.Glossary("terms", Map.of("術式", "술식")));
        var titles = titles(1);
        titles.getFirst().getTitle().setKo("");
        ai.generated = Map.of("1", "새 목차 번역");

        // 실행: 새 제목을 저장한 것과 같은 legacy 사본으로 다른 계정이 다시 조회한다.
        var saved = service.resolve(key, 11, work(), titles, List.of(titles.getFirst().getTitle()));
        service.resolve(key, 22, work(), saved, List.of());

        // 검증: 새 생성 1회만 실행하며 같은 문맥으로 생성한 제목은 유료 검토를 반복하지 않는다.
        assertThat(ai.generations).hasValue(1);
        assertThat(ai.reviews).hasValue(0);
        assertThat(ai.lastSegments).extracting(SourceEpisode.Segment::id).containsExactly("1");
        assertThat(ai.lastContext).contains("真神幸一郎が帰還した。");
        assertThat(ai.lastAccepted).contains(Map.of("source", "真神幸一郎が帰還した。", "translation", "마가미 코이치로가 귀환했다."));
        assertThat(ai.lastGlossary).containsEntry("術式", "술식");
        assertThat(saved.getFirst().getTitle().getKo()).isEqualTo("새 목차 번역");
    }

    @Test void mixedPageGeneratesOnlyDirtyTargetsAndKeepsReadyNeighborsOutOfTranslationTargets() {
        // 준비
        var titles = titles(3);
        var dirty = titles.get(1).getTitle();
        dirty.setKo("");
        ai.generated = Map.of("2", "새 제목");

        // 실행
        var saved = service.resolve(key, 11, work(), titles, List.of(dirty));

        // 검증: 기존 제목은 검토 입력일 뿐 신규 번역 target으로 재생성하지 않는다.
        assertThat(ai.lastSegments).extracting(SourceEpisode.Segment::id).containsExactly("2");
        assertThat(ai.reviews).hasValue(1);
        assertThat(saved.getFirst().getTitle().getKo()).isEqualTo("기존 제목 1");
        assertThat(saved.get(2).getTitle().getKo()).isEqualTo("기존 제목 3");
    }

    @Test void invalidReviewDoesNotApplyOrPersistAReadyPatchAndDoesNotAutomaticallyChargeAgain() {
        // 준비
        ai.review = ignored -> Map.of("unknown-id", "잘못된 변경");
        var titles = titles(2);

        // 실행
        var saved = service.resolve(key, 11, work(), titles, List.of());
        var other = titles(2);
        service.resolve(key, 22, work(), other, List.of());

        // 검증: 원래 정상 값과 최초 실패 이력을 보존한다.
        assertThat(titles).extracting(title -> title.getTitle().getKo()).containsExactly("기존 제목 1", "기존 제목 2");
        assertThat(saved).extracting(title -> title.getTitle().getKo()).containsExactly("기존 제목 1", "기존 제목 2");
        assertThat(other).extracting(title -> title.getTitle().getKo()).containsExactly("기존 제목 1", "기존 제목 2");
        assertThat(ai.reviews).hasValue(1);
        assertThat(jdbc.queryForObject("SELECT state FROM novel_toc_translation", String.class)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT failure_code FROM novel_toc_translation", String.class)).isEqualTo("TOC_REVIEW_RESULT_INVALID");
    }

    @Test void invalidGenerationIsNotCachedAsReadyOrCopiedOverTheExistingTitle() {
        // 준비
        var titles = titles(1);
        var dirty = titles.getFirst().getTitle();
        dirty.setKo("");
        ai.generated = Map.of("another", "누락된 제목");

        // 실행
        assertThatThrownBy(() -> service.resolve(key, 11, work(), titles, List.of(dirty)))
                .isInstanceOf(NovelProblem.class).hasMessage("TOC_TRANSLATION_RESULT_INVALID");

        // 검증
        assertThat(dirty.getKo()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT state FROM novel_toc_translation", String.class)).isEqualTo("FAILED");
    }

    @Test void twoActorsCannotRunTheSamePaidReviewConcurrently() throws Exception {
        // 준비: 첫 provider 요청을 완료시키지 않은 상태에서 다른 계정이 같은 검토를 요청한다.
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        ai.review = ignored -> {
            entered.countDown();
            try { if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test timeout"); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
            return Map.of("1", "수정된 제목");
        };
        try (var requests = Executors.newSingleThreadExecutor()) {
            var first = requests.submit(() -> service.resolve(key, 11, work(), titles(2), List.of()));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

            // 실행
            var waiting = titles(2);
            var waitingStorage = service.resolve(key, 22, work(), waiting, List.of());
            assertThat(waiting).extracting(title -> title.getTitle().getKo()).containsExactly("기존 제목 1", "기존 제목 2");
            assertThat(waitingStorage).extracting(RawEpisodeContext::getIdentifier).containsExactly("1", "2");
            release.countDown();
            first.get(5, TimeUnit.SECONDS);
            var reused = titles(2);
            service.resolve(key, 22, work(), reused, List.of());

            // 검증
            assertThat(ai.reviews).hasValue(1);
            assertThat(reused.getFirst().getTitle().getKo()).isEqualTo("수정된 제목");
        } finally {
            release.countDown();
        }
    }

    @Test void syosetuHundredTitlePageKeepsEveryIdSequenceAndTranslationWithoutNewGeneration() {
        // 준비: 카쿠요무의 50개 화면과 달리 기존 소설가가 되자 원본 목차는 한 페이지에 100개다.
        var syosetu = new EpisodeKey("syosetu", "n3159fs", "1");
        var titles = titles(100);

        // 실행
        var storage = service.resolve(syosetu, 11, work(), titles, List.of());

        // 검증: 기존 페이지 경계나 순서를 바꾸지 않고 전체 100개를 검토 입력으로 사용한다.
        assertThat(ai.reviews).hasValue(1);
        assertThat(ai.generations).hasValue(0);
        assertThat(ai.lastTitles).hasSize(100);
        assertThat(titles).hasSize(100);
        assertThat(storage).hasSize(100);
        assertThat(titles).extracting(RawEpisodeContext::getIdentifier)
                .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, 100).mapToObj(Integer::toString).toList());
        assertThat(storage).extracting(episode -> episode.getTitle().getKo())
                .containsExactlyElementsOf(titles.stream().map(episode -> episode.getTitle().getKo()).toList());
    }

    @Test void firstProviderReviewFailureKeepsStoredTitlesAvailableWithoutChargingAgain() {
        // 준비: 선택적 문맥 검토의 첫 provider 요청이 실패해도 정상 저장된 목차를 읽을 수 있어야 한다.
        ai.review = ignored -> { throw new NovelProblem("AI_REVIEW_TIMED_OUT", 502); };
        var titles = titles(2);

        // 실행
        var storage = service.resolve(key, 11, work(), titles, List.of());
        service.resolve(key, 22, work(), titles(2), List.of());

        // 검증: 실패는 영속화하고 표시/저장용 기존 ID·번역을 유지하며 다시 과금하지 않는다.
        assertThat(ai.reviews).hasValue(1);
        assertThat(titles).extracting(RawEpisodeContext::getIdentifier).containsExactly("1", "2");
        assertThat(titles).extracting(title -> title.getTitle().getKo()).containsExactly("기존 제목 1", "기존 제목 2");
        assertThat(storage).extracting(title -> title.getTitle().getKo()).containsExactly("기존 제목 1", "기존 제목 2");
        assertThat(jdbc.queryForObject("SELECT state FROM novel_toc_translation", String.class)).isEqualTo("FAILED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"RUNNING", "FAILED"})
    void restartWithUnfinishedReviewKeepsStoredTitlesAvailableWithoutNewProviderCall(String state) {
        // 준비: 서버가 재시작했을 때 이전 실행의 상태/이력이 DB에 남아 있는 상황이다.
        service.resolve(key, 11, work(), titles(2), List.of());
        jdbc.update("UPDATE novel_toc_translation SET state=?,owner=?,failure_code=?", state,
                "RUNNING".equals(state) ? UUID.randomUUID().toString() : null,
                "FAILED".equals(state) ? "PREVIOUS_REVIEW_FAILED" : null);
        var restartedAi = new FakeAi();
        var restarted = service(restartedAi);
        var titles = titles(2);

        // 실행
        var storage = restarted.resolve(key, 22, work(), titles, List.of());

        // 검증: stale RUNNING/FAILED는 cache miss가 아니며 정상 원래 목차를 그대로 표시한다.
        assertThat(restartedAi.reviews).hasValue(0);
        assertThat(restartedAi.generations).hasValue(0);
        assertThat(titles).extracting(RawEpisodeContext::getIdentifier).containsExactly("1", "2");
        assertThat(titles).extracting(title -> title.getTitle().getKo()).containsExactly("기존 제목 1", "기존 제목 2");
        assertThat(storage).extracting(RawEpisodeContext::getIdentifier).containsExactly("1", "2");
        assertThat(storage).extracting(title -> title.getTitle().getKo()).containsExactly("기존 제목 1", "기존 제목 2");
        assertThat(jdbc.queryForObject("SELECT state FROM novel_toc_translation", String.class)).isEqualTo(state);
    }

    @Test void databaseAccessFailureIsPropagatedRatherThanTreatedAsAnUnavailableOptionalReview() {
        // 준비: DB 연결/권한 오류는 검토 cache miss나 정상 조회로 위장하지 않는다.
        var unavailable = spy(store);
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("database unavailable"))
                .when(unavailable).claim(anyString(), anyString(), anyString(), anyString(), anyString());
        var subject = new NovelTocTranslationService(unavailable, repository, ai, NovelExecutionSettings.baseline(), scheduler);

        // 실행 / 검증
        assertThatThrownBy(() -> subject.resolve(key, 11, work(), titles(2), List.of()))
                .isInstanceOf(org.springframework.dao.DataAccessResourceFailureException.class);
        assertThat(ai.reviews).hasValue(0);
        assertThat(ai.generations).hasValue(0);
    }

    @Test void requestTraceSurvivesSchedulerThreadForGenerationAndReview() {
        // 준비: 새/기존 제목이 섞이면 서로 다른 scheduler 실행에서도 동일 HTTP trace를 이어야 한다.
        var trace = RequestTrace.begin(null).id();
        var titles = titles(2);
        var dirty = titles.getFirst().getTitle();
        dirty.setKo("");
        ai.generated = Map.of("1", "새 제목");

        // 실행
        service.resolve(key, 11, work(), titles, List.of(dirty));

        // 검증
        assertThat(ai.generationTrace).isEqualTo(trace);
        assertThat(ai.reviewTrace).isEqualTo(trace);
    }

    @Test void transientDatabaseSaveFailureReusesTheSamePaidReviewResult() {
        // 준비: provider 성공 뒤 첫 DB 저장만 일시적으로 실패한다.
        var unstable = spy(store);
        doThrow(new org.springframework.dao.CannotAcquireLockException("transient test failure"))
                .doCallRealMethod().when(unstable).complete(any(), anyMap());
        var subject = new NovelTocTranslationService(unstable, repository, ai, NovelExecutionSettings.baseline(), scheduler);
        ai.review = ignored -> Map.of("1", "교정된 제목");
        var titles = titles(2);

        // 실행
        subject.resolve(key, 11, work(), titles, List.of());
        subject.resolve(key, 22, work(), titles(2), List.of());

        // 검증: 저장만 재시도하고 두 번째 계정도 같은 유료 결과를 재사용한다.
        assertThat(ai.reviews).hasValue(1);
        verify(unstable, times(2)).complete(any(), anyMap());
        assertThat(titles.getFirst().getTitle().getKo()).isEqualTo("교정된 제목");
        assertThat(jdbc.queryForObject("SELECT state FROM novel_toc_translation", String.class)).isEqualTo("READY");
    }

    @Test void lostSaveAcknowledgementConfirmsTheAlreadyCommittedResultWithoutNewAi() {
        // 준비: 첫 DB commit은 성공했지만 호출자에게 전달된 성공 응답만 소실된다.
        var unstable = spy(store);
        var saves = new AtomicInteger();
        doAnswer(call -> {
            call.callRealMethod();
            if (saves.incrementAndGet() == 1) throw new org.springframework.dao.DataAccessResourceFailureException("lost acknowledgement");
            return null;
        }).when(unstable).complete(any(), anyMap());
        var subject = new NovelTocTranslationService(unstable, repository, ai, NovelExecutionSettings.baseline(), scheduler);

        // 실행
        subject.resolve(key, 11, work(), titles(2), List.of());

        // 검증: 동일 결과 확인이 멱등하게 성공하고 READY를 FAILED로 바꾸지 않는다.
        assertThat(ai.reviews).hasValue(1);
        assertThat(saves).hasValue(2);
        assertThat(jdbc.queryForObject("SELECT state FROM novel_toc_translation", String.class)).isEqualTo("READY");
        assertThat(jdbc.queryForObject("SELECT failure_code FROM novel_toc_translation", String.class)).isNull();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void queuedGenerationAndReviewDeductWaitingTimeFromTheProviderDeadline(boolean generation) throws Exception {
        // 준비: 실제 catalog 실행 슬롯을 점유해서 목차 요청이 scheduler 대기열에 들어가게 한다.
        var observed = spy(scheduler);
        var queued = new CountDownLatch(1);
        doAnswer(call -> {
            var future = call.callRealMethod();
            if (call.getArgument(0, String.class).startsWith("toc:")) queued.countDown();
            return future;
        }).when(observed).submitCatalog(anyString(), eq(1), anyLong(), any(), any());
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var blocker = observed.submitCatalog("deadline-blocker", 1, System.nanoTime() + TimeUnit.SECONDS.toNanos(10),
                ignored -> {}, () -> {
                    entered.countDown();
                    if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test timeout");
                    return "released";
                });
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        var subject = new NovelTocTranslationService(store, repository, ai, NovelExecutionSettings.baseline(), observed);
        var titles = titles(1);
        List<TranslationUnit> dirty = generation ? List.of(titles.getFirst().getTitle()) : List.of();
        if (generation) {
            titles.getFirst().getTitle().setKo("");
            ai.generated = Map.of("1", "새 제목");
        }

        // 실행: 큐에 들어간 사실을 확인한 뒤 150ms의 제어된 대기를 소비하고 실행을 허용한다.
        try (var requests = Executors.newSingleThreadExecutor()) {
            var response = requests.submit(() -> subject.resolve(key, 11, work(), titles, dirty));
            try {
                assertThat(queued.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(ai.generations.get() + ai.reviews.get()).isZero();
                TimeUnit.MILLISECONDS.sleep(150);
            } finally {
                release.countDown();
            }
            response.get(5, TimeUnit.SECONDS);
            blocker.get(5, TimeUnit.SECONDS);

            // 검증: 시작 시 120초를 다시 주지 않고 큐 대기 시간을 뺀 기한을 AI 호출에 전달한다.
            long providerRemaining = generation ? ai.generationRemaining : ai.reviewRemaining;
            assertThat(providerRemaining).isPositive().isLessThanOrEqualTo(119_900);
            assertThat(ai.generations.get() + ai.reviews.get()).isEqualTo(1);
        } finally {
            release.countDown();
            observed.close();
        }
    }

    private NovelTocTranslationService service(FakeAi backend) {
        return new NovelTocTranslationService(store, repository, backend, NovelExecutionSettings.baseline(), scheduler);
    }

    private NovelContext work() {
        return NovelContext.builder().identifier(key.novel()).title(TranslationUnit.of("帰還", "帰還", "귀환"))
                .synopsis(TranslationUnit.of("真神幸一郎が帰還した。", "原文", "마가미 코이치로가 귀환했다.")).build();
    }

    private List<RawEpisodeContext> titles(int count) {
        var result = new ArrayList<RawEpisodeContext>();
        for (int index = 1; index <= count; index++) {
            result.add(RawEpisodeContext.builder().identifier(Integer.toString(index)).sequence(index)
                    .title(TranslationUnit.of("第" + index + "話", "原文", "기존 제목 " + index)).build());
        }
        return result;
    }

    private static class FakeAi implements NovelPorts.Ai {
        final AtomicInteger reviews = new AtomicInteger();
        final AtomicInteger generations = new AtomicInteger();
        Function<List<NovelPorts.TitleCandidate>, Map<String, String>> review = ignored -> Map.of();
        Map<String, String> generated = Map.of();
        String identity = "model";
        List<NovelPorts.TitleCandidate> lastTitles;
        List<NovelPorts.TitleReference> lastReferences;
        List<SourceEpisode.Segment> lastSegments;
        List<String> lastContext;
        List<Map<String, String>> lastAccepted;
        Map<String, String> lastGlossary;
        String generationTrace;
        String reviewTrace;
        long generationRemaining;
        long reviewRemaining;

        @Override public String model() { return identity; }
        @Override public String speechModel() { return "speech"; }
        @Override public NovelPorts.TitleReview reviewTitles(String trace, String call, List<NovelPorts.TitleCandidate> titles,
                List<NovelPorts.TitleReference> references, Map<String, String> glossary, long remaining, String language) {
            reviews.incrementAndGet();
            reviewTrace = trace;
            reviewRemaining = remaining;
            lastTitles = titles;
            lastReferences = references;
            lastGlossary = glossary;
            return new NovelPorts.TitleReview(review.apply(titles), "openai", identity, 10, 5, Map.of());
        }
        @Override public NovelPorts.Translation repair(String trace, String call, List<SourceEpisode.Segment> items,
                List<String> context, Map<String, String> glossary, List<Map<String, String>> accepted,
                long remaining, TranslationOptions.ResponseShape shape, TranslationOptions.ValidationPolicy validation,
                TranslationOptions.AnnotationPolicy annotation, String language) {
            generations.incrementAndGet();
            generationTrace = trace;
            generationRemaining = remaining;
            lastSegments = items;
            lastContext = context;
            lastAccepted = accepted;
            lastGlossary = glossary;
            return new NovelPorts.Translation(generated, "openai", identity, 10, 5);
        }
        @Override public NovelPorts.Translation translate(String trace, List<SourceEpisode.Segment> items, List<String> context, long remaining) {
            throw new AssertionError("context-free legacy translation must not be used");
        }
        @Override public NovelPorts.Speech synthesize(String id, String text, String language, long remaining) {
            throw new AssertionError("TOC must never generate speech");
        }
    }
}
