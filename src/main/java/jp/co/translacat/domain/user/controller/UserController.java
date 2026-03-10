package jp.co.translacat.domain.user.controller;

import jakarta.validation.Valid;
import jp.co.translacat.domain.user.dto.*;
import jp.co.translacat.domain.user.service.OAuth2AuthenticationService;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.utils.ResponseUtil;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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
        return ResponseUtil.ok(userService.logout(token));
    }

    @PostMapping("/token/refresh")
    public ResponseDto<UserLoginResponseDto> refresh(@RequestBody UserRefreshTokenRequestDto request) {
        String refreshToken = request.getRefreshToken();
        return ResponseUtil.ok(userService.refreshAccessToken(refreshToken));
    }
}
