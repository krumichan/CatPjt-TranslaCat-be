package jp.co.translacat.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;

@Getter
public class UserCreateRequestDto {
    @NotNull(message = "The 'email' does not allow null values.")
    @Pattern(regexp = "^[\\w-]+(\\.[\\w-]+)*@[\\w-]+(\\.[\\w-]+)*(\\.[a-zA-Z]{2,})$", message = "Invalid email format")
    @Schema(description = "user email", example = "tester@gmail.com")
    private String email;

    @NotBlank(message = "The 'password' field does not allow null or empty values.")
    @Schema(description = "user password", example = "tester")
    private String password;

    @NotBlank(message = "The 'username' field does not allow null or empty values.")
    @Schema(description = "user name", example = "tester")
    private String username;
}
