package io.github.tsndre.minijava.store.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Caps the size of every request body read through the request. Reads past the limit fail, so JSON
 * decoding stops early instead of buffering arbitrarily large payloads. Multipart uploads are
 * parsed by the server itself and limited by its multipart configuration instead.
 */
public class BodySizeLimitFilter extends OncePerRequestFilter {

    private final long limit;

    public BodySizeLimitFilter(long limit) {
        this.limit = limit;
    }

    public static class BodyTooLargeException extends IOException {

        BodyTooLargeException() {
            super("http: request body too large");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        chain.doFilter(new LimitedRequest(request, limit), response);
    }

    private static final class LimitedRequest extends HttpServletRequestWrapper {

        private final long limit;
        private LimitedInputStream stream;

        LimitedRequest(HttpServletRequest request, long limit) {
            super(request);
            this.limit = limit;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (stream == null) {
                stream = new LimitedInputStream(super.getInputStream(), limit);
            }
            return stream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String encoding = getCharacterEncoding();
            Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }

    private static final class LimitedInputStream extends ServletInputStream {

        private final ServletInputStream in;
        private long remaining;

        LimitedInputStream(ServletInputStream in, long limit) {
            this.in = in;
            this.remaining = limit;
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            int n = read(one, 0, 1);
            return n < 0 ? -1 : one[0] & 0xff;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (len == 0) {
                return 0;
            }
            // Read one byte past the limit, so a body of exactly the limit still succeeds.
            int n = in.read(b, off, (int) Math.min(len, remaining + 1));
            if (n < 0) {
                return n;
            }
            if (n > remaining) {
                remaining = 0;
                throw new BodyTooLargeException();
            }
            remaining -= n;
            return n;
        }

        @Override
        public boolean isFinished() {
            return in.isFinished();
        }

        @Override
        public boolean isReady() {
            return in.isReady();
        }

        @Override
        public void setReadListener(ReadListener listener) {
            in.setReadListener(listener);
        }
    }
}
