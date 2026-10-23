package jp.co.translacat.domain.user.profile.storage.port;

import jp.co.translacat.domain.user.profile.storage.model.ImageStorageUpload;

public interface ImageStoragePort {

    void store(ImageStorageUpload upload);

    void delete(String objectKey);

    String resolvePublicUrl(String objectKey);

    default boolean supportsAccountDeletion() { return false; }

    /** 계정 전용 prefix의 미연결 과거 객체도 정리한다. 남아 있으면 false이며 같은 계정으로 재호출한다. */
    default boolean deleteUserProfileObjects(long userId) { return false; }
}
