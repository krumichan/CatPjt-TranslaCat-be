package jp.co.translacat.domain.chat.ai.controller;

import jp.co.translacat.domain.chat.ai.dto.request.ChatAiSystemSettingUpdateRequestDto;
import jp.co.translacat.domain.chat.ai.dto.response.ChatAiSystemSettingResponseDto;
import jp.co.translacat.domain.chat.ai.service.ChatAiSystemSettingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminChatAiSettingControllerTest {

    @Mock private ChatAiSystemSettingService service;

    @Test
    void delegatesAdminSettingApis() {
        AdminChatAiSettingController controller =
                new AdminChatAiSettingController(service);
        ChatAiSystemSettingUpdateRequestDto request =
                new ChatAiSystemSettingUpdateRequestDto(
                        3,
                        20,
                        300,
                        2,
                        24,
                        72,
                        168,
                        null,
                        null,
                        30,
                        12000,
                        800,
                        5,
                        60
                );
        when(service.getSettings())
                .thenReturn(mock(ChatAiSystemSettingResponseDto.class));
        when(service.updateSettings(request))
                .thenReturn(mock(ChatAiSystemSettingResponseDto.class));

        controller.getSettings();
        controller.updateSettings(request);

        verify(service).getSettings();
        verify(service).updateSettings(request);
    }
}
