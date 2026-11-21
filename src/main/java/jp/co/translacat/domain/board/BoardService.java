package jp.co.translacat.domain.board;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jp.co.translacat.domain.user.deletion.UserAccountWriteFence;
import jp.co.translacat.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static jp.co.translacat.domain.board.BoardDtos.*;

@Service
@RequiredArgsConstructor
@Transactional
class BoardService {
    private static final int PAGE_SIZE = 20;
    private static final int MAX_REPLY_DEPTH = 8;

    private final BoardRepository entries;
    private final BoardPostRepository posts;
    private final BoardAccess access;
    private final BoardAttachments attachments;
    private final BoardTranslation translation;
    private final JdbcTemplate jdbc;
    @PersistenceContext private EntityManager entities;

    // 목록에는 열람이 허용된 메타데이터만 포함한다.
    Page posts(Board board, BoardAccess.Reader reader, Locale locale, int page,
            Sort sort, UUID category, boolean uncategorized) {
        if ((board == Board.ADMIN && sort == Sort.POPULAR) || (category != null && uncategorized)) {
            throw BoardException.invalid();
        }
        posts.requireCategory(board, category);

        // 목록의 비밀글에는 제목·작성자·첨부를 포함하지 않는다.
        List<BoardRepository.EntryRow> rows = entries.posts(board, sort, category, uncategorized,
                offset(page), PAGE_SIZE + 1);
        return new Page(
                rows.stream()
                        .limit(PAGE_SIZE)
                        .map(row
                                -> row.passwordHash() == null ? visible(board, reader, row, true, locale) : locked(row))
                        .toList(),
                rows.size() > PAGE_SIZE);
    }

    // 상세 조회는 읽기만 수행하고 실제 열람은 별도 요청에서 집계한다.
    Entry post(Board board, BoardAccess.Reader reader, UUID post, Locale locale) {
        var row = entries.post(board, post, false);
        return access.unlocked(reader, row) ? visible(board, reader, row, false, locale) : locked(row);
    }

    // 본문 열람 권한과 계정 상태를 다시 확인하고 작성자 본인의 조회는 누적하지 않는다.
    long recordView(Board board, BoardAccess.Reader reader, UUID post) {
        var row = entries.post(board, post, true);
        access.requireUnlocked(reader, row);
        if (row.authorId() == reader.id()) {
            return entries.post(board, post, false).viewCount();
        }

        lockAuthor(reader);
        return posts.recordView(post, reader.id());
    }

    // 게시글 수정·삭제와 직렬화하고 현재 로그인 세션에 허용된 게시글에만 투표한다.
    Reactions vote(Board board, BoardAccess.Reader reader, UUID post, Reaction reaction) {
        var row = entries.post(board, post, true);
        access.requireUnlocked(reader, row);
        lockAuthor(reader);
        posts.setVote(post, reader.id(), reaction);
        return posts.reactions(post, reader.id());
    }

    @Transactional(readOnly = true)
    List<Category> categories(Board board) {
        return posts.categories(board);
    }

    // 같은 게시판에서만 분류를 만들거나 이름을 변경한다.
    Category saveCategory(Board board, BoardAccess.Reader reader, UUID category, CategoryWrite request) {
        lockAuthor(reader);
        String name = request.name().strip();
        if (name.isBlank()) {
            throw BoardException.invalid();
        }

        return posts.saveCategory(board, category, name);
    }

    Page replies(Board board, BoardAccess.Reader reader, UUID post, UUID parent, Locale locale, int page) {
        var root = entries.post(board, post, false);
        access.requireUnlocked(reader, root);
        if (parent != null) {
            // 비밀 댓글의 답글은 상위 댓글까지 확인한 사용자에게만 반환한다.
            requireThread(reader, root, entries.reply(post, parent));
        }

        List<BoardRepository.EntryRow> rows =
                entries.replies(parent == null ? post : parent, offset(page), PAGE_SIZE + 1);
        return new Page(rows.stream()
                                .limit(PAGE_SIZE)
                                .map(row
                                        -> access.unlocked(reader, row) ? visible(board, reader, row, false, locale)
                                                                        : locked(row))
                                .toList(),
                rows.size() > PAGE_SIZE);
    }

    // 새 게시글, 선택한 분류, 첨부 참조를 함께 커밋한다.
    Created createPost(Board board, BoardAccess.Reader reader, Write request) {
        lockAuthor(reader);
        String title = requiredTitle(request.title());
        String hash = access.passwordHash(request.password());
        validateCategory(board, request);

        // 첨부 연결을 글 생성과 함께 커밋한다.
        UUID id = entries.insert(board, reader.id(), null, null, title, request.content(), hash);
        if (request.categoryId() != null) {
            posts.assignCategory(id, request.categoryId());
        }
        attachments.attach(board, reader.id(), id, request.attachments());
        return new Created(id);
    }

    Created createReply(Board board, BoardAccess.Reader reader, UUID post, UUID parent, Write request) {
        // 수정과 답글 작성이 겹칠 때 현재 글을 먼저 잠근 뒤 작성 계정을 확인한다.
        var root = entries.post(board, post, true);
        lockAuthor(reader);
        access.requireUnlocked(reader, root);

        // 상위 댓글이 잠긴 경우 답글 작성도 그 댓글의 열람 권한을 요구한다.
        if (parent != null && requireThread(reader, root, entries.reply(post, parent)) >= MAX_REPLY_DEPTH) {
            throw BoardException.invalid();
        }
        requireReplyFields(request);

        String hash = access.passwordHash(request.password());
        UUID id =
                entries.insert(board, reader.id(), post, parent == null ? post : parent, null, request.content(), hash);
        return new Created(id);
    }

    // 권한을 확인한 뒤 본문과 명시적으로 요청한 분류 변경을 저장한다.
    Created update(Board board, BoardAccess.Reader reader, UUID post, UUID reply, Write request) {
        // 수정과 답글 작성이 겹칠 때 현재 글을 먼저 잠근 뒤 작성 계정을 확인한다.
        var root = entries.post(board, post, true);
        lockAuthor(reader);
        var row = reply == null ? root : entries.reply(post, reply);

        // 작성자도 비밀 콘텐츠를 열어야 수정할 수 있다.
        requireOwner(reader, row);
        requireThread(reader, root, row);
        if (reply != null) {
            requireReplyFields(request);
        } else {
            validateCategory(board, request);
        }

        String title = reply == null ? requiredTitle(request.title()) : null;
        String hash = access.passwordHash(request.password());
        entries.update(row, title, request.content(), hash);
        if (reply == null && (request.categoryId() != null || Boolean.TRUE.equals(request.clearCategory()))) {
            posts.assignCategory(row.id(), request.categoryId());
        }
        attachments.attach(board, reader.id(), row.id(), request.attachments());
        return new Created(row.id());
    }

    // 게시글을 지울 때 댓글 계층과 게시글 전용 집계도 함께 지운다.
    boolean delete(Board board, BoardAccess.Reader reader, UUID post, UUID reply) {
        // 수정과 답글 작성이 겹칠 때 현재 글을 먼저 잠근 뒤 작성 계정을 확인한다.
        var root = entries.post(board, post, true);
        lockAuthor(reader);
        var row = reply == null ? root : entries.reply(post, reply);

        // 비밀 댓글을 포함한 하위 스레드는 상위 글·댓글과 함께 제거한다.
        requireOwner(reader, row);
        requireThread(reader, root, row);
        if (reply == null) {
            attachments.remove(post);
            posts.delete(post);
        }
        entries.delete(row);
        return true;
    }

    boolean unlock(Board board, BoardAccess.Reader reader, UUID post, UUID reply, Unlock request) {
        var root = entries.post(board, post, true);
        if (reply == null) {
            access.unlock(reader, root, request.password());
            return true;
        }

        // 답글 자신의 비밀번호를 검사하기 전에 게시글과 부모 댓글을 확인한다.
        access.requireUnlocked(reader, root);
        var row = entries.reply(post, reply);
        if (!post.equals(row.parent())) {
            requireThread(reader, root, entries.reply(post, row.parent()));
        }
        access.unlock(reader, row, request.password());
        return true;
    }

    @Transactional(readOnly = true)
    SignedUrl download(Board board, BoardAccess.Reader reader, UUID post, UUID attachment) {
        var root = entries.post(board, post, false);
        access.requireUnlocked(reader, root);
        return attachments.download(post, attachment);
    }

    @Scheduled(fixedDelayString = "${translacat.board.unlock-cleanup.interval-ms:60000}", initialDelayString = "60000")
    void cleanExpiredUnlocks() {
        jdbc.update("DELETE FROM board_unlock WHERE expires_at <= UTC_TIMESTAMP(6) LIMIT 1000");
    }

    // 누적 조회수는 유지하고 개인별 열람 시각만 만료한다.
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    void cleanExpiredViews() {
        posts.deleteExpiredViews();
    }

    private int requireThread(BoardAccess.Reader reader, BoardRepository.EntryRow post, BoardRepository.EntryRow row) {
        access.requireUnlocked(reader, post);
        int depth = 0;

        // 부모 체인 전체를 순회해 숨겨진 상위 댓글을 건너뛰는 요청을 거절한다.
        while (!row.id().equals(post.id())) {
            if (++depth > MAX_REPLY_DEPTH || row.parent() == null) {
                throw BoardException.missing();
            }
            access.requireUnlocked(reader, row);
            if (row.parent().equals(post.id())) {
                break;
            }
            row = entries.reply(post.id(), row.parent());
        }

        return depth;
    }

    // 열람 가능한 글에만 분류와 집계를 추가하고 내 투표는 글 상세에서 반환한다.
    private Entry visible(
            Board board, BoardAccess.Reader reader, BoardRepository.EntryRow row, boolean summary, Locale locale) {
        // 익명 작성자의 식별자는 API 응답에 포함하지 않는다.
        String author = board == Board.ANONYMOUS ? null : row.author();
        var translated =
                locale == null ? new BoardTranslation.Result(null, null, false) : translation.read(row, locale);
        boolean post = row.parent() == null;

        return new Entry(row.id(), false, row.passwordHash() != null, row.authorId() == reader.id(), author,
                row.createdAt(), row.updatedAt(), row.title(), summary ? null : row.content(), translated.title(),
                summary ? null : translated.content(), translated.translating(),
                !summary && post ? attachments.list(row.id()) : List.of(), row.category(),
                post ? row.viewCount() : null, !summary && post ? posts.reactions(row.id(), reader.id()) : null);
    }

    // 잠긴 목록과 상세는 본문과 분류·집계를 내보내지 않는다.
    private static Entry locked(BoardRepository.EntryRow row) {
        return new Entry(row.id(), true, true, false, null, row.createdAt(), row.updatedAt(), null, null, null, null,
                false, List.of(), null, null, null);
    }

    private void lockAuthor(BoardAccess.Reader reader) {
        UserAccountWriteFence.lock(entities, entities.find(User.class, reader.id()));
    }

    private static void requireOwner(BoardAccess.Reader reader, BoardRepository.EntryRow row) {
        if (row.authorId() != reader.id()) {
            throw BoardException.missing();
        }
    }

    private static String requiredTitle(String title) {
        if (title == null || title.isBlank()) {
            throw BoardException.invalid();
        }
        return title.strip();
    }

    // 제목, 첨부, 분류는 게시글에만 허용한다.
    private static void requireReplyFields(Write request) {
        if (request.title() != null || (request.attachments() != null && !request.attachments().isEmpty())
                || request.categoryId() != null || Boolean.TRUE.equals(request.clearCategory())) {
            throw BoardException.invalid();
        }
    }

    // 기존 클라이언트는 분류 필드를 생략할 수 있고, 명시적인 해제 요청과 지정은 함께 올 수 없다.
    private void validateCategory(Board board, Write request) {
        if (request.categoryId() != null && Boolean.TRUE.equals(request.clearCategory())) {
            throw BoardException.invalid();
        }
        posts.requireCategory(board, request.categoryId());
    }

    private static int offset(int page) {
        if (page < 0 || page > Integer.MAX_VALUE / PAGE_SIZE) {
            throw BoardException.invalid();
        }
        return page * PAGE_SIZE;
    }
}
