package io.github.tsndre.minijava.store.web.filter;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;

/** Passes the response through and keeps a copy of its body. */
public final class RecordingResponse extends HttpServletResponseWrapper {

    private final ByteArrayOutputStream copy = new ByteArrayOutputStream();
    private ServletOutputStream outputStream;
    private PrintWriter writer;

    RecordingResponse(HttpServletResponse response) {
        super(response);
    }

    /** The body written so far. */
    public byte[] body() {
        if (writer != null) {
            writer.flush();
        }
        return copy.toByteArray();
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        if (outputStream == null) {
            ServletOutputStream out = super.getOutputStream();
            outputStream = new ServletOutputStream() {
                @Override
                public void write(int b) throws IOException {
                    copy.write(b);
                    out.write(b);
                }

                @Override
                public void write(byte[] b, int off, int len) throws IOException {
                    copy.write(b, off, len);
                    out.write(b, off, len);
                }

                @Override
                public void flush() throws IOException {
                    out.flush();
                }

                @Override
                public boolean isReady() {
                    return out.isReady();
                }

                @Override
                public void setWriteListener(WriteListener listener) {
                    out.setWriteListener(listener);
                }
            };
        }
        return outputStream;
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        if (writer == null) {
            writer = new PrintWriter(new OutputStreamWriter(getOutputStream(), Charset.forName(getCharacterEncoding())));
        }
        return writer;
    }

    @Override
    public void flushBuffer() throws IOException {
        if (writer != null) {
            writer.flush();
        }
        super.flushBuffer();
    }
}
