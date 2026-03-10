package jp.co.translacat.global.logging;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.util.StreamUtils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

public class CustomCachingRequestWrapper extends HttpServletRequestWrapper {
    private final byte[] cachedBody;

    public CustomCachingRequestWrapper(HttpServletRequest request) throws IOException {
        super(request);

        // 미리 request body를 읽어 저장.
        this.cachedBody = StreamUtils.copyToByteArray(request.getInputStream());
    }

    @Override
    public ServletInputStream getInputStream() {
        return new CachedServletInputStream(this.cachedBody);
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(this.cachedBody)));
    }

    public byte[] getContentAsByteArray() {
        return this.cachedBody;
    }

    // 내부 클래스로 InputStream 재구현
    private static class CachedServletInputStream extends ServletInputStream {
        private final ByteArrayInputStream buffer;

        public CachedServletInputStream(byte[] contents) {
            this.buffer = new ByteArrayInputStream(contents);
        }

        @Override
        public int read() { return buffer.read(); }
        @Override
        public boolean isFinished() { return buffer.available() == 0; }
        @Override
        public boolean isReady() { return true; }
        @Override
        public void setReadListener(ReadListener listener) { }
    }
}
