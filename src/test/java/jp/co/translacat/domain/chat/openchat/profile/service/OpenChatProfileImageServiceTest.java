package jp.co.translacat.domain.chat.openchat.profile.service;

import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.openchat.dto.response.OpenChatMemberProfileResponseDto;
import jp.co.translacat.domain.chat.openchat.event.OpenChatProfileUpdatedApplicationEvent;
import jp.co.translacat.domain.chat.openchat.profile.entity.OpenChatMemberProfile;
import jp.co.translacat.domain.chat.openchat.profile.repository.OpenChatMemberProfileRepository;
import jp.co.translacat.domain.chat.openchat.service.OpenChatAccessService;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.domain.user.profile.storage.model.ImageStorageUpload;
import jp.co.translacat.domain.user.profile.storage.model.ProfileImageType;
import jp.co.translacat.domain.user.profile.storage.model.ProfileImageUploadFile;
import jp.co.translacat.domain.user.profile.storage.model.ValidatedImage;
import jp.co.translacat.domain.user.profile.storage.port.ImageStoragePort;
import jp.co.translacat.domain.user.profile.storage.service.ProfileImageValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenChatProfileImageServiceTest {

    @Mock private OpenChatAccessService accessService;
    @Mock private OpenChatMemberProfileRepository profileRepository;
    @Mock private OpenChatProfileResponseMapper responseMapper;
    @Mock private ProfileImageValidator imageValidator;
    @Mock private OpenChatProfileImageKeyFactory imageKeyFactory;
    @Mock private ImageStoragePort imageStoragePort;
    @Mock private ApplicationEventPublisher eventPublisher;

    private OpenChatProfileImageService service;
    private ChatRoomMember member;
    private OpenChatMemberProfile profile;

    @BeforeEach
    void setUp() {
        service = new OpenChatProfileImageService(
                accessService,
                profileRepository,
                responseMapper,
                imageValidator,
                imageKeyFactory,
                imageStoragePort,
                eventPublisher
        );

        User user = User.createLocalUser(
                "image@open.test",
                "password",
                "image-user",
                Role.USER,
                "OPENIMAGE1"
        );
        user.setId(10L);
        ChatRoom room = ChatRoom.createOpenRoom("open", "desc", user);
        ReflectionTestUtils.setField(room, "id", 100L);
        member = ChatRoomMember.createOwner(room, user, "ko", "ja");
        ReflectionTestUtils.setField(member, "id", 20L);
        profile = OpenChatMemberProfile.create(
                member,
                "OC-ABCDE",
                "cat",
                "open-chat-profiles/20/old.png"
        );

        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void uploadStoresNewObjectAndDeletesOldObjectAfterCommit() {
        byte[] bytes = new byte[]{1, 2, 3};
        ProfileImageUploadFile file = new ProfileImageUploadFile(
                "cat.png",
                "image/png",
                bytes
        );
        ValidatedImage validated = new ValidatedImage(
                "image/png",
                "png",
                bytes
        );
        OpenChatMemberProfileResponseDto mapped = mappedResponse();

        when(accessService.getActiveOpenMember(10L, 100L))
                .thenReturn(member);
        when(profileRepository.findByChatRoomMemberId(20L))
                .thenReturn(Optional.of(profile));
        when(imageValidator.validate(file, ProfileImageType.PROFILE))
                .thenReturn(validated);
        when(imageKeyFactory.create(20L, "png"))
                .thenReturn("open-chat-profiles/20/new.png");
        when(responseMapper.toResponse(profile)).thenReturn(mapped);

        OpenChatMemberProfileResponseDto result = service.upload(
                10L,
                100L,
                file
        );

        assertThat(result).isSameAs(mapped);
        assertThat(profile.getProfileImageObjectKey())
                .isEqualTo("open-chat-profiles/20/new.png");

        ArgumentCaptor<ImageStorageUpload> uploadCaptor =
                ArgumentCaptor.forClass(ImageStorageUpload.class);
        verify(imageStoragePort).store(uploadCaptor.capture());
        assertThat(uploadCaptor.getValue().objectKey())
                .isEqualTo("open-chat-profiles/20/new.png");
        verify(imageStoragePort, never())
                .delete("open-chat-profiles/20/old.png");

        synchronizations().forEach(TransactionSynchronization::afterCommit);

        verify(imageStoragePort)
                .delete("open-chat-profiles/20/old.png");
        verify(eventPublisher).publishEvent(
                any(OpenChatProfileUpdatedApplicationEvent.class)
        );
    }

    @Test
    void uploadDeletesNewObjectWhenTransactionRollsBack() {
        byte[] bytes = new byte[]{1};
        ProfileImageUploadFile file = new ProfileImageUploadFile(
                "cat.webp",
                "image/webp",
                bytes
        );

        when(accessService.getActiveOpenMember(10L, 100L))
                .thenReturn(member);
        when(profileRepository.findByChatRoomMemberId(20L))
                .thenReturn(Optional.of(profile));
        when(imageValidator.validate(file, ProfileImageType.PROFILE))
                .thenReturn(new ValidatedImage(
                        "image/webp",
                        "webp",
                        bytes
                ));
        when(imageKeyFactory.create(20L, "webp"))
                .thenReturn("open-chat-profiles/20/new.webp");
        when(responseMapper.toResponse(profile))
                .thenReturn(mappedResponse());

        service.upload(10L, 100L, file);

        synchronizations().forEach(synchronization ->
                synchronization.afterCompletion(
                        TransactionSynchronization.STATUS_ROLLED_BACK
                )
        );

        verify(imageStoragePort)
                .delete("open-chat-profiles/20/new.webp");
        verify(imageStoragePort, never())
                .delete("open-chat-profiles/20/old.png");
    }

    @Test
    void deleteClearsKeyAndDeletesObjectOnlyAfterCommit() {
        when(accessService.getActiveOpenMember(10L, 100L))
                .thenReturn(member);
        when(profileRepository.findByChatRoomMemberId(20L))
                .thenReturn(Optional.of(profile));
        when(responseMapper.toResponse(profile))
                .thenReturn(mappedResponse());

        service.delete(10L, 100L);

        assertThat(profile.getProfileImageObjectKey()).isNull();
        verify(imageStoragePort, never())
                .delete("open-chat-profiles/20/old.png");

        synchronizations().forEach(TransactionSynchronization::afterCommit);

        verify(imageStoragePort)
                .delete("open-chat-profiles/20/old.png");
    }

    private java.util.List<TransactionSynchronization> synchronizations() {
        return TransactionSynchronizationManager.getSynchronizations();
    }

    private OpenChatMemberProfileResponseDto mappedResponse() {
        return new OpenChatMemberProfileResponseDto(
                20L,
                "OC-ABCDE",
                "cat",
                null,
                member.getRole(),
                true,
                member.getJoinedAt()
        );
    }
}
