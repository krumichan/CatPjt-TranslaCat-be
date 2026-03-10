package jp.co.translacat.global.controller;

import io.swagger.v3.oas.annotations.Operation;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.utils.ResponseUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
public class HealthCheckController {

    @GetMapping
    @Operation(summary = "서버 생존 상태 확인", description = "서버가 정상적으로 생존해있는지 확인한다.")
    public ResponseDto<String> health() {
        return ResponseUtil.ok("OK");
    }
}
