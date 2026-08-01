package jp.co.translacat.domain.chat.translation.service;

import jp.co.translacat.domain.chat.member.service.ChatRoomMemberQueryService;
import jp.co.translacat.domain.chat.message.repository.ChatMessageRepository;
import jp.co.translacat.domain.chat.openchat.service.OpenChatAccessService;
import jp.co.translacat.domain.chat.openchat.support.OpenChatErrorCode;
import jp.co.translacat.domain.chat.translation.repository.ChatMessageTranslationRepository;
import jp.co.translacat.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatMessageTranslationRetryServiceOpenChatBanTest {

    @Mock private ChatMessageRepository messageRepository;
    @Mock private ChatMessageTranslationRepository translationRepository;
    @Mock private ChatRoomMemberQueryService memberQueryService;
    @Mock private OpenChatAccessService accessService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private ChatMessageTranslationRetryService service;

    @BeforeEach
    void setUp() {
        service = new ChatMessageTranslationRetryService(
                messageRepository,
                translationRepository,
                memberQueryService,
                accessService,
                eventPublisher
        );
    }

    @Test
    void bannedOpenChatUserCannotRetryTranslation() {
        doThrow(new BusinessException(
                "banned",
                OpenChatErrorCode.BANNED
        )).when(accessService).validateOpenRoomMemberAccess(10L, 100L);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.retry(
                        10L,
                        100L,
                        200L,
                        "ja"
                ))
                .satisfies(exception -> assertThat(
                        exception.getErrorCode()
                ).isEqualTo(OpenChatErrorCode.BANNED));

        verify(memberQueryService, never())
                .getActiveMember(10L, 100L);
        verify(messageRepository, never())
                .findByIdAndChatRoomIdAndDeletedAtIsNull(200L, 100L);
    }
}
