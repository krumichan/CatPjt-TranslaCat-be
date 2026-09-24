package jp.co.translacat.domain.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.user.dto.UserCreateRequestDto;
import jp.co.translacat.domain.user.repository.RefreshTokenRepository;
import jp.co.translacat.domain.user.repository.UserAllowedRepository;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.global.security.JWTService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRegistrationPasswordTest {
    @Mock UserRepository userRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock UserAllowedRepository userAllowedRepository;
    @Mock JWTService jwtService;
    @Mock AuthenticationManager authenticationManager;
    @Mock PasswordEncoder passwordEncoder;
    @InjectMocks UserService userService;

    @Test
    void localRegistrationStoresOnlyTheEncodedPassword() throws Exception {
        var request = new ObjectMapper().readValue(
                "{\"email\":\"receipt@example.com\",\"password\":\"plain-secret\",\"username\":\"receipt\"}",
                UserCreateRequestDto.class);
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());
        when(userRepository.existsByPublicId(any())).thenReturn(false);
        when(passwordEncoder.encode("plain-secret")).thenReturn("encoded-secret");
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var saved = userService.register(request);

        assertThat(saved.getPassword()).isEqualTo("encoded-secret");
        verify(passwordEncoder).encode("plain-secret");
    }
}
