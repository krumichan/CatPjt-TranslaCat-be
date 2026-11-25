import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 固定した JPEG とキーだけを検証し、この呼び出しで作成を確認できたキーだけを後処理する。 */
final class R2ImageVerification {
    static final String BUCKET = "translacat-private-assets";
    static final String PREFIX = "image-translation/b9f88b92-adf8-4d32-9927-b1d7018d8a81/";
    static final String CONTENT_TYPE = "image/jpeg";
    static final List<Fixture> FIXTURES = List.of(
            new Fixture("6b8ee4a3-ff98-4011-8e96-de3b73974044",
                    "866f953067aba7e004e3dbfb005b8c9448f38cc51d3dbe8cc7fbf23602e46630", 95_212),
            new Fixture("57082c5f-c233-4a0b-9871-e22a68754a5a",
                    "b8d6c54e3bb369420370b4e591ea686c7bd63bd2a2cf947af73bb44015b08a58", 96_026),
            new Fixture("c19bd758-9ed0-429c-b913-777bdb35cb05",
                    "f68e90b01e5fbb64116e047bf228a43078c3911db5f0b8d48993d99d0b765b3c", 96_213)
    );

    private R2ImageVerification() {
    }

    /** 固定のバケット、プレフィックス、JPEG の組み合わせで検証を行う。 */
    static Report verify(String bucket, String prefix, Map<String, byte[]> jpegByKey, Storage storage) {
        return verify(bucket, prefix, FIXTURES, jpegByKey, storage);
    }

    /** 合成試験で同じ三つのキーに対する JPEG のピンだけを差し替える。 */
    static Report verify(
            String bucket, String prefix, List<Fixture> expected, Map<String, byte[]> jpegByKey, Storage storage) {
        // 範囲と入力の検証を終えるまでストレージに一切アクセスしない。
        if (!BUCKET.equals(bucket) || !PREFIX.equals(prefix) || !hasFixedKeys(expected)) {
            return new Report(Outcome.SCOPE_REJECTED, List.of(), List.of(), null, false);
        }
        Map<String, byte[]> pinnedJpegs = pinInputs(expected, jpegByKey);
        if (pinnedJpegs.isEmpty()) {
            return new Report(Outcome.INPUT_REJECTED, List.of(), List.of(), null, false);
        }

        // 既存オブジェクトまたは切り詰めがある初回 LIST からは処理を進めない。
        try {
            if (!storage.list().empty()) {
                return new Report(Outcome.START_NOT_EMPTY, List.of(), List.of(), null, false);
            }
        } catch (IOException | RuntimeException failure) {
            return new Report(Outcome.START_LIST_FAILED, List.of(), List.of(), null, false);
        }

        // PUT を一件ずつ確定し、衝突・結果不明があれば後続の PUT を止める。
        List<String> confirmedKeys = new ArrayList<>();
        Outcome outcome = Outcome.PASS;
        String uncertainKey = null;
        for (Fixture fixture : expected) {
            String key = fixture.key();
            try {
                Put result = storage.put(key, pinnedJpegs.get(key).clone());
                if (result == Put.CONFLICT) {
                    outcome = Outcome.PUT_CONFLICT;
                    break;
                }
                if (result != Put.CREATED) {
                    outcome = Outcome.PUT_UNKNOWN;
                    uncertainKey = key;
                    break;
                }
                confirmedKeys.add(key);
            } catch (IOException | RuntimeException failure) {
                outcome = Outcome.PUT_UNKNOWN;
                uncertainKey = key;
                break;
            }
        }

        // すべての作成を確定した場合のみ HEAD と GET で各 JPEG を検証する。
        if (outcome == Outcome.PASS && !verifyStoredJpegs(expected, pinnedJpegs, storage)) {
            outcome = Outcome.VERIFICATION_FAILED;
        }

        // HEAD、GET、LIST の結果から削除対象を追加しない。最後の LIST は読み取りのみに使う。
        List<String> deletedKeys = deleteConfirmedKeys(confirmedKeys, storage);
        boolean finalListEmpty = checkFinalList(storage);
        if (outcome == Outcome.PASS && (deletedKeys.size() != FIXTURES.size() || !finalListEmpty)) {
            outcome = Outcome.CLEANUP_NOT_CONFIRMED;
        }
        return new Report(outcome, List.copyOf(confirmedKeys), List.copyOf(deletedKeys), uncertainKey, finalListEmpty);
    }

    // 差し替え可能な合成入力でもキー数と処理順序は本番用と一致させる。
    private static boolean hasFixedKeys(List<Fixture> expected) {
        return expected != null
                && expected.size() == FIXTURES.size()
                && expected.stream().map(Fixture::key).toList().equals(FIXTURES.stream().map(Fixture::key).toList());
    }

    // 入力のキー集合を照合し、保持するバイト列を呼び出し元から切り離す。
    private static Map<String, byte[]> pinInputs(List<Fixture> expected, Map<String, byte[]> jpegByKey) {
        Set<String> keys = new HashSet<>(expected.stream().map(Fixture::key).toList());
        if (jpegByKey == null || !keys.equals(jpegByKey.keySet())) {
            return Map.of();
        }

        Map<String, byte[]> pinnedJpegs = new HashMap<>();
        for (Fixture fixture : expected) {
            byte[] input = jpegByKey.get(fixture.key());
            if (input == null) {
                return Map.of();
            }
            byte[] snapshot = input.clone();
            if (!matchesFixture(fixture, snapshot)) {
                return Map.of();
            }
            pinnedJpegs.put(fixture.key(), snapshot);
        }
        return pinnedJpegs;
    }

    /** JPEG の始端・終端、長さ、SHA-256 ピンを照合する。 */
    static boolean matchesFixture(Fixture fixture, byte[] body) {
        return body.length == fixture.sizeBytes()
                && body.length >= 4
                && (body[0] & 0xff) == 0xff
                && (body[1] & 0xff) == 0xd8
                && (body[body.length - 2] & 0xff) == 0xff
                && (body[body.length - 1] & 0xff) == 0xd9
                && sha256(body).equals(fixture.sha256());
    }

    // 入出力のピン照合に使う小文字 SHA-256 を計算する。
    private static String sha256(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 unavailable", unavailable);
        }
    }

    // HEAD のメタデータ、GET の生バイトと SHA-256 のどれか一つでも不一致なら検証を止める。
    private static boolean verifyStoredJpegs(
            List<Fixture> expected, Map<String, byte[]> pinnedJpegs, Storage storage) {
        try {
            for (Fixture fixture : expected) {
                Head head = storage.head(fixture.key());
                if (head.sizeBytes() != fixture.sizeBytes() || !CONTENT_TYPE.equals(head.contentType())) {
                    return false;
                }

                byte[] returned = storage.get(fixture.key(), fixture.sizeBytes() + 1);
                if (!matchesFixture(fixture, returned) || !Arrays.equals(pinnedJpegs.get(fixture.key()), returned)) {
                    return false;
                }
            }
            return true;
        } catch (IOException | RuntimeException failure) {
            return false;
        }
    }

    // 確定済みキーにつき一回だけ削除を試み、一件の失敗で残りの処理を止めない。
    private static List<String> deleteConfirmedKeys(List<String> confirmedKeys, Storage storage) {
        List<String> deletedKeys = new ArrayList<>();
        for (String key : confirmedKeys) {
            try {
                storage.delete(key);
                deletedKeys.add(key);
            } catch (IOException | RuntimeException failure) {
                // 応答未確定のキーは削除済みとして記録しない。再試行もしない。
            }
        }
        return deletedKeys;
    }

    // LIST が取得できないか件数を確定できない場合は空と判定しない。
    private static boolean checkFinalList(Storage storage) {
        try {
            return storage.list().empty();
        } catch (IOException | RuntimeException failure) {
            return false;
        }
    }

    /** 同じ実行で使うバイト長、SHA-256、固定キーの組。 */
    record Fixture(String key, String sha256, int sizeBytes) {
    }

    /** 件数と打ち切りフラグを確認するための単一 LIST 応答。 */
    record Listing(int count, boolean truncated) {
        /** LIST に値がないことを確認できた場合に true を返す。 */
        boolean empty() {
            return count == 0 && !truncated;
        }
    }

    /** 成功した HEAD から取り出したサイズと MIME タイプ。 */
    record Head(long sizeBytes, String contentType) {
    }

    enum Put {
        CREATED, CONFLICT, UNKNOWN
    }

    enum Outcome {
        PASS, SCOPE_REJECTED, INPUT_REJECTED, START_LIST_FAILED, START_NOT_EMPTY,
        PUT_CONFLICT, PUT_UNKNOWN, VERIFICATION_FAILED, CLEANUP_NOT_CONFIRMED
    }

    /** 作成・削除の成功応答と確定できない PUT を区別して返す。 */
    record Report(
            Outcome outcome, List<String> confirmedKeys, List<String> deletedKeys,
            String uncertainKey, boolean finalListEmpty) {
        /** 三件の作成・検証・削除と最終 LIST の空をすべて確認できた場合に true を返す。 */
        boolean passed() {
            return outcome == Outcome.PASS;
        }
    }

    /** 固定プレフィックスだけを使うストレージ。メソッドのキーは末尾の UUID。 */
    interface Storage {
        /** 固定プレフィックスの一ページを取得し、件数と打ち切り状態を返す。 */
        Listing list() throws IOException;

        /** キーが存在しない場合だけ作成し、成功応答・412・不明を区別する。 */
        Put put(String key, byte[] bytes) throws IOException;

        /** キーの HEAD を取得する。 */
        Head head(String key) throws IOException;

        /** キーの GET を指定した最大バイト数まで取得する。 */
        byte[] get(String key, int maxBytes) throws IOException;

        /** 指定された一件だけを削除し、成功応答が得られない場合は失敗する。 */
        void delete(String key) throws IOException;
    }
}
