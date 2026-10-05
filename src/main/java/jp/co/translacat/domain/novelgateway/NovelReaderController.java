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
@RequestMapping("/api/v1/{platform}/{novel}/episodes/{episode}")
public class NovelReaderController {
    private final NovelReaderFacade facade;

    @GetMapping("/reader")
    public ResponseEntity<ResponseDto<JsonNode>> reader(@PathVariable String platform, @PathVariable String novel,
                                                       @PathVariable String episode) {
        return success(facade.reader(new NovelReaderAddress(platform, novel, episode)));
    }
    @PostMapping("/translations")
    public ResponseEntity<ResponseDto<JsonNode>> start(@PathVariable String platform, @PathVariable String novel,
            @PathVariable String episode, @RequestBody NovelReaderCommandService.TranslationStart request) {
        return success(facade.start(new NovelReaderAddress(platform, novel, episode), request));
    }
    @GetMapping("/translations/{jobId}")
    public ResponseEntity<ResponseDto<JsonNode>> status(@PathVariable String platform, @PathVariable String novel,
            @PathVariable String episode, @PathVariable String jobId) {
        return success(facade.status(new NovelReaderAddress(platform, novel, episode), jobId));
    }
    @PostMapping("/audio")
    public ResponseEntity<ResponseDto<JsonNode>> audio(@PathVariable String platform, @PathVariable String novel,
            @PathVariable String episode, @RequestBody NovelReaderCommandService.AudioRequest request) {
        return success(facade.audio(new NovelReaderAddress(platform, novel, episode), request));
    }

    @PostMapping("/translations/{jobId}/repair")
    public ResponseEntity<ResponseDto<JsonNode>> repair(@PathVariable String platform, @PathVariable String novel,
            @PathVariable String episode, @PathVariable String jobId,
            @RequestBody NovelReaderCommandService.RepairRequest request) {
        return success(facade.repair(new NovelReaderAddress(platform, novel, episode), jobId, request));
    }

    @PostMapping("/translations/{jobId}/cancel")
    public ResponseEntity<ResponseDto<JsonNode>> cancel(@PathVariable String platform, @PathVariable String novel,
            @PathVariable String episode, @PathVariable String jobId) {
        return success(facade.cancel(new NovelReaderAddress(platform, novel, episode), jobId));
    }

    @GetMapping("/glossary")
    public ResponseEntity<ResponseDto<JsonNode>> glossary(@PathVariable String platform, @PathVariable String novel,
            @PathVariable String episode) {
        return success(facade.glossary(new NovelReaderAddress(platform, novel, episode)));
    }

    @PostMapping("/glossary")
    public ResponseEntity<ResponseDto<JsonNode>> glossary(@PathVariable String platform, @PathVariable String novel,
            @PathVariable String episode, @RequestBody NovelReaderCommandService.GlossaryUpdate request) {
        return success(facade.glossary(new NovelReaderAddress(platform, novel, episode), request));
    }

    private ResponseEntity<ResponseDto<JsonNode>> success(JsonNode result) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ResponseUtil.ok(result));
    }
}
