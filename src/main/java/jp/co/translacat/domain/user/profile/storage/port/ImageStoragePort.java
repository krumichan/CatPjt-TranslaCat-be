package jp.co.translacat.domain.user.profile.storage.port;

import jp.co.translacat.domain.user.profile.storage.model.ImageStorageUpload;

public interface ImageStoragePort {

    void store(ImageStorageUpload upload);

    void delete(String objectKey);

    String resolvePublicUrl(String objectKey);
}
