package io.github.tsndre.minijava.store.web.filter;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.http.HttpHeaders;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Holds a handler's status, headers and body until the handler is done, so that a request which
 * times out can still be answered with a clean 504. Once the request has timed out, everything the
 * handler writes is discarded. The real response is only touched by {@link #copyTo}.
 */
final class BufferedResponse extends HttpServletResponseWrapper {

    private final Object lock = new Object();
    private final HttpHeaders headers = new HttpHeaders();
    private final List<Cookie> cookies = new ArrayList<>();
    private final ByteArrayOutputStream body = new ByteArrayOutputStream();

    private int status = SC_OK;
    private Integer errorStatus;
    private String errorMessage;
    private String redirectLocation;
    private String contentType;
    private String characterEncoding;
    private long contentLength = -1;

    private ServletOutputStream outputStream;
    private PrintWriter writer;
    private boolean started;
    private boolean timedOut;

    BufferedResponse(HttpServletResponse response) {
        super(response);
    }

    /**
     * Marks the response as timed out, unless the handler has already started sending it.
     *
     * @return whether the response timed out; if not, the caller must wait for the handler
     */
    boolean timeOut() {
        synchronized (lock) {
            if (started) {
                return false;
            }
            timedOut = true;
            return true;
        }
    }

    void copyTo(HttpServletResponse response) throws IOException {
        synchronized (lock) {
            if (writer != null) {
                writer.flush();
            }
            for (Cookie cookie : cookies) {
                response.addCookie(cookie);
            }
            headers.forEach((name, values) -> values.forEach(value -> response.addHeader(name, value)));
            if (contentType != null) {
                response.setContentType(contentType);
            }
            if (characterEncoding != null) {
                response.setCharacterEncoding(characterEncoding);
            }
            if (redirectLocation != null) {
                response.sendRedirect(redirectLocation);
                return;
            }
            if (errorStatus != null) {
                if (errorMessage == null) {
                    response.sendError(errorStatus);
                } else {
                    response.sendError(errorStatus, errorMessage);
                }
                return;
            }
            response.setStatus(status);
            response.setContentLengthLong(contentLength >= 0 ? contentLength : body.size());
            body.writeTo(response.getOutputStream());
            response.flushBuffer();
        }
    }

    private void start() {
        synchronized (lock) {
            started = true;
        }
    }

    private void write(byte[] b, int off, int len) {
        synchronized (lock) {
            started = true;
            if (!timedOut) {
                body.write(b, off, len);
            }
        }
    }

    @Override
    public ServletOutputStream getOutputStream() {
        if (writer != null) {
            throw new IllegalStateException("getWriter() has already been called");
        }
        start();
        if (outputStream == null) {
            outputStream = new ServletOutputStream() {
                @Override
                public void write(int b) {
                    BufferedResponse.this.write(new byte[] {(byte) b}, 0, 1);
                }

                @Override
                public void write(byte[] b, int off, int len) {
                    BufferedResponse.this.write(b, off, len);
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setWriteListener(WriteListener listener) {
                    throw new UnsupportedOperationException();
                }
            };
        }
        return outputStream;
    }

    @Override
    public PrintWriter getWriter() {
        if (outputStream != null && writer == null) {
            throw new IllegalStateException("getOutputStream() has already been called");
        }
        if (writer == null) {
            Charset charset = characterEncoding == null ? StandardCharsets.ISO_8859_1 : Charset.forName(characterEncoding);
            ServletOutputStream out = getOutputStream();
            writer = new PrintWriter(new OutputStreamWriter(out, charset));
            outputStream = out;
        }
        return writer;
    }

    @Override
    public void flushBuffer() {
        start();
        if (writer != null) {
            writer.flush();
        }
    }

    @Override
    public void resetBuffer() {
        synchronized (lock) {
            body.reset();
        }
    }

    @Override
    public void reset() {
        synchronized (lock) {
            body.reset();
            headers.clear();
            cookies.clear();
            status = SC_OK;
            contentType = null;
            characterEncoding = null;
            contentLength = -1;
        }
    }

    @Override
    public boolean isCommitted() {
        synchronized (lock) {
            return errorStatus != null || redirectLocation != null;
        }
    }

    @Override
    public void setBufferSize(int size) {
        // Everything is buffered anyway.
    }

    @Override
    public int getBufferSize() {
        return Integer.MAX_VALUE;
    }

    @Override
    public void setStatus(int sc) {
        synchronized (lock) {
            status = sc;
        }
    }

    @Override
    public int getStatus() {
        synchronized (lock) {
            return errorStatus != null ? errorStatus : status;
        }
    }

    @Override
    public void sendError(int sc, String msg) {
        synchronized (lock) {
            started = true;
            errorStatus = sc;
            errorMessage = msg;
        }
    }

    @Override
    public void sendError(int sc) {
        sendError(sc, null);
    }

    @Override
    public void sendRedirect(String location) {
        synchronized (lock) {
            started = true;
            redirectLocation = location;
        }
    }

    @Override
    public void sendRedirect(String location, int sc, boolean clearBuffer) {
        sendRedirect(location);
    }

    @Override
    public void addCookie(Cookie cookie) {
        synchronized (lock) {
            cookies.add(cookie);
        }
    }

    @Override
    public boolean containsHeader(String name) {
        synchronized (lock) {
            return headers.containsHeader(name);
        }
    }

    @Override
    public String getHeader(String name) {
        synchronized (lock) {
            return headers.getFirst(name);
        }
    }

    @Override
    public Collection<String> getHeaders(String name) {
        synchronized (lock) {
            List<String> values = headers.get(name);
            return values == null ? List.of() : List.copyOf(values);
        }
    }

    @Override
    public Collection<String> getHeaderNames() {
        synchronized (lock) {
            return List.copyOf(headers.headerNames());
        }
    }

    @Override
    public void setHeader(String name, String value) {
        if (handledAsProperty(name, value)) {
            return;
        }
        synchronized (lock) {
            if (value == null) {
                headers.remove(name);
            } else {
                headers.set(name, value);
            }
        }
    }

    @Override
    public void addHeader(String name, String value) {
        if (value == null || handledAsProperty(name, value)) {
            return;
        }
        synchronized (lock) {
            headers.add(name, value);
        }
    }

    @Override
    public void setIntHeader(String name, int value) {
        setHeader(name, Integer.toString(value));
    }

    @Override
    public void addIntHeader(String name, int value) {
        addHeader(name, Integer.toString(value));
    }

    @Override
    public void setDateHeader(String name, long date) {
        setHeader(name, httpDate(date));
    }

    @Override
    public void addDateHeader(String name, long date) {
        addHeader(name, httpDate(date));
    }

    /** Content-Type and Content-Length are kept as properties, as the servlet API does. */
    private boolean handledAsProperty(String name, String value) {
        if (HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(name)) {
            setContentType(value);
            return true;
        }
        if (HttpHeaders.CONTENT_LENGTH.equalsIgnoreCase(name)) {
            setContentLengthLong(value == null ? -1 : Long.parseLong(value));
            return true;
        }
        return false;
    }

    private static String httpDate(long epochMillis) {
        return DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC));
    }

    @Override
    public void setContentType(String type) {
        synchronized (lock) {
            contentType = type;
            if (type != null) {
                int charset = type.toLowerCase().indexOf("charset=");
                if (charset >= 0) {
                    characterEncoding = type.substring(charset + "charset=".length()).trim();
                }
            }
        }
    }

    @Override
    public String getContentType() {
        synchronized (lock) {
            return contentType;
        }
    }

    @Override
    public void setCharacterEncoding(String charset) {
        synchronized (lock) {
            characterEncoding = charset;
        }
    }

    @Override
    public String getCharacterEncoding() {
        synchronized (lock) {
            return characterEncoding == null ? StandardCharsets.ISO_8859_1.name() : characterEncoding;
        }
    }

    @Override
    public void setContentLength(int len) {
        setContentLengthLong(len);
    }

    @Override
    public void setContentLengthLong(long len) {
        synchronized (lock) {
            contentLength = len;
        }
    }
}
