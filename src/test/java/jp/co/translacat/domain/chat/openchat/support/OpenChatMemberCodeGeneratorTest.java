package jp.co.translacat.domain.chat.openchat.support;

import jp.co.translacat.domain.chat.openchat.profile.repository.OpenChatMemberProfileRepository;
import jp.co.translacat.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OpenChatMemberCodeGeneratorTest {

    @Mock
    private OpenChatMemberProfileRepository profileRepository;

    @Mock
    private RandomGenerator randomGenerator;

    @Test
    @DisplayName("memberCode 충돌 시 다시 생성한다")
    void retryWhenCodeCollides() {
        when(randomGenerator.nextInt(anyInt())).thenReturn(0);
        when(profileRepository.existsByMemberCode("OC-AAAAA"))
                .thenReturn(true, false);

        OpenChatMemberCodeGenerator generator =
                new OpenChatMemberCodeGenerator(
                        profileRepository,
                        randomGenerator
                );

        assertThat(generator.generate()).isEqualTo("OC-AAAAA");
        verify(profileRepository, times(2))
                .existsByMemberCode("OC-AAAAA");
    }

    @Test
    @DisplayName("제한 횟수 동안 memberCode 생성에 실패하면 예외를 던진다")
    void failAfterMaximumAttempts() {
        when(randomGenerator.nextInt(anyInt())).thenReturn(0);
        when(profileRepository.existsByMemberCode("OC-AAAAA"))
                .thenReturn(true);

        OpenChatMemberCodeGenerator generator =
                new OpenChatMemberCodeGenerator(
                        profileRepository,
                        randomGenerator
                );

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(generator::generate)
                .satisfies(exception -> assertThat(
                        exception.getErrorCode()
                ).isEqualTo(
                        OpenChatErrorCode
                                .MEMBER_CODE_GENERATION_FAILED
                ));

        verify(profileRepository,
                times(OpenChatPolicy.MEMBER_CODE_MAX_ATTEMPTS))
                .existsByMemberCode("OC-AAAAA");
    }
}
