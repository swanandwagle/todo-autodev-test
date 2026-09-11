package com.example.todo.error;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProblemDetailFactoryTest {

    @Test
    void create_setsStatusTitleDetail() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/tasks/123");
        when(request.getAttribute(RequestIdFilter.ATTRIBUTE)).thenReturn("req-id-abc");

        ProblemDetail pd = ProblemDetailFactory.create(404, "TASK_NOT_FOUND",
                "Task not found", "No task with given id.", request);

        assertEquals(404, pd.getStatus());
        assertEquals("Task not found", pd.getTitle());
        assertEquals("No task with given id.", pd.getDetail());
    }

    @Test
    void create_typeUrlUsesKebabCode() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/tasks");
        when(request.getAttribute(RequestIdFilter.ATTRIBUTE)).thenReturn(null);

        ProblemDetail pd = ProblemDetailFactory.create(400, "INVALID_PAGINATION",
                "Bad request", "Page must be non-negative.", request);

        assertTrue(pd.getType().toString().endsWith("invalid-pagination"),
                "type URI should use kebab-case code, got: " + pd.getType());
    }

    @Test
    void create_instanceUsesRequestUri() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/tasks/abc");
        when(request.getAttribute(RequestIdFilter.ATTRIBUTE)).thenReturn("rid");

        ProblemDetail pd = ProblemDetailFactory.create(404, "NOT_FOUND", "Not found", "detail", request);

        assertEquals("/api/tasks/abc", pd.getInstance().toString());
    }

    @Test
    void create_requestIdPropertySet() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/tasks");
        when(request.getAttribute(RequestIdFilter.ATTRIBUTE)).thenReturn("test-request-id");

        ProblemDetail pd = ProblemDetailFactory.create(500, "INTERNAL_ERROR", "Error", "detail", request);

        assertEquals("test-request-id", pd.getProperties().get("requestId"));
    }

    @Test
    void create_nullRequestId_requestIdPropertyEmpty() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/tasks");
        when(request.getAttribute(RequestIdFilter.ATTRIBUTE)).thenReturn(null);

        ProblemDetail pd = ProblemDetailFactory.create(500, "INTERNAL_ERROR", "Error", "detail", request);

        assertEquals("", pd.getProperties().get("requestId"));
    }

    @Test
    void create_codePropertySet() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/tasks");
        when(request.getAttribute(RequestIdFilter.ATTRIBUTE)).thenReturn(null);

        ProblemDetail pd = ProblemDetailFactory.create(422, "VERSION_CONFLICT", "Conflict", "detail", request);

        assertEquals("VERSION_CONFLICT", pd.getProperties().get("code"));
    }

    @Test
    void create_timestampPropertyPresent() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/tasks");
        when(request.getAttribute(RequestIdFilter.ATTRIBUTE)).thenReturn(null);

        ProblemDetail pd = ProblemDetailFactory.create(400, "BAD", "Bad", "detail", request);

        assertNotNull(pd.getProperties().get("timestamp"));
    }
}
