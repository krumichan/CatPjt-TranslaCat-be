package jp.co.translacat.domain.documenttranslation.format;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OfficeOutputLimitTest {
    @Test
    void refusesAnyAttemptToWriteBeyondTheLimit() throws IOException {
        // 준비: 작은 상한을 사용해 실제 바이트 쓰기 경계를 확인한다.
        var destination = new ByteArrayOutputStream();
        try (var output = new OfficeParts.BoundedZipOutput(destination, 4)) {
            // 실행: 배열·단일 바이트가 초과하기 전에 거부한다.
            output.write(new byte[]{1, 2});
            assertThrows(IOException.class, () -> output.write(new byte[]{3, 4, 5}));
            output.write(new byte[]{3, 4});
            assertThrows(IOException.class, () -> output.write(5));
        }

        // 검증: 뒤따른 올바른 쓰기까지 포함해 정확한 상한만 기록된다.
        assertArrayEquals(new byte[]{1, 2, 3, 4}, destination.toByteArray());
    }
}
