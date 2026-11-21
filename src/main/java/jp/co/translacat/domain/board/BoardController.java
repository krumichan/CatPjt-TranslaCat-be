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
@RequestMapping("/api/v1/boards/{board}")
public class BoardController {
    private final BoardService service;
    private final BoardAccess access;
    private final BoardAttachments uploads;

    @ModelAttribute
    void preventCaching(HttpServletResponse response) {
        // 잠금 해제한 원문, 번역문과 파일 URL이 브라우저나 중간 캐시에 남지 않게 한다.
        response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
    }

    /** 게시판의 정렬·분류 조건을 적용한 목록을 반환한다. */
    @GetMapping("/posts")
    public ResponseDto<Page> posts(@AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable Board board,
            @RequestParam Locale locale, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "LATEST") Sort sort, @RequestParam(required = false) UUID category,
            @RequestParam(defaultValue = "false") boolean uncategorized) {
        return ResponseUtil.ok(service.posts(board, access.reader(principal, authorization, board), locale,
                page, sort, category, uncategorized));
    }

    @GetMapping("/posts/{post}")
    public ResponseDto<Entry> post(@AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable Board board,
            @PathVariable UUID post, @RequestParam Locale locale) {
        return ResponseUtil.ok(service.post(board, access.reader(principal, authorization, board), post, locale));
    }

    /** 현재 세션에서 실제로 읽은 게시글의 누적 조회수를 반환한다. */
    @PostMapping("/posts/{post}/views")
    public ResponseDto<Long> recordView(@AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable Board board,
            @PathVariable UUID post) {
        return ResponseUtil.ok(service.recordView(board, access.reader(principal, authorization, board), post));
    }

    /** 이 계정의 투표를 지정한 값으로 저장하고 변경 후의 합계를 반환한다. */
    @PutMapping("/posts/{post}/vote")
    public ResponseDto<Reactions> vote(@AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable Board board,
            @PathVariable UUID post, @Valid @RequestBody Vote request) {
        return ResponseUtil.ok(service.vote(board, access.reader(principal, authorization, board),
                post, request.reaction()));
    }

    /** 읽기 권한이 있는 게시판의 분류를 반환한다. */
    @GetMapping("/categories")
    public ResponseDto<List<Category>> categories(@AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable Board board) {
        access.reader(principal, authorization, board);
        return ResponseUtil.ok(service.categories(board));
    }

    /** 관리자가 현재 게시판에 분류를 추가한다. */
    @PostMapping("/categories")
    public ResponseDto<Category> createCategory(@AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable Board board,
            @Valid @RequestBody CategoryWrite request) {
        return ResponseUtil.created(service.saveCategory(board, access.categoryEditor(principal, authorization, board),
                null, request));
    }

    /** 관리자가 게시글 연결을 유지한 채 분류 이름을 변경한다. */
    @PutMapping("/categories/{category}")
    public ResponseDto<Category> renameCategory(@AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable Board board,
            @PathVariable UUID category, @Valid @RequestBody CategoryWrite request) {
        return ResponseUtil.ok(service.saveCategory(board, access.categoryEditor(principal, authorization, board),
                category, request));
    }

    @GetMapping("/posts/{post}/replies")
    public ResponseDto<Page> replies(@AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable Board board,
            @PathVariable UUID post, @RequestParam(required = false) UUID parent, @RequestParam Locale locale,
            @RequestParam(defaultValue = "0") int page) {
        return ResponseUtil.ok(
                service.replies(board, access.reader(principal, authorization, board), post, parent, locale, page));
    }

    @PostMapping("/posts")
    public ResponseDto<Created> createPost(@AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable Board board,
            @Valid @RequestBody Write request) {
        return ResponseUtil.created(service.createPost(board, access.reader(principal, authorization, board), request));
    }

    @PostMapping("/posts/{post}/replies")
    public ResponseDto<Created> createReply(@AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable Board board,
            @PathVariable UUID post, @RequestParam(required = false) UUID parent, @Valid @RequestBody Write request) {
        return ResponseUtil.created(
                service.createReply(board, access.reader(principal, authorization, board), post, parent, request));
    }

    @PutMapping({"/posts/{post}", "/posts/{post}/replies/{reply}"})
    public ResponseDto<Created> update(@AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable Board board,
            @PathVariable UUID post, @PathVariable(required = false) UUID reply, @Valid @RequestBody Write request) {
        var reader = access.reader(principal, authorization, board);
        return ResponseUtil.ok(service.update(board, reader, post, reply, request));
    }

    @DeleteMapping({"/posts/{post}", "/posts/{post}/replies/{reply}"})
    public ResponseDto<Boolean> delete(@AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable Board board,
            @PathVariable UUID post, @PathVariable(required = false) UUID reply) {
        return ResponseUtil.ok(service.delete(board, access.reader(principal, authorization, board), post, reply));
    }

    @PostMapping({"/posts/{post}/unlock", "/posts/{post}/replies/{reply}/unlock"})
    public ResponseDto<Boolean> unlock(@AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable Board board,
            @PathVariable UUID post, @PathVariable(required = false) UUID reply, @Valid @RequestBody Unlock request) {
        var reader = access.reader(principal, authorization, board);
        return ResponseUtil.ok(service.unlock(board, reader, post, reply, request));
    }

    @PostMapping("/uploads")
    public ResponseDto<UploadStatus> startUpload(@AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable Board board,
            @Valid @RequestBody Upload request) {
        return ResponseUtil.created(uploads.start(board, access.reader(principal, authorization, board).id(), request));
    }

    @GetMapping("/uploads/{id}")
    public ResponseDto<UploadStatus> uploadStatus(@AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable Board board,
            @PathVariable UUID id) {
        return ResponseUtil.ok(uploads.status(board, access.reader(principal, authorization, board).id(), id));
    }

    @PostMapping("/uploads/{id}/parts/{number}")
    public ResponseDto<SignedUrl> uploadPart(@AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable Board board,
            @PathVariable UUID id, @PathVariable int number) {
        return ResponseUtil.ok(uploads.part(board, access.reader(principal, authorization, board).id(), id, number));
    }

    @PostMapping("/uploads/{id}/complete")
    public ResponseDto<Created> completeUpload(@AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable Board board,
            @PathVariable UUID id) {
        return ResponseUtil.ok(uploads.complete(board, access.reader(principal, authorization, board).id(), id));
    }

    @GetMapping("/posts/{post}/attachments/{id}")
    public ResponseDto<SignedUrl> download(@AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable Board board,
            @PathVariable UUID post, @PathVariable UUID id) {
        return ResponseUtil.ok(service.download(board, access.reader(principal, authorization, board), post, id));
    }
}
