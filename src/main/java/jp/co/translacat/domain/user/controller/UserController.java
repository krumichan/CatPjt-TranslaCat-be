package jp.co.translacat.domain.user.controller;

import jakarta.validation.Valid;
import jp.co.translacat.domain.user.dto.*;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.service.OAuth2AuthenticationService;
import jp.co.translacat.domain.user.service.UserService;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.exception.BusinessException;
import jp.co.translacat.global.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class UserController {

    private final UserService userService;
    private final OAuth2AuthenticationService oAuthService;

    @PostMapping("/register")
    public ResponseDto<User> register(@RequestBody @Valid UserCreateRequestDto userCreateRequestDto) {
        User createdUser = userService.register(userCreateRequestDto);
        createdUser.setPassword(null);
        return ResponseUtil.ok(createdUser);
    }

    @PostMapping("/login")
    public ResponseDto<UserLoginResponseDto> login(@RequestBody @Valid UserLoginRequestDto userLoginRequestDto) {
        return ResponseUtil.ok(userService.authentication(userLoginRequestDto));
    }

    @PostMapping("/social/{provider}")
    public ResponseDto<UserLoginResponseDto> socialLogin(
            @PathVariable String provider,
            @RequestBody @Valid OAuth2LoginRequestDto requestDto) {
        return ResponseUtil.ok(oAuthService.loginViaSocial(provider, requestDto.getIdToken()));
    }

    @PostMapping("/logout")
    public ResponseDto<String> logout(@RequestHeader("Authorization") String token) {
        // HTTP 인증 헤더와 compact JWT를 구분한다. 서명 검증과 사용자별 refresh 폐기는 기존 서비스가 수행한다.
        if (token == null || !token.startsWith("Bearer ") || token.substring(7).isBlank()) {
            throw new BusinessException("Bearer 인증 토큰이 필요합니다.", "INVALID_AUTHORIZATION_HEADER");
        }
        return ResponseUtil.ok(userService.logout(token.substring(7)));
    }

    @PostMapping("/token/refresh")
    public ResponseDto<UserLoginResponseDto> refresh(@RequestBody UserRefreshTokenRequestDto request) {
        String refreshToken = request.getRefreshToken();
        return ResponseUtil.ok(userService.refreshAccessToken(refreshToken));
    }
}
