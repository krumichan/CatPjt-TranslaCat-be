package jp.co.translacat.domain.chat.ai.service;

import jp.co.translacat.domain.chat.ai.dto.request.ChatAiSystemSettingUpdateRequestDto;
import jp.co.translacat.domain.chat.ai.dto.response.ChatAiSystemSettingResponseDto;
import jp.co.translacat.domain.chat.ai.entity.ChatAiSystemSetting;
import jp.co.translacat.domain.chat.ai.repository.ChatAiSystemSettingRepository;
import jp.co.translacat.domain.chat.ai.support.ChatAiErrorCode;
import jp.co.translacat.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatAiSystemSettingServiceTest {

    @Mock private ChatAiSystemSettingRepository repository;

    private ChatAiSystemSettingService service;

    @BeforeEach
    void setUp() {
        service = new ChatAiSystemSettingService(repository);
    }

    @Test
    void createsDocs9DefaultValuesWhenSettingDoesNotExist() {
        when(repository.findById(ChatAiSystemSetting.DEFAULT_ID))
                .thenReturn(Optional.empty());
        when(repository.save(any(ChatAiSystemSetting.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ChatAiSystemSettingResponseDto response = service.getSettings();

        assertThat(response.maxAiMembersPerRoom()).isEqualTo(2);
        assertThat(response.conversationResponseRate()).isEqualTo(15);
        assertThat(response.conversationCooldownSeconds()).isEqualTo(180);
        assertThat(response.conversationMinHumanMessagesAfterAi()).isEqualTo(2);
        assertThat(response.revivalFirstDelayHours()).isEqualTo(24);
        assertThat(response.revivalSecondDelayHours()).isEqualTo(72);
        assertThat(response.revivalThirdDelayHours()).isEqualTo(168);
        assertThat(response.revivalAllowedStartTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(response.revivalAllowedEndTime()).isEqualTo(LocalTime.of(22, 0));
        assertThat(response.contextMaxMessages()).isEqualTo(30);
        assertThat(response.contextMaxCharacters()).isEqualTo(12_000);
        assertThat(response.replyMaxCharacters()).isEqualTo(800);
        assertThat(response.mentionRateLimitCount()).isEqualTo(5);
        assertThat(response.mentionRateLimitWindowSeconds()).isEqualTo(60);
    }

    @Test
    void rejectsInvalidResponseRate() {
        ChatAiSystemSetting setting = ChatAiSystemSetting.createDefault();
        when(repository.findById(ChatAiSystemSetting.DEFAULT_ID))
                .thenReturn(Optional.of(setting));

        ChatAiSystemSettingUpdateRequestDto request =
                new ChatAiSystemSettingUpdateRequestDto(
                        null,
                        101,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                );

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.updateSettings(request))
                .satisfies(exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ChatAiErrorCode.SETTING_INVALID));
    }
}
