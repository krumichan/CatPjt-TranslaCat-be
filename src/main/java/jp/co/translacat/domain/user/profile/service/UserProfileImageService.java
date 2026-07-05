package jp.co.translacat.domain.user.profile.service;

import jp.co.translacat.domain.user.profile.dto.UserProfileResponseDto;
import jp.co.translacat.domain.user.profile.entity.UserProfile;
import jp.co.translacat.domain.user.profile.storage.model.ImageStorageUpload;
import jp.co.translacat.domain.user.profile.storage.model.ProfileImageType;
import jp.co.translacat.domain.user.profile.storage.model.ProfileImageUploadFile;
import jp.co.translacat.domain.user.profile.storage.model.ValidatedImage;
import jp.co.translacat.domain.user.profile.storage.port.ImageStoragePort;
import jp.co.translacat.domain.user.profile.storage.service.ProfileImageValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileImageService {

    private final UserProfileService userProfileService;
    private final ProfileImageValidator profileImageValidator;
    private final ImageStoragePort imageStoragePort;

    @Transactional
    public UserProfileResponseDto uploadProfileImage(
            Long userId,
            ProfileImageUploadFile file
    ) {
        return upload(userId, file, ProfileImageType.PROFILE);
    }

    @Transactional
    public UserProfileResponseDto uploadProfileBackgroundImage(
            Long userId,
            ProfileImageUploadFile file
    ) {
        return upload(userId, file, ProfileImageType.BACKGROUND);
    }

    @Transactional
    public UserProfileResponseDto deleteProfileImage(Long userId) {
        UserProfile userProfile = userProfileService.getOrCreateByUserId(userId);
        String oldObjectKey = userProfile.clearProfileImage();

        deleteOldObjectAfterCommit(oldObjectKey);

        return userProfileService.toResponse(userProfile);
    }

    @Transactional
    public UserProfileResponseDto deleteProfileBackgroundImage(Long userId) {
        UserProfile userProfile = userProfileService.getOrCreateByUserId(userId);
        String oldObjectKey = userProfile.clearProfileBackgroundImage();

        deleteOldObjectAfterCommit(oldObjectKey);

        return userProfileService.toResponse(userProfile);
    }

    private UserProfileResponseDto upload(
            Long userId,
            ProfileImageUploadFile file,
            ProfileImageType imageType
    ) {
        UserProfile userProfile = userProfileService.getOrCreateByUserId(userId);
        ValidatedImage validatedImage =
                profileImageValidator.validate(file, imageType);

        String newObjectKey = imageType.createObjectKey(
                userId,
                validatedImage.extension()
        );

        String oldObjectKey = currentObjectKey(userProfile, imageType);

        imageStoragePort.store(new ImageStorageUpload(
                newObjectKey,
                validatedImage.contentType(),
                validatedImage.bytes()
        ));

        registerUploadSynchronization(newObjectKey, oldObjectKey);
        replaceObjectKey(userProfile, imageType, newObjectKey);

        return userProfileService.toResponse(userProfile);
    }

    private String currentObjectKey(
            UserProfile userProfile,
            ProfileImageType imageType
    ) {
        return switch (imageType) {
            case PROFILE -> userProfile.getProfileImageObjectKey();
            case BACKGROUND ->
                    userProfile.getProfileBackgroundImageObjectKey();
        };
    }

    private void replaceObjectKey(
            UserProfile userProfile,
            ProfileImageType imageType,
            String newObjectKey
    ) {
        switch (imageType) {
            case PROFILE ->
                    userProfile.replaceProfileImageObjectKey(newObjectKey);
            case BACKGROUND ->
                    userProfile.replaceProfileBackgroundImageObjectKey(
                            newObjectKey
                    );
        }
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
                    "이미지 저장 변경은 활성 트랜잭션 안에서 실행되어야 합니다."
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
                    "이미지 object 삭제 실패. cleanup 대상 objectKey={}",
                    objectKey,
                    e
            );
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
