package jp.co.translacat.domain.chat.ai.service;

import jp.co.translacat.domain.chat.ai.dto.response.ChatAiMemberResponseDto;
import jp.co.translacat.domain.chat.ai.entity.ChatAiAgent;
import jp.co.translacat.domain.chat.ai.entity.ChatRoomAiMember;
import jp.co.translacat.domain.chat.member.event.ChatRoomMembersChangedApplicationEvent;
import jp.co.translacat.domain.user.profile.storage.model.ImageStorageUpload;
import jp.co.translacat.domain.user.profile.storage.model.ProfileImageType;
import jp.co.translacat.domain.user.profile.storage.model.ProfileImageUploadFile;
import jp.co.translacat.domain.user.profile.storage.model.ValidatedImage;
import jp.co.translacat.domain.user.profile.storage.port.ImageStoragePort;
import jp.co.translacat.domain.user.profile.storage.service.ProfileImageValidator;
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
public class ChatAiProfileImageService {

    private final ChatAiAccessService accessService;
    private final ChatAiMemberService aiMemberService;
    private final ProfileImageValidator imageValidator;
    private final ChatAiProfileImageKeyFactory imageKeyFactory;
    private final ImageStoragePort imageStoragePort;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ChatAiMemberResponseDto uploadProfileImage(
            Long loginUserId,
            Long roomId,
            Long aiMemberId,
            ProfileImageUploadFile file
    ) {
        return upload(
                loginUserId,
                roomId,
                aiMemberId,
                file,
                ProfileImageType.PROFILE
        );
    }

    @Transactional
    public ChatAiMemberResponseDto uploadBackgroundImage(
            Long loginUserId,
            Long roomId,
            Long aiMemberId,
            ProfileImageUploadFile file
    ) {
        return upload(
                loginUserId,
                roomId,
                aiMemberId,
                file,
                ProfileImageType.BACKGROUND
        );
    }

    @Transactional
    public ChatAiMemberResponseDto deleteProfileImage(
            Long loginUserId,
            Long roomId,
            Long aiMemberId
    ) {
        accessService.getManageableRoomForUpdate(loginUserId, roomId);
        ChatRoomAiMember aiMember = aiMemberService.getActiveMember(
                roomId,
                aiMemberId
        );
        String oldObjectKey = aiMember.getAiAgent().clearProfileImage();
        deleteOldObjectAfterCommit(oldObjectKey);
        publishMembersChanged(roomId);
        return aiMemberService.toResponse(aiMember);
    }

    @Transactional
    public ChatAiMemberResponseDto deleteBackgroundImage(
            Long loginUserId,
            Long roomId,
            Long aiMemberId
    ) {
        accessService.getManageableRoomForUpdate(loginUserId, roomId);
        ChatRoomAiMember aiMember = aiMemberService.getActiveMember(
                roomId,
                aiMemberId
        );
        String oldObjectKey = aiMember.getAiAgent()
                .clearProfileBackgroundImage();
        deleteOldObjectAfterCommit(oldObjectKey);
        publishMembersChanged(roomId);
        return aiMemberService.toResponse(aiMember);
    }

    private ChatAiMemberResponseDto upload(
            Long loginUserId,
            Long roomId,
            Long aiMemberId,
            ProfileImageUploadFile file,
            ProfileImageType imageType
    ) {
        accessService.getManageableRoomForUpdate(loginUserId, roomId);
        ChatRoomAiMember aiMember = aiMemberService.getActiveMember(
                roomId,
                aiMemberId
        );
        ChatAiAgent agent = aiMember.getAiAgent();
        ValidatedImage validatedImage = imageValidator.validate(
                file,
                imageType
        );
        String newObjectKey = imageKeyFactory.create(
                aiMemberId,
                imageType,
                validatedImage.extension()
        );
        String oldObjectKey = currentObjectKey(agent, imageType);

        imageStoragePort.store(new ImageStorageUpload(
                newObjectKey,
                validatedImage.contentType(),
                validatedImage.bytes()
        ));

        registerUploadSynchronization(newObjectKey, oldObjectKey);
        replaceObjectKey(agent, imageType, newObjectKey);
        publishMembersChanged(roomId);
        return aiMemberService.toResponse(aiMember);
    }

    private void publishMembersChanged(Long roomId) {
        eventPublisher.publishEvent(
                ChatRoomMembersChangedApplicationEvent.of(roomId)
        );
    }

    private String currentObjectKey(
            ChatAiAgent agent,
            ProfileImageType imageType
    ) {
        return imageType == ProfileImageType.PROFILE
                ? agent.getProfileImageObjectKey()
                : agent.getProfileBackgroundImageObjectKey();
    }

    private void replaceObjectKey(
            ChatAiAgent agent,
            ProfileImageType imageType,
            String objectKey
    ) {
        if (imageType == ProfileImageType.PROFILE) {
            agent.replaceProfileImageObjectKey(objectKey);
            return;
        }
        agent.replaceProfileBackgroundImageObjectKey(objectKey);
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

    private void requireTransactionSynchronization() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException(
                    "AI 프로필 이미지 변경은 활성 트랜잭션 안에서 실행되어야 합니다."
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
                    "AI 프로필 이미지 object 삭제 실패. objectKey={}",
                    objectKey,
                    e
            );
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
