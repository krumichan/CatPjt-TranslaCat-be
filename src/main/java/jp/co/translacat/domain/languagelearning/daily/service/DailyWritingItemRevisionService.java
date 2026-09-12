package jp.co.translacat.domain.languagelearning.daily.service;

import jp.co.translacat.domain.languagelearning.daily.entity.DailyWritingItem;

import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class DailyWritingItemRevisionService {

    public String revision(DailyWritingItem item) {
        MessageDigest digest = sha256();
        update(digest, String.valueOf(item.getOrderNo()));
        update(digest, item.getDifficulty() == null
                ? null
                : item.getDifficulty().name());
        update(digest, item.getOriginText());
        update(digest, item.getKeywordsJson());
        update(digest, item.getFocusMetricsJson());
        update(digest, item.getFocusReason());
        update(digest, item.getProvidedFactsJson());
        update(digest, item.getRequiredIntentsJson());
        update(digest, item.getResponseConstraintsJson());
        return HexFormat.of().formatHex(digest.digest());
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 MessageDigest is unavailable.",
                    exception
            );
        }
    }

    private void update(MessageDigest digest, String value) {
        if (value == null) {
            digest.update(ByteBuffer.allocate(Integer.BYTES)
                    .putInt(-1)
                    .array());
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES)
                .putInt(bytes.length)
                .array());
        digest.update(bytes);
    }
}
