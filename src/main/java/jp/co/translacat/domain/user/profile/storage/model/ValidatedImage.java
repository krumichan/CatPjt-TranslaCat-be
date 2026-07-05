package jp.co.translacat.domain.user.profile.storage.model;

public record ValidatedImage(
        String contentType,
        String extension,
        byte[] bytes
) {
}
