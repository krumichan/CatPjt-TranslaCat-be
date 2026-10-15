package jp.co.translacat.domain.languagelearning.webai;

import com.fasterxml.jackson.databind.JsonNode;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.utils.ResponseUtil;
import jp.co.translacat.infrastructure.languagelearning.client.LanguageLearningServiceException;
import jp.co.translacat.infrastructure.languagelearning.client.LanguageLearningWebAiClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/language-learning/web-ai/external")
public class WebAiExternalController {
    private final LanguageLearningWebAiClient client;

    public WebAiExternalController(LanguageLearningWebAiClient client) {
        this.client = client;
    }

    @PostMapping("/prompt")
    public ResponseDto<JsonNode> prompt(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
                                       @RequestBody JsonNode body) {
        return ResponseUtil.ok(client.externalPrompt(capability(auth), body));
    }

    @PostMapping("/results")
    public ResponseDto<JsonNode> result(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
                                       @RequestBody JsonNode body) {
        return ResponseUtil.ok(client.externalResult(capability(auth), body));
    }

    private String capability(String auth) {
        // 계획의 비밀 토큰은 헤더로만 받고 계정 로그인 JWT를 해석하거나 저장하지 않는다.
        if (auth == null || !auth.startsWith("Bearer ")) {
            throw invalidToken();
        }
        String token = auth.substring(7);
        if (!token.matches("[A-Za-z0-9._~-]{32,512}")) {
            throw invalidToken();
        }
        return token;
    }

    private LanguageLearningServiceException invalidToken() {
        return new LanguageLearningServiceException(HttpStatus.UNAUTHORIZED, "WEB_AI_TOKEN_INVALID",
                "Web AI 학습 계획의 연결 토큰을 확인해 주세요.");
    }
}
