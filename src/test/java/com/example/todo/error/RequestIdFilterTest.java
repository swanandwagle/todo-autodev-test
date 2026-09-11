package com.example.todo.error;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RequestIdFilterTest {

    private RequestIdFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RequestIdFilter();
    }

    @Test
    void noIncomingHeader_generatesUUID() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getHeader(RequestIdFilter.HEADER)).thenReturn(null);

        filter.doFilterInternal(request, response, chain);

        ArgumentCaptor<String> attrCaptor = ArgumentCaptor.forClass(String.class);
        verify(request).setAttribute(eq(RequestIdFilter.ATTRIBUTE), attrCaptor.capture());
        String generatedId = attrCaptor.getValue();
        assertNotNull(generatedId);
        // should be a valid UUID
        assertDoesNotThrow(() -> java.util.UUID.fromString(generatedId));
    }

    @Test
    void incomingHeaderPresent_usedAsRequestId() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getHeader(RequestIdFilter.HEADER)).thenReturn("client-provided-id");

        filter.doFilterInternal(request, response, chain);

        verify(request).setAttribute(RequestIdFilter.ATTRIBUTE, "client-provided-id");
        verify(response).setHeader(RequestIdFilter.HEADER, "client-provided-id");
    }

    @Test
    void blankHeader_generatesUUID() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getHeader(RequestIdFilter.HEADER)).thenReturn("   ");

        filter.doFilterInternal(request, response, chain);

        ArgumentCaptor<String> attrCaptor = ArgumentCaptor.forClass(String.class);
        verify(request).setAttribute(eq(RequestIdFilter.ATTRIBUTE), attrCaptor.capture());
        String generatedId = attrCaptor.getValue();
        assertDoesNotThrow(() -> java.util.UUID.fromString(generatedId));
    }

    @Test
    void requestIdSetAsResponseHeader() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getHeader(RequestIdFilter.HEADER)).thenReturn("my-request-id");

        filter.doFilterInternal(request, response, chain);

        verify(response).setHeader(RequestIdFilter.HEADER, "my-request-id");
    }

    @Test
    void chainContinuesAfterFilter() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getHeader(RequestIdFilter.HEADER)).thenReturn("id-123");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
