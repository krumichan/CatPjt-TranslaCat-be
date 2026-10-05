package jp.co.translacat.domain.novelgateway;

import com.fasterxml.jackson.databind.JsonNode;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/{platform}/novels/catalog")
public class NovelCatalogController {
    private final NovelCatalogFacade facade;
    @PostMapping
    public ResponseEntity<ResponseDto<JsonNode>> start(@PathVariable String platform, @RequestBody NovelCatalogRequest request) {
        return success(facade.start(platform, request));
    }
    @GetMapping("/{id}")
    public ResponseEntity<ResponseDto<JsonNode>> status(@PathVariable String platform, @PathVariable String id) {
        return success(facade.snapshot(platform, id));
    }
    @PostMapping("/{id}/items/{item}/retry")
    public ResponseEntity<ResponseDto<JsonNode>> retry(@PathVariable String platform, @PathVariable String id,
            @PathVariable String item, @RequestBody NovelCatalogRequest.Retry request) {
        return success(facade.retry(platform, id, item, request));
    }
    @PostMapping("/{id}/cancel")
    public ResponseEntity<ResponseDto<JsonNode>> cancel(@PathVariable String platform, @PathVariable String id) {
        return success(facade.cancel(platform, id));
    }
    private ResponseEntity<ResponseDto<JsonNode>> success(JsonNode value) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ResponseUtil.ok(value));
    }
}
