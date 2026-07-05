package jp.co.translacat.domain.user.profile.storage.model;

public record ImageStorageUpload(
        String objectKey,
        String contentType,
        byte[] bytes
) {
}
