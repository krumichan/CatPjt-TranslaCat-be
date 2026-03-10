package jp.co.translacat.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class OAuth2LoginRequestDto {
    @NotBlank(message = "The 'idToken' field does not allow null or empty values.")
    @Schema(description = "Social ID Token")
    private String idToken;
}
