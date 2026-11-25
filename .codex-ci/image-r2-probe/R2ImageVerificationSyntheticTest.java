import java.io.IOException;
import java.net.SocketTimeoutException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** メモリ内の合成入力で、許可範囲と作成・削除の状態遷移を検証する。 */
public final class R2ImageVerificationSyntheticTest {
    private static final List<String> KEYS = List.of(
            "6b8ee4a3-ff98-4011-8e96-de3b73974044",
            "57082c5f-c233-4a0b-9871-e22a68754a5a",
            "c19bd758-9ed0-429c-b913-777bdb35cb05");

    /**
     * 網・ファイル・SDK にアクセスせず、違反があれば例外で終了する。
     *
     * @param args 未使用。
     */
    public static void main(String[] args) {
        check(KEYS.equals(R2ImageVerification.FIXTURES.stream().map(R2ImageVerification.Fixture::key).toList()),
                "Pinned key order changed");
        check(R2ImageVerification.PREFIX.equals("image-translation/b9f88b92-adf8-4d32-9927-b1d7018d8a81/"),
                "Pinned prefix changed");
        rejectsUnsafePreflight();
        verifiesAndCleansThreeObjects();
        preservesConflictingAndUnknownWrites();
        rejectsVerificationAndCleanupFailures();
        System.out.println("R2 image verification synthetic checks passed");
    }

    // 不正なスコープ・入力・初回 LIST では書き込まない。
    private static void rejectsUnsafePreflight() {
        Inputs fixture = inputs();
        Fake storage = new Fake(Fault.NONE);
        var wrongPrefix = R2ImageVerification.verify(
                R2ImageVerification.BUCKET, R2ImageVerification.PREFIX + "other/",
                fixture.expected(), fixture.bytes(), storage);
        var wrongBucket = R2ImageVerification.verify(
                "wrong-bucket", R2ImageVerification.PREFIX, fixture.expected(), fixture.bytes(), storage);
        check(wrongPrefix.outcome() == R2ImageVerification.Outcome.SCOPE_REJECTED, "Alternate prefix passed");
        check(wrongBucket.outcome() == R2ImageVerification.Outcome.SCOPE_REJECTED, "Alternate bucket passed");

        var extraKey = new HashMap<>(fixture.bytes());
        extraKey.put("foreign-object", jpeg(42));
        var wrongKeys = R2ImageVerification.verify(
                R2ImageVerification.BUCKET, R2ImageVerification.PREFIX, fixture.expected(), extraKey, storage);
        fixture.bytes().put(KEYS.getFirst(), jpeg(42));
        var wrongSha = R2ImageVerification.verify(
                R2ImageVerification.BUCKET, R2ImageVerification.PREFIX, fixture.expected(), fixture.bytes(), storage);
        check(wrongKeys.outcome() == R2ImageVerification.Outcome.INPUT_REJECTED, "Extra key passed");
        check(wrongSha.outcome() == R2ImageVerification.Outcome.INPUT_REJECTED, "Wrong SHA passed");
        check(storage.calls.isEmpty(), "Rejected scope or fixture touched storage");

        for (Fault fault : List.of(Fault.START_NONEMPTY, Fault.START_TRUNCATED, Fault.START_IO)) {
            storage = new Fake(fault);
            var report = verify(storage);
            var expected = fault == Fault.START_IO ? R2ImageVerification.Outcome.START_LIST_FAILED
                    : R2ImageVerification.Outcome.START_NOT_EMPTY;
            check(report.outcome() == expected && !report.passed(), "Unsafe first LIST passed");
            check(storage.calls.equals(List.of("LIST")), "Unsafe first LIST caused a second operation");
        }
    }

    // 正常時は 14 リクエストで、操作順序と確認済みキーが一致する。
    private static void verifiesAndCleansThreeObjects() {
        Fake storage = new Fake(Fault.NONE);
        var report = verify(storage);

        List<String> expected = new ArrayList<>(List.of("LIST"));
        KEYS.forEach(key -> expected.add("PUT " + key));
        for (String key : KEYS) {
            expected.add("HEAD " + key);
            expected.add("GET " + key);
        }
        KEYS.forEach(key -> expected.add("DELETE " + key));
        expected.add("LIST");

        check(storage.calls.equals(expected) && storage.calls.size() == 14, "Wrong request order or budget");
        check(report.passed() && report.finalListEmpty(), "Valid fixture failed");
        check(report.confirmedKeys().equals(KEYS), "Confirmation is incomplete");
        deletesOnly(storage, report, KEYS);
    }

    // 412 または不明な PUT では後続 PUT を止め、確認済みの一件だけを削除する。
    private static void preservesConflictingAndUnknownWrites() {
        for (Fault fault : List.of(Fault.CONFLICT, Fault.UNKNOWN_COMMITTED, Fault.UNKNOWN_TIMEOUT)) {
            Fake storage = new Fake(fault);
            var report = verify(storage);
            var expected = fault == Fault.CONFLICT ? R2ImageVerification.Outcome.PUT_CONFLICT
                    : R2ImageVerification.Outcome.PUT_UNKNOWN;
            check(report.outcome() == expected && !report.passed(), "Unsuccessful write passed");
            if (fault != Fault.CONFLICT) {
                check(KEYS.get(1).equals(report.uncertainKey()), "Uncertain key was lost");
            }

            check(storage.keysFor("PUT").equals(KEYS.subList(0, 2)), "PUT continued or was retried");
            check(storage.keysFor("HEAD").isEmpty() && storage.keysFor("GET").isEmpty(),
                    "Lookup was used after unconfirmed creation");
            check(report.confirmedKeys().equals(List.of(KEYS.getFirst())), "Unconfirmed key marked confirmed");
            check(storage.live.containsKey(KEYS.get(1)) == (fault != Fault.UNKNOWN_TIMEOUT), "Unowned object removed");
            check(report.finalListEmpty() == (fault == Fault.UNKNOWN_TIMEOUT), "Final LIST state was lost");
            check(storage.calls.getLast().equals("LIST"), "Failed PUT skipped final LIST");
            deletesOnly(storage, report, List.of(KEYS.getFirst()));
        }
    }

    // HEAD/GET の不一致や後処理失敗でも、確定済みだけを削除して最終 LIST を試みる。
    private static void rejectsVerificationAndCleanupFailures() {
        for (Fault fault : List.of(Fault.BAD_HEAD, Fault.BAD_HASH, Fault.FINAL_NONEMPTY,
                Fault.FINAL_TRUNCATED, Fault.FINAL_IO, Fault.DELETE_IO)) {
            Fake storage = new Fake(fault);
            var report = verify(storage);
            boolean verificationFailed = fault == Fault.BAD_HEAD || fault == Fault.BAD_HASH;
            var expected = verificationFailed ? R2ImageVerification.Outcome.VERIFICATION_FAILED
                    : R2ImageVerification.Outcome.CLEANUP_NOT_CONFIRMED;

            check(report.outcome() == expected && !report.passed(), "Unverified result passed");
            check(report.finalListEmpty() == verificationFailed && storage.calls.getLast().equals("LIST"),
                    "Final LIST result was ignored");
            if (fault == Fault.DELETE_IO) {
                check(storage.keysFor("DELETE").equals(KEYS), "DELETE was retried or a later key was skipped");
                check(report.deletedKeys().equals(List.of(KEYS.getFirst(), KEYS.getLast())),
                        "Failed DELETE was recorded as confirmed");
            } else {
                deletesOnly(storage, report, KEYS);
            }
        }
    }

    // 合成入力の固定キーと検証値を同じ検証処理へ渡す。
    private static R2ImageVerification.Report verify(Fake storage) {
        Inputs input = inputs();
        return R2ImageVerification.verify(
                R2ImageVerification.BUCKET, R2ImageVerification.PREFIX, input.expected(), input.bytes(), storage);
    }

    // メモリ上の短い JPEG マーカー付き入力に対して独立に SHA-256 を計算する。
    private static Inputs inputs() {
        Map<String, byte[]> bytes = new HashMap<>();
        List<R2ImageVerification.Fixture> expected = new ArrayList<>();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (int index = 0; index < KEYS.size(); index++) {
                String key = KEYS.get(index);
                byte[] body = jpeg(index + 1);
                bytes.put(key, body);
                expected.add(new R2ImageVerification.Fixture(
                        key, HexFormat.of().formatHex(digest.digest(body)), body.length));
            }
        } catch (NoSuchAlgorithmException error) {
            throw new AssertionError(error);
        }
        return new Inputs(expected, bytes);
    }

    // 先頭と末尾に検証対象の JPEG シグネチャを持つ合成バイト列を作る。
    private static byte[] jpeg(int content) {
        return new byte[] {(byte) 0xff, (byte) 0xd8, (byte) content, (byte) 0xff, (byte) 0xd9};
    }

    // 削除要求と成功報告が同じ固定キー集合と一対一で対応することを確認する。
    private static void deletesOnly(Fake storage, R2ImageVerification.Report report, List<String> keys) {
        var attempts = storage.keysFor("DELETE");
        check(attempts.size() == keys.size() && Set.copyOf(attempts).equals(Set.copyOf(keys)), "Wrong DELETE scope");
        check(report.deletedKeys().size() == keys.size() && Set.copyOf(report.deletedKeys()).equals(Set.copyOf(keys)),
                "Wrong DELETE proof");
    }

    // JVM の assert オプションに依存せず、違反があれば実行を失敗させる。
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private record Inputs(List<R2ImageVerification.Fixture> expected, Map<String, byte[]> bytes) {
    }

    private enum Fault {
        NONE, START_NONEMPTY, START_TRUNCATED, START_IO, CONFLICT, UNKNOWN_COMMITTED, UNKNOWN_TIMEOUT,
        BAD_HEAD, BAD_HASH, FINAL_NONEMPTY, FINAL_TRUNCATED, FINAL_IO, DELETE_IO
    }

    /** メモリ上で指定した一種類の不正状態を再現し、操作順序を記録する。 */
    private static final class Fake implements R2ImageVerification.Storage {
        private final Fault fault;
        private final Map<String, byte[]> live = new HashMap<>();
        private final List<String> calls = new ArrayList<>();
        private int lists;

        private Fake(Fault fault) {
            this.fault = fault;
        }

        /** {@inheritDoc} */
        @Override
        public R2ImageVerification.Listing list() throws IOException {
            calls.add("LIST");
            boolean first = ++lists == 1;
            if (fault == (first ? Fault.START_IO : Fault.FINAL_IO)) {
                throw new IOException("Synthetic LIST failure");
            }
            int count = fault == (first ? Fault.START_NONEMPTY : Fault.FINAL_NONEMPTY) ? 1 : live.size();
            boolean truncated = fault == (first ? Fault.START_TRUNCATED : Fault.FINAL_TRUNCATED);
            return new R2ImageVerification.Listing(count, truncated);
        }

        /** {@inheritDoc} */
        @Override
        public R2ImageVerification.Put put(String key, byte[] bytes) throws IOException {
            observe("PUT", key);
            if (key.equals(KEYS.get(1))) {
                if (fault == Fault.CONFLICT) {
                    live.put(key, jpeg(42));
                    return R2ImageVerification.Put.CONFLICT;
                }
                if (fault == Fault.UNKNOWN_COMMITTED) {
                    live.put(key, bytes.clone());
                    return R2ImageVerification.Put.UNKNOWN;
                }
                if (fault == Fault.UNKNOWN_TIMEOUT) {
                    throw new SocketTimeoutException("Synthetic lost PUT response");
                }
            }
            live.put(key, bytes.clone());
            return R2ImageVerification.Put.CREATED;
        }

        /** {@inheritDoc} */
        @Override
        public R2ImageVerification.Head head(String key) {
            observe("HEAD", key);
            long length = live.get(key).length;
            return new R2ImageVerification.Head(fault == Fault.BAD_HEAD ? length + 1 : length, "image/jpeg");
        }

        /** {@inheritDoc} */
        @Override
        public byte[] get(String key, int maxBytes) {
            observe("GET", key);
            byte[] bytes = live.get(key);
            check(maxBytes == bytes.length + 1, "GET did not limit the body to pinned size plus one");
            return fault == Fault.BAD_HASH ? jpeg(42) : bytes.clone();
        }

        /** {@inheritDoc} */
        @Override
        public void delete(String key) throws IOException {
            observe("DELETE", key);
            if (fault == Fault.DELETE_IO && key.equals(KEYS.get(1))) {
                throw new IOException("Synthetic DELETE failure");
            }
            live.remove(key);
        }

        // 固定キーへの操作だけを許可し、実際の呼び出しを順番に記録する。
        private void observe(String operation, String key) {
            check(KEYS.contains(key), "Storage call escaped scope");
            calls.add(operation + " " + key);
        }

        // 指定操作が触ったキーを呼び出し順に返す。
        private List<String> keysFor(String operation) {
            String prefix = operation + " ";
            return calls.stream().filter(call -> call.startsWith(prefix))
                    .map(call -> call.substring(prefix.length()))
                    .toList();
        }
    }
}
