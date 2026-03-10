package jp.co.translacat.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SwaggerConfig 클래스
 *
 * Swagger(OpenAPI) 설정 클래스입니다.
 * 이 설정을 통해 Spring Boot에서 API 문서를 자동으로 생성하고
 * JWT 인증을 Swagger UI에서 테스트할 수 있도록 구성합니다.
 *
 * Swagger UI 접속 URL: /swagger-ui/index.html
 */
@Configuration
public class SwaggerConfig {

    /**
     * OpenAPI Bean 생성
     *
     * Swagger UI가 사용할 OpenAPI 객체를 생성합니다.
     * JWT 인증이 필요한 경우 SecurityRequirement를 추가하여
     * Swagger UI에서 Authorization 헤더를 입력할 수 있도록 합니다.
     */
    @Bean
    public OpenAPI openAPI() {
        SecurityRequirement securityRequirement =
                new SecurityRequirement().addList("JWT");
        return new OpenAPI()
                .components(this.createComponents()) // SecurityScheme 등 Components 설정
                .info(this.apiInfo())
                .addSecurityItem(securityRequirement); // JWT 보안 요구 사항 추가
    }

    /**
     * OpenAPI Components 생성
     *
     * Components에는 SecurityScheme(보안 스키마) 정의가 들어갑니다.
     * 여기서는 JWT Bearer Token 방식으로 인증하도록 설정합니다.
     */
    private Components createComponents() {
        return new Components().addSecuritySchemes("JWT"
                , new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP) // HTTP 인증 방식
                        .scheme("bearer")
                        .name("Authorization")
                        .in(SecurityScheme.In.HEADER) // Authorization 헤더 사용
                        .bearerFormat("JWT"));
    }

    /**
     * API 기본 정보 설정
     *
     * Swagger UI에 표시될 API 제목, 설명, 버전을 정의합니다.
     */
    private Info apiInfo() {
        return new Info()
                .title("API Documentation") // API 문서 제목
                .description("This is the API Documentation")
                .version("1.0");
    }
}