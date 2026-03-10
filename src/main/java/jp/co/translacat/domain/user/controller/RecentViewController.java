package jp.co.translacat.domain.user.controller;

import jakarta.validation.Valid;
import jp.co.translacat.domain.user.dto.RecentViewResponseDto;
import jp.co.translacat.domain.user.dto.RecentViewSaveRequestDto;
import jp.co.translacat.domain.user.entity.RecentView;
import jp.co.translacat.domain.user.enums.RecentViewType;
import jp.co.translacat.domain.user.service.RecentViewService;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/recent")
public class RecentViewController {
    private final RecentViewService recentViewService;

    @GetMapping("/top10")
    public ResponseDto<List<RecentViewResponseDto>> top10() {
        List<RecentViewResponseDto> response = Stream.concat(
            recentViewService.findTop5By(RecentViewType.NOVEL).stream(),
            recentViewService.findTop5By(RecentViewType.EPISODE).stream()
        )
        .sorted(Comparator.comparing(RecentView::getViewedAt).reversed())
        .map(RecentViewResponseDto::of)
        .toList();
        return ResponseUtil.ok(response);
    }

    @DeleteMapping("/{recentViewId}")
    public ResponseDto<Boolean> delete(@PathVariable Long recentViewId) {
        return ResponseUtil.ok(recentViewService.delete(recentViewId));
    }

    @PostMapping("/save")
    public ResponseDto<Boolean> save(@RequestBody @Valid RecentViewSaveRequestDto requestDto) {
        return ResponseUtil.ok(this.recentViewService.save(requestDto));
    }
}
