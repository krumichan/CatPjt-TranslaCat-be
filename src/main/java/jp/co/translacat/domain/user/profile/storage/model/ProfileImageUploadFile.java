package jp.co.translacat.domain.user.profile.storage.model;

public record ProfileImageUploadFile(
        String originalFilename,
        String contentType,
        byte[] bytes
) {
}
