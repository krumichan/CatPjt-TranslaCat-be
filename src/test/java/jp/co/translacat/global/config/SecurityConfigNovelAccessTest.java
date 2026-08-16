package jp.co.translacat.global.config;

import jp.co.translacat.domain.novel.platform.controller.PlatformController;
import jp.co.translacat.domain.novel.platform.dto.PlatformResponseDto;
import jp.co.translacat.domain.novel.platform.service.PlatformService;
import jp.co.translacat.global.logging.ApiLoggingFilter;
import jp.co.translacat.global.security.JwtFilter;
import jp.co.translacat.global.security.JWTService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = PlatformController.class,
        properties = {
                "cors.allowed-origin=http://localhost:3000",
                "jwt.token.secret-key=dGVzdC1zZWNyZXQta2V5LXRlc3Qtc2VjcmV0LWtleQ==",
                "jwt.token.expired.access=86400000",
                "jwt.token.expired.refresh=604800000"
        }
)
@Import({
        SecurityConfig.class,
        JwtFilter.class,
        JWTService.class,
        ApiLoggingFilter.class,
        SecurityConfigNovelAccessTest.SecurityRegressionController.class,
        SecurityConfigNovelAccessTest.TestSupportConfig.class
})
class SecurityConfigNovelAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void adminCanAccessNovelApi() throws Exception {
        mockMvc.perform(get("/api/v1/platforms")
                        .with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/platforms",
            "/api/v1/syosetu/genres",
            "/api/v1/syosetu/ranking/periods",
            "/api/v1/syosetu/novels/n1234",
            "/api/v1/syosetu/n1234/episodes/1",
            "/api/v1/syosetu/search/novels",
            "/api/v1/recent/top10"
    })
    void userSeesNotFoundForEveryNovelGetApi(String path) throws Exception {
        mockMvc.perform(get(path)
                        .with(SecurityMockMvcRequestPostProcessors.user("user").roles("USER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void userSeesNotFoundForNovelDictionaryApi() throws Exception {
        mockMvc.perform(post("/api/v1/dictionary/register")
                        .with(SecurityMockMvcRequestPostProcessors.user("user").roles("USER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void anonymousUserSeesNotFoundForNovelApi() throws Exception {
        mockMvc.perform(get("/api/v1/platforms"))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonNovelProtectedApisKeepUnauthorizedAndForbiddenResponses() throws Exception {
        mockMvc.perform(get("/api/v1/security-regression/protected"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/admin/security-regression")
                        .with(SecurityMockMvcRequestPostProcessors.user("user").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @RestController
    public static class SecurityRegressionController {

        @GetMapping("/api/v1/{platformCode}/genres")
        String genres() {
            return "ok";
        }

        @GetMapping("/api/v1/{platformCode}/ranking/periods")
        String rankingPeriods() {
            return "ok";
        }

        @GetMapping("/api/v1/{platformCode}/novels/{novelId}")
        String novel() {
            return "ok";
        }

        @GetMapping("/api/v1/{platformCode}/{novelId}/episodes/{episodeId}")
        String episode() {
            return "ok";
        }

        @GetMapping("/api/v1/{platformCode}/search/novels")
        String searchNovels() {
            return "ok";
        }

        @PostMapping("/api/v1/dictionary/register")
        String registerDictionary() {
            return "ok";
        }

        @GetMapping("/api/v1/recent/top10")
        String recentViews() {
            return "ok";
        }

        @GetMapping("/api/v1/security-regression/protected")
        String protectedApi() {
            return "ok";
        }

        @GetMapping("/api/v1/admin/security-regression")
        String adminApi() {
            return "ok";
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestSupportConfig {

        @Bean
        PlatformService platformService() {
            return new PlatformService(null, null) {
                @Override
                public List<PlatformResponseDto> platforms() {
                    return List.of();
                }
            };
        }
    }
}
