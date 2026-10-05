package jp.co.translacat.domain.novel.episode.service;

import jp.co.translacat.domain.novel.episode.entity.EpisodeContent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

public final class EpisodeContentSnapshot {
    private EpisodeContentSnapshot() {}

    public static String fingerprint(List<EpisodeContent> contents) {
        // 위치·본문·번역·루비를 함께 비교해 늦은 저장이 새 번역/사전 변경을 덮지 못하게 한다.
        StringBuilder identity = new StringBuilder();
        contents.stream().sorted(Comparator.comparingInt(EpisodeContent::getSequence)).forEach(content -> {
            append(identity, Integer.toString(content.getSequence()));
            append(identity, content.getContent());
            append(identity, content.getContentJa());
            append(identity, content.getContentKo());
        });
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(identity.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Snapshot digest is unavailable.");
        }
    }

    private static void append(StringBuilder identity, String value) {
        if (value == null) identity.append("-1:");
        else identity.append(value.length()).append(':').append(value);
    }
}
