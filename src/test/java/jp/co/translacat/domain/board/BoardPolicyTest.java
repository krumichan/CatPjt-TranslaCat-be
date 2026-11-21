package jp.co.translacat.domain.board;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static jp.co.translacat.domain.board.BoardDtos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class BoardPolicyTest {
    private final BoardRepository repository = mock(BoardRepository.class);
    private final BoardPostRepository postRepository = mock(BoardPostRepository.class);
    private final BoardAccess access = mock(BoardAccess.class);
    private final BoardAttachments attachments = mock(BoardAttachments.class);
    private final BoardTranslation translation = mock(BoardTranslation.class);
    private final BoardService service =
            new BoardService(repository, postRepository, access, attachments, translation, mock(JdbcTemplate.class));

    @Test
    void memberAndAnonymousFileLimitsAreDecimalMb() {
        // 실행 및 검증
        for (Board board : List.of(Board.MEMBER, Board.ANONYMOUS)) {
            assertThat(BoardAttachments.partBytes(board, 30_000_000)).isPositive();
            assertThatThrownBy(() -> BoardAttachments.partBytes(board, 30_000_001))
                    .isInstanceOf(BoardException.class)
                    .hasMessage("BOARD_INVALID_INPUT");
        }
    }

    @Test
    void partitioningSupportsTheR2MaximumInAtMostTenThousandParts() {
        // 준비
        long bytes = (5L << 40) - (5L << 30);

        // 실행
        long perPart = BoardAttachments.partBytes(Board.ADMIN, bytes);
        long count = (bytes + perPart - 1) / perPart;

        // 검증
        assertThat(count).isLessThanOrEqualTo(10_000);
        assertThat(perPart).isBetween(5L << 20, (5L << 30) - (5L << 20));
        assertThatThrownBy(() -> BoardAttachments.partBytes(Board.ADMIN, bytes + 1)).isInstanceOf(BoardException.class);
    }

    @Test
    void lockedPostNeverReturnsRawContentOrStartsTranslationOrDownload() {
        // 준비
        var reader = new BoardAccess.Reader(7, "login");
        var row = row(null, null, "hash");
        when(repository.post(Board.ADMIN, row.id(), false)).thenReturn(row);
        doThrow(BoardException.locked()).when(access).requireUnlocked(reader, row);

        // 실행
        Entry result = service.post(Board.ADMIN, reader, row.id(), Locale.en);

        // 검증
        assertThat(result.locked()).isTrue();
        assertThat(result.title()).isNull();
        assertThat(result.content()).isNull();
        assertThat(result.author()).isNull();
        assertThat(result.viewCount()).isNull();
        assertThat(result.category()).isNull();
        assertThat(result.reactions()).isNull();
        assertThat(result.attachments()).isEmpty();
        assertThatThrownBy(() -> service.download(Board.ADMIN, reader, row.id(), UUID.randomUUID()))
                .hasMessage("BOARD_LOCKED");
        verifyNoInteractions(translation, attachments);
    }

    @Test
    void lockedPostRejectsViewsAndVotesBeforeWriting() {
        // 준비: 비밀번호를 입력하지 않은 세션이 게시글 조회와 투표를 시도한다.
        var reader = new BoardAccess.Reader(7, "login");
        var post = row(null, null, "locked");
        when(repository.post(Board.MEMBER, post.id(), true)).thenReturn(post);
        doThrow(BoardException.locked()).when(access).requireUnlocked(reader, post);

        // 실행과 검증: 두 요청은 같은 열람 경계에서 거절되고 집계 저장소를 호출하지 않는다.
        assertThatThrownBy(() -> service.recordView(Board.MEMBER, reader, post.id()))
                .hasMessage("BOARD_LOCKED");
        assertThatThrownBy(() -> service.vote(Board.MEMBER, reader, post.id(), Reaction.LIKE))
                .hasMessage("BOARD_LOCKED");
        verifyNoInteractions(postRepository);
    }

    @Test
    void authorViewsDoNotIncreaseTheCount() {
        // 준비: 자신의 게시글을 열람한다.
        var reader = new BoardAccess.Reader(7, "login");
        var post = row(null, null, null);
        when(repository.post(Board.MEMBER, post.id(), true)).thenReturn(post);
        when(repository.post(Board.MEMBER, post.id(), false)).thenReturn(post);

        // 실행
        long views = service.recordView(Board.MEMBER, reader, post.id());

        // 검증: 현재 조회수를 그대로 반환한다.
        assertThat(views).isEqualTo(post.viewCount());
        verifyNoInteractions(postRepository);
    }

    @Test
    void adminBoardRejectsPopularOrder() {
        var reader = new BoardAccess.Reader(7, "login");

        assertThatThrownBy(() -> service.posts(Board.ADMIN, reader, Locale.ko, 0, Sort.POPULAR, null, false))
                .hasMessage("BOARD_INVALID_INPUT");
        verifyNoInteractions(postRepository, repository);
    }

    @Test
    void lockedParentPreventsDirectReplyUnlock() {
        // 준비
        var reader = new BoardAccess.Reader(7, "login");
        var post = row(null, null, null);
        var parent = row(post.id(), post.id(), "hash-a");
        var reply = row(post.id(), parent.id(), "hash-b");
        when(repository.post(Board.ANONYMOUS, post.id(), true)).thenReturn(post);
        when(repository.reply(post.id(), reply.id())).thenReturn(reply);
        when(repository.reply(post.id(), parent.id())).thenReturn(parent);
        doThrow(BoardException.locked()).when(access).requireUnlocked(reader, parent);

        // 실행 및 검증
        assertThatThrownBy(
                () -> service.unlock(Board.ANONYMOUS, reader, post.id(), reply.id(), new Unlock("personal password")))
                .hasMessage("BOARD_LOCKED");
        verify(access, never()).unlock(any(), any(), any());
    }

    @Test
    void anonymousContentDoesNotReturnTheAuthorsName() {
        // 준비
        var reader = new BoardAccess.Reader(22, "login");
        var row = row(null, null, null);
        when(repository.post(Board.ANONYMOUS, row.id(), false)).thenReturn(row);
        when(access.unlocked(reader, row)).thenReturn(true);
        when(translation.read(row, Locale.ja)).thenReturn(new BoardTranslation.Result(null, null, true));

        // 실행
        Entry result = service.post(Board.ANONYMOUS, reader, row.id(), Locale.ja);

        // 검증
        assertThat(result.author()).isNull();
        assertThat(result.mine()).isFalse();
        assertThat(result.content()).isEqualTo("sensitive content");
        assertThat(result.translating()).isTrue();
    }

    // 잠금 여부와 댓글 계층을 지정할 수 있는 게시글 데이터를 만든다.
    private static BoardRepository.EntryRow row(UUID root, UUID parent, String secret) {
        return new BoardRepository.EntryRow(UUID.randomUUID(), root, parent, 7, "author", "private title",
                "sensitive content", secret, 1, Instant.EPOCH, Instant.EPOCH, null, 4);
    }
}
