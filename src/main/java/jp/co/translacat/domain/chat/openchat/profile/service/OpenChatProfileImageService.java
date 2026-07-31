package jp.co.translacat.domain.chat.openchat.profile.service;

import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.openchat.dto.response.OpenChatMemberProfileResponseDto;
import jp.co.translacat.domain.chat.openchat.event.OpenChatProfileUpdatedApplicationEvent;
import jp.co.translacat.domain.chat.openchat.profile.entity.OpenChatMemberProfile;
import jp.co.translacat.domain.chat.openchat.profile.repository.OpenChatMemberProfileRepository;
import jp.co.translacat.domain.chat.openchat.service.OpenChatAccessService;
import jp.co.translacat.domain.chat.openchat.support.OpenChatErrorCode;
import jp.co.translacat.domain.user.profile.storage.model.ImageStorageUpload;
import jp.co.translacat.domain.user.profile.storage.model.ProfileImageType;
import jp.co.translacat.domain.user.profile.storage.model.ProfileImageUploadFile;
import jp.co.translacat.domain.user.profile.storage.model.ValidatedImage;
import jp.co.translacat.domain.user.profile.storage.port.ImageStoragePort;
import jp.co.translacat.domain.user.profile.storage.service.ProfileImageValidator;
import jp.co.translacat.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenChatProfileImageService {

    private final OpenChatAccessService accessService;
    private final OpenChatMemberProfileRepository profileRepository;
    private final OpenChatProfileResponseMapper responseMapper;
    private final ProfileImageValidator imageValidator;
    private final OpenChatProfileImageKeyFactory imageKeyFactory;
    private final ImageStoragePort imageStoragePort;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public OpenChatMemberProfileResponseDto upload(
            Long loginUserId,
            Long roomId,
            ProfileImageUploadFile file
    ) {
        accessService.validateProfileEditAllowed(loginUserId, roomId);
        ChatRoomMember member = accessService.getActiveOpenMember(
                loginUserId,
                roomId
        );
        OpenChatMemberProfile profile = getProfile(member.getId());

        ValidatedImage validatedImage = imageValidator.validate(
                file,
                ProfileImageType.PROFILE
        );
        String newObjectKey = imageKeyFactory.create(
                member.getId(),
                validatedImage.extension()
        );
        String oldObjectKey = profile.getProfileImageObjectKey();

        imageStoragePort.store(new ImageStorageUpload(
                newObjectKey,
                validatedImage.contentType(),
                validatedImage.bytes()
        ));

        registerUploadSynchronization(newObjectKey, oldObjectKey);
        profile.replaceProfileImageObjectKey(newObjectKey);
        publishProfileUpdated(profile);

        return responseMapper.toResponse(profile);
    }

    @Transactional
    public OpenChatMemberProfileResponseDto delete(
            Long loginUserId,
            Long roomId
    ) {
        accessService.validateProfileEditAllowed(loginUserId, roomId);
        ChatRoomMember member = accessService.getActiveOpenMember(
                loginUserId,
                roomId
        );
        OpenChatMemberProfile profile = getProfile(member.getId());
        String oldObjectKey = profile.clearProfileImage();

        deleteOldObjectAfterCommit(oldObjectKey);
        publishProfileUpdated(profile);

        return responseMapper.toResponse(profile);
    }

    private OpenChatMemberProfile getProfile(Long memberId) {
        return profileRepository.findByChatRoomMemberId(memberId)
                .orElseThrow(() -> new BusinessException(
                        "OPEN 채팅 프로필을 찾을 수 없습니다.",
                        OpenChatErrorCode.PROFILE_NOT_FOUND
                ));
    }

    private void registerUploadSynchronization(
            String newObjectKey,
            String oldObjectKey
    ) {
        requireTransactionSynchronization();

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        safeDelete(oldObjectKey);
                    }

                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) {
                            safeDelete(newObjectKey);
                        }
                    }
                }
        );
    }

    private void deleteOldObjectAfterCommit(String oldObjectKey) {
        if (!hasText(oldObjectKey)) {
            return;
        }
        requireTransactionSynchronization();

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        safeDelete(oldObjectKey);
                    }
                }
        );
    }

    private void publishProfileUpdated(
            OpenChatMemberProfile profile
    ) {
        ChatRoomMember member = profile.getChatRoomMember();
        eventPublisher.publishEvent(
                OpenChatProfileUpdatedApplicationEvent.of(
                        member.getChatRoom().getId(),
                        member.getId(),
                        profile.getMemberCode(),
                        profile.getNickname(),
                        profile.getProfileImageObjectKey(),
                        member.getRole()
                )
        );
    }

    private void requireTransactionSynchronization() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException(
                    "OPEN 프로필 이미지 변경은 활성 트랜잭션 안에서 실행되어야 합니다."
            );
        }
    }

    private void safeDelete(String objectKey) {
        if (!hasText(objectKey)) {
            return;
        }
        try {
            imageStoragePort.delete(objectKey);
        } catch (RuntimeException e) {
            log.warn(
                    "OPEN 프로필 이미지 object 삭제 실패. objectKey={}",
                    objectKey,
                    e
            );
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
