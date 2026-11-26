package jp.co.translacat.domain.wordbook;

import com.fasterxml.jackson.databind.JsonNode;
import jp.co.translacat.domain.wordbook.WordbookTypes.CreateResult;
import jp.co.translacat.domain.wordbook.WordbookTypes.Draft;
import jp.co.translacat.domain.wordbook.WordbookTypes.Entry;
import jp.co.translacat.domain.wordbook.WordbookTypes.Page;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/** 인증 계정의 개인 단어장 외부 계약을 제공한다. */
@RestController
@RequestMapping(path = "/api/v1/wordbook/entries", produces = MediaType.APPLICATION_JSON_VALUE)
class WordbookController {
    private static final Set<String> DRAFT_FIELDS =
            Set.of("sourceLanguage", "targetLanguage", "sourceText", "targetText", "context", "sourceLabel");
    private final WordbookService service;

    /** 단어장 애플리케이션 계약을 HTTP 요청에 연결한다. */
    WordbookController(WordbookService service) {
        this.service = service;
    }

    /** 저장을 확정하거나 현재의 기존 표현과 버전을 반환한다. */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ResponseDto<CreateResult>> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody JsonNode request) {
        // 값의 종류와 UUID 형식을 확인한 뒤 인증 계정의 저장 경계에 전달한다.
        long ownerId = owner(principal);
        Draft draft = draft(request, true);
        var kind = WordbookInput.sourceKind(text(request, "sourceKind"));
        var result = service.create(ownerId, WordbookInput.uuid(key), draft, kind);

        HttpStatus status = result.duplicate() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .eTag(etag(result.entry()))
                .body(ResponseUtil.ok(status, result));
    }

    /** 언어 방향 및 페이지 위치로 인증 계정의 항목만 반환한다. */
    @GetMapping
    ResponseEntity<ResponseDto<Page>> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(value = "sourceLanguage", required = false) String sourceLanguage,
            @RequestParam(value = "targetLanguage", required = false) String targetLanguage,
            @RequestParam(value = "limit", required = false) String limit,
            @RequestParam(value = "cursor", required = false) String cursor) {
        // 언어 필터는 한 방향 전체가 있어야 한다. 커서의 계정·필터 범위는 서비스에서 대조한다.
        var languages = WordbookInput.languages(sourceLanguage, targetLanguage, true);
        Page page = service.list(owner(principal), languages, WordbookInput.limit(limit), cursor);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ResponseUtil.ok(page));
    }

    /** 단건 읽기에 저장된 버전의 강한 ETag를 함께 반환한다. */
    @GetMapping("/{entryId}")
    ResponseEntity<ResponseDto<Entry>> get(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("entryId") String entryId) {
        Entry entry = service.get(owner(principal), WordbookInput.uuid(entryId));
        return entryResponse(entry);
    }

    /** 현재 ETag를 확인해 수정 가능 필드 전체를 교체한다. */
    @PutMapping(path = "/{entryId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ResponseDto<Entry>> replace(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("entryId") String entryId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String version,
            @RequestBody JsonNode request) {
        Entry entry = service.replace(
                owner(principal), WordbookInput.uuid(entryId), WordbookInput.version(version), draft(request, false));
        return entryResponse(entry);
    }

    /** 현재 ETag와 일치하는 자신의 항목을 제거한 뒤 빈 본문을 반환한다. */
    @DeleteMapping("/{entryId}")
    ResponseEntity<Void> delete(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("entryId") String entryId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String version) {
        service.delete(owner(principal), WordbookInput.uuid(entryId), WordbookInput.version(version));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    // 전체 교체에서 null 및 생략을 구분하고, 소유자·최초 출처 등을 본문에서 받지 않는다.
    private static Draft draft(JsonNode request, boolean create) {
        if (request == null || !request.isObject()) {
            throw WordbookProblem.invalid();
        }
        var names = request.fieldNames();
        while (names.hasNext()) {
            String field = names.next();
            if (!DRAFT_FIELDS.contains(field) && !(create && "sourceKind".equals(field))) {
                throw WordbookProblem.invalid();
            }
        }
        if (!create && (!request.has("context") || !request.has("sourceLabel"))) {
            throw WordbookProblem.invalid();
        }

        return WordbookInput.draft(
                text(request, "sourceLanguage"),
                text(request, "targetLanguage"),
                text(request, "sourceText"),
                text(request, "targetText"),
                text(request, "context"),
                text(request, "sourceLabel"));
    }

    // JSON 숫자·배열·객체의 문자열 강제 변환을 허용하지 않는다.
    private static String text(JsonNode request, String field) {
        JsonNode value = request.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw WordbookProblem.invalid();
        }
        return value.textValue();
    }

    // 사용자 ID는 클라이언트 입력 대신 인증된 세션에서 가져온다.
    private static long owner(UserPrincipal principal) {
        if (principal == null || principal.getId() == null) {
            throw new WordbookProblem(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED");
        }
        return principal.getId();
    }

    private static String etag(Entry entry) {
        return "\"" + entry.version() + "\"";
    }

    // 단건 조회와 수정을 위해 같은 응답 헤더와 공통 형태를 사용한다.
    private static ResponseEntity<ResponseDto<Entry>> entryResponse(Entry entry) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .eTag(etag(entry))
                .body(ResponseUtil.ok(entry));
    }
}
