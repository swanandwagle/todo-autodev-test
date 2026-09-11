package com.example.todo.config;

import com.example.todo.error.ProblemDetailFactory;
import com.example.todo.error.RequestIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    public static final long MAX_BYTES = 65_536L;
    private final ObjectMapper objectMapper;

    public RequestSizeLimitFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        long contentLength = request.getContentLengthLong();
        if (contentLength < 0) {
            String clHeader = request.getHeader("Content-Length");
            if (clHeader != null) {
                try { contentLength = Long.parseLong(clHeader.trim()); } catch (NumberFormatException ignored) {}
            }
        }
        if (contentLength > MAX_BYTES) {
            rejectTooLarge(request, response);
            return;
        }
        chain.doFilter(new SizeLimitedRequestWrapper(request, MAX_BYTES, () -> rejectTooLarge(request, response)), response);
    }

    private void rejectTooLarge(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (response.isCommitted()) return;
        ProblemDetail pd = ProblemDetailFactory.create(413, "PAYLOAD_TOO_LARGE",
                "Payload too large",
                "Request body must not exceed 64 KB.", request);
        response.setStatus(413);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        String requestId = (String) request.getAttribute(RequestIdFilter.ATTRIBUTE);
        if (requestId != null) {
            response.setHeader(RequestIdFilter.HEADER, requestId);
        }
        objectMapper.writeValue(response.getOutputStream(), pd);
    }

    @FunctionalInterface
    interface TooLargeHandler {
        void handle() throws IOException;
    }

    private static class SizeLimitedRequestWrapper extends HttpServletRequestWrapper {

        private final long maxBytes;
        private final TooLargeHandler handler;
        private SizeLimitedInputStream limitedStream;

        SizeLimitedRequestWrapper(HttpServletRequest request, long maxBytes, TooLargeHandler handler) {
            super(request);
            this.maxBytes = maxBytes;
            this.handler = handler;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (limitedStream == null) {
                limitedStream = new SizeLimitedInputStream(super.getInputStream(), maxBytes, handler);
            }
            return limitedStream;
        }
    }

    private static class SizeLimitedInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private final long maxBytes;
        private final TooLargeHandler handler;
        private long bytesRead = 0;

        SizeLimitedInputStream(ServletInputStream delegate, long maxBytes, TooLargeHandler handler) {
            this.delegate = delegate;
            this.maxBytes = maxBytes;
            this.handler = handler;
        }

        @Override
        public int read() throws IOException {
            int b = delegate.read();
            if (b != -1 && ++bytesRead > maxBytes) {
                handler.handle();
                return -1;
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = delegate.read(b, off, len);
            if (n > 0) {
                bytesRead += n;
                if (bytesRead > maxBytes) {
                    handler.handle();
                    return -1;
                }
            }
            return n;
        }

        @Override public boolean isFinished() { return delegate.isFinished(); }
        @Override public boolean isReady() { return delegate.isReady(); }
        @Override public void setReadListener(ReadListener l) { delegate.setReadListener(l); }
    }
}
