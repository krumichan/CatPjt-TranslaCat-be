package jp.co.translacat.domain.board;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static jp.co.translacat.domain.board.BoardDtos.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/boards")
public class BoardController {
    private final BoardService service;
    private final BoardAccess access;
    private final BoardAttachments uploads;
    private final BoardForumRepository forums;
    private final BoardRepository entries;

    @ModelAttribute
    void preventCaching(HttpServletResponse response) {
        // 잠금 해제한 원문, 번역문과 파일 URL이 브라우저나 중간 캐시에 남지 않게 한다.
        response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
    }

    /** 로그인한 회원에게 열람할 수 있는 게시판만 반환한다. */
    @GetMapping
    public ResponseDto<List<Forum>> directory(@AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        access.reader(principal, authorization, Board.MEMBER);
        return ResponseUtil.ok(forums.list(access.isAdmin(principal)));
    }

    /** 관리자가 이름과 이용 유형을 지정해 독립 게시판을 생성한다. */
    @PostMapping
    public ResponseDto<Forum> createForum(@AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @Valid @RequestBody ForumWrite request) {
        var administrator = access.reader(principal, authorization, Board.ADMIN);
        return ResponseUtil.created(service.createForum(administrator, request));
    }

    /** 이전 게시판 주소로 요청한 글의 현재 게시판 ID만 반환한다. */
    @GetMapping("/{type}/posts/{post}/location")
    public ResponseDto<ForumLocation> legacyLocation(@AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable Board type,
            @PathVariable UUID post) {
        access.reader(principal, authorization, type);
        return ResponseUtil.ok(new ForumLocation(entries.forumForPost(type, post)));
    }

    /** 선택한 게시판의 최신글 또는 인기글만 반환한다. */
    @GetMapping("/{board}/posts")
    public ResponseDto<Page> posts(@AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable String board,
            @RequestParam Locale locale, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "LATEST") Sort sort) {
        var target = forumAccess(principal, authorization, board);
        return ResponseUtil.ok(service.posts(target.forum(), target.reader(), locale, page, sort));
    }

    /** 현재 게시판 소속 글의 상세를 반환한다. 잠긴 글은 메타데이터만 반환한다. */
    @GetMapping("/{board}/posts/{post}")
    public ResponseDto<Entry> post(@AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable String board,
            @PathVariable UUID post, @RequestParam Locale locale) {
        var target = forumAccess(principal, authorization, board);
        return ResponseUtil.ok(service.post(target.forum(), target.reader(), post, locale));
    }

    /** 현재 세션에서 실제로 읽은 게시글의 누적 조회수를 반환한다. */
    @PostMapping("/{board}/posts/{post}/views")
    public ResponseDto<Long> recordView(@AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable String board,
            @PathVariable UUID post) {
        var target = forumAccess(principal, authorization, board);
        return ResponseUtil.ok(service.recordView(target.forum(), target.reader(), post));
    }

    /** 이 계정의 투표를 지정한 값으로 저장하고 변경 후의 합계를 반환한다. */
    @PutMapping("/{board}/posts/{post}/vote")
    public ResponseDto<Reactions> vote(@AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable String board,
            @PathVariable UUID post, @Valid @RequestBody Vote request) {
        var target = forumAccess(principal, authorization, board);
        return ResponseUtil.ok(service.vote(target.forum(), target.reader(),
                post, request.reaction()));
    }

    /** 게시판과 상위 글의 열람 권한을 확인한 뒤 댓글 목록을 반환한다. */
    @GetMapping("/{board}/posts/{post}/replies")
    public ResponseDto<Page> replies(@AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable String board,
            @PathVariable UUID post, @RequestParam(required = false) UUID parent, @RequestParam Locale locale,
            @RequestParam(defaultValue = "0") int page) {
        var target = forumAccess(principal, authorization, board);
        return ResponseUtil.ok(
                service.replies(target.forum(), target.reader(), post, parent, locale, page));
    }

    /** 선택한 게시판에 새 글과 검증된 첨부를 저장한다. */
    @PostMapping("/{board}/posts")
    public ResponseDto<Created> createPost(@AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable String board,
            @Valid @RequestBody Write request) {
        var target = forumAccess(principal, authorization, board);
        return ResponseUtil.created(service.createPost(target.forum(), target.reader(), request));
    }

    /** 게시글 또는 열람 가능한 부모 댓글에 답글을 작성한다. */
    @PostMapping("/{board}/posts/{post}/replies")
    public ResponseDto<Created> createReply(@AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable String board,
            @PathVariable UUID post, @RequestParam(required = false) UUID parent, @Valid @RequestBody Write request) {
        var target = forumAccess(principal, authorization, board);
        return ResponseUtil.created(
                service.createReply(target.forum(), target.reader(), post, parent, request));
    }

    /** 현재 게시판의 글이나 댓글을 작성자가 수정한다. */
    @PutMapping({"/{board}/posts/{post}", "/{board}/posts/{post}/replies/{reply}"})
    public ResponseDto<Created> update(@AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable String board,
            @PathVariable UUID post, @PathVariable(required = false) UUID reply, @Valid @RequestBody Write request) {
        var target = forumAccess(principal, authorization, board);
        return ResponseUtil.ok(service.update(target.forum(), target.reader(), post, reply, request));
    }

    /** 현재 게시판의 글이나 댓글을 작성자가 삭제한다. */
    @DeleteMapping({"/{board}/posts/{post}", "/{board}/posts/{post}/replies/{reply}"})
    public ResponseDto<Boolean> delete(@AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable String board,
            @PathVariable UUID post, @PathVariable(required = false) UUID reply) {
        var target = forumAccess(principal, authorization, board);
        return ResponseUtil.ok(service.delete(target.forum(), target.reader(), post, reply));
    }

    /** 게시판 소속과 부모 항목의 잠금을 확인한 후 비밀글을 연다. */
    @PostMapping({"/{board}/posts/{post}/unlock", "/{board}/posts/{post}/replies/{reply}/unlock"})
    public ResponseDto<Boolean> unlock(@AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable String board,
            @PathVariable UUID post, @PathVariable(required = false) UUID reply, @Valid @RequestBody Unlock request) {
        var target = forumAccess(principal, authorization, board);
        return ResponseUtil.ok(service.unlock(target.forum(), target.reader(), post, reply, request));
    }

    /** 선택한 게시판의 유형 한도 내에서 작성자 전용 업로드를 시작한다. */
    @PostMapping("/{board}/uploads")
    public ResponseDto<UploadStatus> startUpload(@AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable String board,
            @Valid @RequestBody Upload request) {
        var target = forumAccess(principal, authorization, board);
        return ResponseUtil.created(uploads.start(target.forum(), target.reader().id(), request));
    }

    /** 해당 게시판에서 시작한 업로드의 복구 상태를 반환한다. */
    @GetMapping("/{board}/uploads/{id}")
    public ResponseDto<UploadStatus> uploadStatus(@AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable String board,
            @PathVariable UUID id) {
        var target = forumAccess(principal, authorization, board);
        return ResponseUtil.ok(uploads.status(target.forum(), target.reader().id(), id));
    }

    /** 해당 게시판의 업로드에 한해 파일 조각의 서명 URL을 반환한다. */
    @PostMapping("/{board}/uploads/{id}/parts/{number}")
    public ResponseDto<SignedUrl> uploadPart(@AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable String board,
            @PathVariable UUID id, @PathVariable int number) {
        var target = forumAccess(principal, authorization, board);
        return ResponseUtil.ok(uploads.part(target.forum(), target.reader().id(), id, number));
    }

    /** 해당 게시판 업로드의 실제 파일 크기를 검증하고 완료한다. */
    @PostMapping("/{board}/uploads/{id}/complete")
    public ResponseDto<Created> completeUpload(@AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable String board,
            @PathVariable UUID id) {
        var target = forumAccess(principal, authorization, board);
        return ResponseUtil.ok(uploads.complete(target.forum(), target.reader().id(), id));
    }

    /** 해당 게시판의 글을 열 수 있는 사용자에게 첨부 URL을 반환한다. */
    @GetMapping("/{board}/posts/{post}/attachments/{id}")
    public ResponseDto<SignedUrl> download(@AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable String board,
            @PathVariable UUID post, @PathVariable UUID id) {
        var target = forumAccess(principal, authorization, board);
        return ResponseUtil.ok(service.download(target.forum(), target.reader(), post, id));
    }

    // URL의 게시판을 조회하고 게시판 자체의 유형으로 현재 로그인 권한을 검증한다.
    private ForumAccess forumAccess(UserPrincipal principal, String authorization, String board) {
        Forum forum = forums.find(board);
        return new ForumAccess(forum, access.reader(principal, authorization, forum.type()));
    }

    private record ForumAccess(Forum forum, BoardAccess.Reader reader) {}
}
