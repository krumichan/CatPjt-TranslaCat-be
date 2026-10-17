package jp.co.translacat.domain.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.common.enums.PlatformCode;
import jp.co.translacat.domain.novel.platform.entity.Platform;
import jp.co.translacat.domain.novel.platform.service.PlatformService;
import jp.co.translacat.domain.user.dto.RecentViewResponseDto;
import jp.co.translacat.domain.user.dto.RecentViewSaveRequestDto;
import jp.co.translacat.domain.user.entity.RecentView;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.RecentViewType;
import jp.co.translacat.domain.user.repository.RecentViewRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecentViewLanguageTest {
    @Mock RecentViewRepository repository;
    @Mock UserService users;
    @Mock PlatformService platforms;
    private RecentViewService service;
    private User actor;
    private Platform platform;

    @BeforeEach
    void prepare() {
        // 준비: 요청 body의 사용자가 아닌 현재 인증 사용자의 최근 기록만 갱신한다.
        service = new RecentViewService(repository, users, platforms);
        actor = new User();
        actor.setId(1L);
        platform = mock(Platform.class);
        var principal = org.springframework.security.core.userdetails.User.withUsername("recent@example.test")
                .password("unused").roles("USER").build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void clearAuthentication() { SecurityContextHolder.clearContext(); }

    private void configureSave() {
        when(users.findByEmail("recent@example.test")).thenReturn(actor);
        when(platforms.getPlatformByCode(PlatformCode.SYOSETU)).thenReturn(platform);
        when(platform.getId()).thenReturn(7L);
    }

    private RecentView existing() {
        var view = RecentView.create(actor, platform, RecentViewType.NOVEL, "n123aa", null,
                "原題", "原題の読み", "한국어 제목");
        view.setId(11L);
        view.setViewedAt(LocalDateTime.parse("2020-01-01T00:00:00"));
        return view;
    }

    @Test
    void englishVisitUpdatesTheSameOwnedBookAndCannotOverwriteItsKoreanTitle() throws Exception {
        // 준비
        configureSave();
        var view = existing();
        when(repository.findByUserIdAndPlatformIdAndRecentViewTypeAndNovelIdAndEpisodeIdIsNull(
                1L, 7L, RecentViewType.NOVEL, "n123aa")).thenReturn(Optional.of(view));
        var request = request("\"targetLanguage\":\"en\",\"titleEn\":\"An English title\",\"titleKo\":\"English poison\"");

        // 실행
        assertThat(service.save(request)).isTrue();

        // 검증: 별도 언어 행을 만들지 않으며 원제·한국어·소유권·식별자는 그대로 보존한다.
        assertThat(view.getId()).isEqualTo(11L);
        assertThat(view.getUser()).isSameAs(actor);
        assertThat(view.getTitle()).isEqualTo("原題");
        assertThat(view.getTitleKo()).isEqualTo("한국어 제목");
        assertThat(view.getTitleEn()).isEqualTo("An English title");
        assertThat(view.getTargetLanguage()).isEqualTo("en");
        assertThat(view.getViewedAt()).isAfter(LocalDateTime.parse("2020-01-01T00:00:00"));
        verify(repository, never()).save(any());
    }

    @Test
    void legacyKoreanRequestKeepsTheEnglishTitleAndRestoresLastVisitLanguage() throws Exception {
        // 준비: 영어 방문 뒤 기존 형식의 한국어 요청을 보낸다.
        configureSave();
        var view = existing();
        view.update("原題", "原題の読み", null, "An English title", "en");
        when(repository.findByUserIdAndPlatformIdAndRecentViewTypeAndNovelIdAndEpisodeIdIsNull(
                1L, 7L, RecentViewType.NOVEL, "n123aa")).thenReturn(Optional.of(view));
        var request = request("\"titleKo\":\"새 한국어 제목\"");

        // 실행
        service.save(request);

        // 검증
        assertThat(request.getTargetLanguage()).isEqualTo("ko");
        assertThat(view.getTargetLanguage()).isEqualTo("ko");
        assertThat(view.getTitleKo()).isEqualTo("새 한국어 제목");
        assertThat(view.getTitleEn()).isEqualTo("An English title");
        assertThat(view.getTitle()).isEqualTo("原題");
        verify(repository, never()).save(any());
    }

    @Test
    void firstEnglishVisitWithoutTranslationStoresRawTitleAndNeverRelabelsKorean() throws Exception {
        // 준비
        configureSave();
        when(repository.findByUserIdAndPlatformIdAndRecentViewTypeAndNovelIdAndEpisodeIdIsNull(
                1L, 7L, RecentViewType.NOVEL, "n123aa")).thenReturn(Optional.empty());

        // 실행
        service.save(request("\"targetLanguage\":\"en\",\"titleKo\":\"must not become English\""));

        // 검증: 영어 자료가 없다는 사실을 유지해 FE가 원제 표시와 영어 재진입을 선택한다.
        var saved = ArgumentCaptor.forClass(RecentView.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getUser()).isSameAs(actor);
        assertThat(saved.getValue().getTargetLanguage()).isEqualTo("en");
        assertThat(saved.getValue().getTitle()).isEqualTo("原題");
        assertThat(saved.getValue().getTitleKo()).isNull();
        assertThat(saved.getValue().getTitleEn()).isNull();
        when(platform.getCode()).thenReturn(PlatformCode.SYOSETU);
        var response = RecentViewResponseDto.of(saved.getValue());
        assertThat(response.getTargetLanguage()).isEqualTo("en");
        assertThat(response.getTitle().getRawJa()).isEqualTo("原題");
        assertThat(response.getTitleEn()).isNull();
        assertThat(response.getTitle().getKo()).isNull();
    }

    @Test
    void rawReaderVisitDoesNotEraseAnExistingEnglishTitleAndKoreanCannotOverwriteIt() {
        // 준비
        var view = existing();
        view.update("原題", "読み", "ignored", "An English title", "en");

        // 실행: 원문만 아는 영어 reader 방문과 이후 한국어 방문을 순서대로 반영한다.
        view.update(null, null, "ignored", null, "en");
        assertThat(view.getTitle()).isEqualTo("原題");
        assertThat(view.getTitleJa()).isEqualTo("読み");
        view.update("原題", "読み", "새 한국어 제목", "English poison", "ko");
        view.update("原題", "読み", null, null, "ko");
        view.update("原題", "読み", "  ", null, "ko");

        // 검증
        assertThat(view.getTitleEn()).isEqualTo("An English title");
        assertThat(view.getTitleKo()).isEqualTo("새 한국어 제목");
        assertThat(view.getTargetLanguage()).isEqualTo("ko");
    }

    @Test
    void invalidTargetCannotChangeExistingTitlesOrVisitTime() {
        // 준비
        var view = existing();
        var viewedAt = view.getViewedAt();

        // 실행 / 검증
        assertThatThrownBy(() -> view.update("changed", "changed", "changed", "changed", "ja"))
                .hasMessage("TRANSLATION_TARGET_LANGUAGE_INVALID");
        assertThat(view.getTitle()).isEqualTo("原題");
        assertThat(view.getTitleKo()).isEqualTo("한국어 제목");
        assertThat(view.getViewedAt()).isEqualTo(viewedAt);
    }

    @Test
    void aDifferentUsersRecentViewCannotBeDeleted() {
        // 준비
        when(users.findByEmail("recent@example.test")).thenReturn(actor);
        User owner = new User();
        owner.setId(2L);
        var view = existing();
        view.setUser(owner);
        when(repository.findById(11L)).thenReturn(Optional.of(view));

        // 실행 / 검증: 언어 추가와 무관하게 원래 소유권 경계를 유지한다.
        assertThatThrownBy(() -> service.delete(11L)).isInstanceOf(AccessDeniedException.class);
        verify(repository, never()).deleteById(any());
    }

    private RecentViewSaveRequestDto request(String optional) throws Exception {
        return new ObjectMapper().readValue("{\"platformCode\":\"SYOSETU\",\"type\":\"NOVEL\","
                + "\"novelId\":\"n123aa\",\"title\":\"原題\",\"titleJa\":\"原題の読み\","
                + optional + "}", RecentViewSaveRequestDto.class);
    }
}
