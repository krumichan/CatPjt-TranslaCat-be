package jp.co.translacat.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import java.time.LocalDate;

@Getter
public class UserCreateRequestDto {
    @NotBlank
    @Schema(description = "user email", example = "tester@gmail.com")
    private String email;

    @NotBlank(message = "The 'password' field does not allow null or empty values.")
    @Schema(description = "user password", example = "tester")
    private String password;

    @NotBlank
    @Schema(description = "nickname", example = "TranslaCat")
    private String nickname;

    @NotNull
    private LocalDate birthDate;

    @NotBlank
    private String verificationToken;
}
