package com.example.todo.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RequestSizeLimitFilterTest {

    private RequestSizeLimitFilter filter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        filter = new RequestSizeLimitFilter(objectMapper);
    }

    private static ServletInputStream toServletInputStream(byte[] data) {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        return new ServletInputStream() {
            @Override public int read() { return bais.read(); }
            @Override public boolean isFinished() { return bais.available() == 0; }
            @Override public boolean isReady() { return true; }
            @Override public void setReadListener(ReadListener l) {}
        };
    }

    @Test
    void contentLengthBelowLimit_chainContinues() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getContentLengthLong()).thenReturn(100L);
        when(request.getInputStream()).thenReturn(toServletInputStream(new byte[100]));

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(any(), eq(response));
        verify(response, never()).setStatus(413);
    }

    @Test
    void contentLengthExceedsLimit_returns413() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getContentLengthLong()).thenReturn(RequestSizeLimitFilter.MAX_BYTES + 1);
        when(request.getAttribute(any())).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/api/tasks");
        when(response.isCommitted()).thenReturn(false);
        ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(new jakarta.servlet.ServletOutputStream() {
            @Override public void write(int b) { responseBody.write(b); }
            @Override public boolean isReady() { return true; }
            @Override public void setWriteListener(jakarta.servlet.WriteListener l) {}
        });

        filter.doFilterInternal(request, response, chain);

        verify(response).setStatus(413);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void noContentLength_chainContinues() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getContentLengthLong()).thenReturn(-1L);
        when(request.getHeader("Content-Length")).thenReturn(null);
        when(request.getInputStream()).thenReturn(toServletInputStream(new byte[0]));

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(any(), eq(response));
    }

    @Test
    void contentLengthExactlyAtLimit_chainContinues() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getContentLengthLong()).thenReturn(RequestSizeLimitFilter.MAX_BYTES);
        when(request.getInputStream()).thenReturn(toServletInputStream(new byte[(int) RequestSizeLimitFilter.MAX_BYTES]));

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(any(), eq(response));
        verify(response, never()).setStatus(413);
    }

    @Test
    void maxBytesConstantIs65536() {
        assertEquals(65_536L, RequestSizeLimitFilter.MAX_BYTES);
    }
}
