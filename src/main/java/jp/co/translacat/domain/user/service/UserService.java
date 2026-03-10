package jp.co.translacat.domain.user.service;

import jp.co.translacat.domain.user.entity.RefreshToken;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.entity.UserAllowed;
import jp.co.translacat.domain.user.enums.SocialType;
import jp.co.translacat.domain.user.repository.UserAllowedRepository;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.domain.user.repository.RefreshTokenRepository;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.global.security.JWTService;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.domain.user.dto.UserLoginRequestDto;
import jp.co.translacat.domain.user.dto.UserCreateRequestDto;
import jp.co.translacat.domain.user.dto.UserLoginResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserAllowedRepository userAllowedRepository;

    private final JWTService jwtService;

    private final AuthenticationManager authenticationManager;

    public User findByEmail(String email) {
        return userRepository.findByEmail(email).orElse(null);
    }

    public Optional<UserAllowed> findUserAllowedByEmail(String email) {
        return userAllowedRepository.findByEmail(email);
    }

    public User register(UserCreateRequestDto userCreateRequestDto) {
        User maybeExistUser = this.findByEmail(userCreateRequestDto.getEmail());
        if (maybeExistUser != null) {
            throw new RuntimeException(userCreateRequestDto.getEmail() + " is already exist.");
        }

        User user = User.createLocalUser(
                userCreateRequestDto.getEmail(),
                userCreateRequestDto.getPassword(),
                userCreateRequestDto.getUsername(),
                Role.USER);

        return userRepository.save(user);
    }

    public User getOrRegisterSocialUser(String email, String username, String socialId, SocialType socialType) {
        UserAllowed allowed = this.findUserAllowedByEmail(email)
            .orElseThrow(() -> new AccessDeniedException("Your email is not registered in the access list."));

        if (!allowed.isAccessible()) {
            throw new AccessDeniedException("Your access period has expired or been restricted.");
        }

        return userRepository.findBySocialIdAndSocialType(socialId, socialType)
            .map(existingUser -> {
                existingUser.setUsername(username);
                return userRepository.save(existingUser);
            })
            .orElseGet(() -> {
                User newUser = User.createSocialUser(email, username, socialType, socialId, Role.USER);
                return userRepository.save(newUser);
            });
    }

    public UserLoginResponseDto authentication(UserLoginRequestDto userLoginRequestDto) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        userLoginRequestDto.getEmail(),
                        userLoginRequestDto.getPassword()
                )
        );

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        Long userId = userPrincipal.user().getId();
        String username = userPrincipal.getUsername();

        return this.generateUserTokens(userId, username);
    }

    public String logout(String token) {
        refreshTokenRepository.deleteById(jwtService.getId(token));
        return "SUCCESS";
    }

    public UserLoginResponseDto refreshAccessToken(String refreshToken) {
        Long userId = jwtService.getId(refreshToken);

        RefreshToken rt = refreshTokenRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Refresh Token Not Found"));

        if (!rt.getToken().equals(refreshToken)) {
            throw new RuntimeException("Invalid Refresh Token");
        }

        if (rt.getExpireDate().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.deleteById(userId);
            throw new RuntimeException("Refresh Token Expired. Please login again.");
        }

        String username = jwtService.extractUsername(refreshToken);
        String newAccessToken = jwtService.generateAccessToken(userId, username);

        Date accessExpiry = jwtService.extractExpiration(newAccessToken);
        long accessTokenExpiresIn = (accessExpiry.getTime() - System.currentTimeMillis()) / 1000;

        return UserLoginResponseDto.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken)
                .accessTokenExpiresIn(accessTokenExpiresIn)
                .build();
    }

    public UserLoginResponseDto generateUserTokens(Long userId, String email) {
        String refreshToken = jwtService.generateRefreshToken(userId, email);

        LocalDateTime refreshExpiry = jwtService.extractExpiration(refreshToken)
                .toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();

        RefreshToken rt = new RefreshToken();
        rt.setUserId(userId);
        rt.setToken(refreshToken);
        rt.setExpireDate(refreshExpiry);
        refreshTokenRepository.save(rt);

        String accessToken = jwtService.generateAccessToken(userId, email);

        Date accessExpiry = jwtService.extractExpiration(accessToken);
        long accessTokenExpiresIn = (accessExpiry.getTime() - System.currentTimeMillis()) / 1000;

        return UserLoginResponseDto.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .accessTokenExpiresIn(accessTokenExpiresIn)
                .build();
    }
}
