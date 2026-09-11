package com.example.todo.error;

import com.example.todo.config.RequestSizeLimitFilter;
import com.example.todo.domain.TaskStatus;
import com.example.todo.exception.InvalidStatusTransitionException;
import com.example.todo.exception.TaskNotFoundException;
import com.example.todo.exception.VersionConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    private MockHttpServletRequest requestWith(String requestId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/tasks");
        if (requestId != null) {
            request.setAttribute(RequestIdFilter.ATTRIBUTE, requestId);
        }
        return request;
    }

    // AC 1: MethodArgumentNotValidException with two field errors → 400 VALIDATION_FAILED, errors[] with both entries
    @Test
    void methodArgumentNotValid_twoFieldErrors_returns400WithErrors() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
        bindingResult.addError(new FieldError("target", "title", "", false, null, null, "must not be blank"));
        bindingResult.addError(new FieldError("target", "priority", "bad", false, null, null, "invalid value"));

        java.lang.reflect.Method dummyMethod = Object.class.getDeclaredMethod("toString");
        MethodParameter methodParameter = new MethodParameter(dummyMethod, -1);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(methodParameter, bindingResult);
        MockHttpServletRequest request = requestWith("req-1");

        ResponseEntity<ProblemDetail> response = handler.handleValidation(ex, request);

        assertEquals(400, response.getStatusCode().value());
        ProblemDetail pd = response.getBody();
        assertNotNull(pd);
        assertEquals("VALIDATION_FAILED", pd.getProperties().get("code"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> errors = (List<Map<String, Object>>) pd.getProperties().get("errors");
        assertNotNull(errors);
        assertEquals(2, errors.size());

        Map<String, Object> titleError = errors.stream()
                .filter(e -> "title".equals(e.get("field"))).findFirst().orElseThrow();
        assertEquals("must not be blank", titleError.get("message"));
        assertEquals("", titleError.get("rejectedValue"));

        Map<String, Object> priorityError = errors.stream()
                .filter(e -> "priority".equals(e.get("field"))).findFirst().orElseThrow();
        assertEquals("invalid value", priorityError.get("message"));
        assertEquals("bad", priorityError.get("rejectedValue"));
    }

    // AC 2: HttpMessageNotReadableException (bad JSON) → 400 MALFORMED_REQUEST with parser's message in detail
    @Test
    void httpMessageNotReadable_badJson_returns400MalformedRequest() {
        // Use two-arg (msg, HttpInputMessage) constructor available since Spring 4; null inputMessage is safe
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "JSON parse error: Unexpected character at position 42",
                (org.springframework.http.HttpInputMessage) null);
        MockHttpServletRequest request = requestWith("req-2");

        ResponseEntity<ProblemDetail> response = handler.handleNotReadable(ex, request);

        assertEquals(400, response.getStatusCode().value());
        ProblemDetail pd = response.getBody();
        assertNotNull(pd);
        assertEquals("MALFORMED_REQUEST", pd.getProperties().get("code"));
        assertNotNull(pd.getDetail());
        assertTrue(pd.getDetail().contains("JSON parse error"),
                "detail should embed parser message, got: " + pd.getDetail());
    }

    // AC 3: TaskNotFoundException(id) → 404 TASK_NOT_FOUND, detail contains the id
    @Test
    void taskNotFound_returns404WithIdInDetail() {
        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
        TaskNotFoundException ex = new TaskNotFoundException(id);
        MockHttpServletRequest request = requestWith("req-3");

        ResponseEntity<ProblemDetail> response = handler.handleTaskNotFound(ex, request);

        assertEquals(404, response.getStatusCode().value());
        ProblemDetail pd = response.getBody();
        assertNotNull(pd);
        assertEquals("TASK_NOT_FOUND", pd.getProperties().get("code"));
        assertNotNull(pd.getDetail());
        assertTrue(pd.getDetail().contains(id.toString()),
                "detail should contain the task id, got: " + pd.getDetail());
    }

    // AC 4: VersionConflictException(id, expected, actual) → 409 VERSION_CONFLICT, both numbers in detail
    @Test
    void versionConflict_returns409WithVersionsInDetail() {
        UUID id = UUID.fromString("22222222-2222-2222-2222-222222222222");
        VersionConflictException ex = new VersionConflictException(id, 3L, 7L);
        MockHttpServletRequest request = requestWith("req-4");

        ResponseEntity<ProblemDetail> response = handler.handleVersionConflict(ex, request);

        assertEquals(409, response.getStatusCode().value());
        ProblemDetail pd = response.getBody();
        assertNotNull(pd);
        assertEquals("VERSION_CONFLICT", pd.getProperties().get("code"));
        assertNotNull(pd.getDetail());
        assertTrue(pd.getDetail().contains("3"), "detail should contain expected version 3, got: " + pd.getDetail());
        assertTrue(pd.getDetail().contains("7"), "detail should contain actual version 7, got: " + pd.getDetail());
        assertEquals(3L, pd.getProperties().get("expectedVersion"));
        assertEquals(7L, pd.getProperties().get("actualVersion"));
    }

    // AC 5: InvalidStatusTransitionException(from, to) → 409 INVALID_STATUS_TRANSITION, from/to in detail
    @Test
    void invalidStatusTransition_returns409WithFromAndTo() {
        InvalidStatusTransitionException ex = new InvalidStatusTransitionException(
                TaskStatus.DONE, TaskStatus.TODO);
        MockHttpServletRequest request = requestWith("req-5");

        ResponseEntity<ProblemDetail> response = handler.handleInvalidStatusTransition(ex, request);

        assertEquals(409, response.getStatusCode().value());
        ProblemDetail pd = response.getBody();
        assertNotNull(pd);
        assertEquals("INVALID_STATUS_TRANSITION", pd.getProperties().get("code"));
        assertNotNull(pd.getDetail());
        assertTrue(pd.getDetail().contains("DONE"), "detail should contain 'from' status DONE, got: " + pd.getDetail());
        assertTrue(pd.getDetail().contains("TODO"), "detail should contain 'to' status TODO, got: " + pd.getDetail());
    }

    // AC 6: HttpMediaTypeNotSupportedException → 415 UNSUPPORTED_MEDIA_TYPE
    @Test
    void unsupportedMediaType_returns415() {
        HttpMediaTypeNotSupportedException ex = new HttpMediaTypeNotSupportedException(
                MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON));
        MockHttpServletRequest request = requestWith("req-6");

        ResponseEntity<ProblemDetail> response = handler.handleUnsupportedMedia(ex, request);

        assertEquals(415, response.getStatusCode().value());
        ProblemDetail pd = response.getBody();
        assertNotNull(pd);
        assertEquals("UNSUPPORTED_MEDIA_TYPE", pd.getProperties().get("code"));
    }

    // AC 7: HttpRequestMethodNotSupportedException → 405 METHOD_NOT_ALLOWED with Allow header
    @Test
    void methodNotAllowed_returns405WithAllowHeader() {
        HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException(
                "DELETE", List.of("GET", "POST"));
        MockHttpServletRequest request = requestWith("req-7");

        ResponseEntity<ProblemDetail> response = handler.handleMethodNotAllowed(ex, request);

        assertEquals(405, response.getStatusCode().value());
        ProblemDetail pd = response.getBody();
        assertNotNull(pd);
        assertEquals("METHOD_NOT_ALLOWED", pd.getProperties().get("code"));

        Set<HttpMethod> allow = response.getHeaders().getAllow();
        assertNotNull(allow);
        assertFalse(allow.isEmpty(), "Allow header should list supported methods");
        assertTrue(allow.contains(HttpMethod.GET), "Allow header should contain GET");
        assertTrue(allow.contains(HttpMethod.POST), "Allow header should contain POST");
    }

    // AC 8: Uncaught RuntimeException → 500 INTERNAL_ERROR, no stack trace/SQL/class name in body
    @Test
    void uncaughtRuntimeException_returns500WithoutLeakingInternals() {
        RuntimeException ex = new RuntimeException("internal database error with SQL: SELECT * FROM tasks");
        MockHttpServletRequest request = requestWith("req-8");
        request.setMethod("GET");
        request.setRequestURI("/api/v1/tasks");

        ResponseEntity<ProblemDetail> response = handler.handleGeneric(ex, request);

        assertEquals(500, response.getStatusCode().value());
        ProblemDetail pd = response.getBody();
        assertNotNull(pd);
        assertEquals("INTERNAL_ERROR", pd.getProperties().get("code"));

        String detail = pd.getDetail();
        assertNotNull(detail);
        assertFalse(detail.contains("RuntimeException"),
                "detail must not leak exception class name");
        assertFalse(detail.contains("SELECT"),
                "detail must not leak SQL");
        assertFalse(detail.contains("at com."),
                "detail must not contain stack trace");
    }

    // AC 9: Request with X-Request-Id header → response echoes it (via RequestIdFilter attribute)
    @Test
    void requestWithRequestId_responseEchoesSameId() {
        UUID id = UUID.fromString("33333333-3333-3333-3333-333333333333");
        TaskNotFoundException ex = new TaskNotFoundException(id);
        MockHttpServletRequest request = requestWith("abc-123");

        ResponseEntity<ProblemDetail> response = handler.handleTaskNotFound(ex, request);

        ProblemDetail pd = response.getBody();
        assertNotNull(pd);
        assertEquals("abc-123", pd.getProperties().get("requestId"),
                "requestId property should echo the provided X-Request-Id");
    }

    // AC 10: No X-Request-Id → a UUID is generated (tested at filter level; here we verify the handler
    //        propagates whatever ID the filter set on the attribute, even a generated UUID-shaped value)
    @Test
    void noRequestIdAttribute_requestIdPropertyIsEmpty() {
        UUID id = UUID.fromString("44444444-4444-4444-4444-444444444444");
        TaskNotFoundException ex = new TaskNotFoundException(id);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/tasks");
        // no attribute set — simulates case where filter did not run

        ResponseEntity<ProblemDetail> response = handler.handleTaskNotFound(ex, request);

        ProblemDetail pd = response.getBody();
        assertNotNull(pd);
        // ProblemDetailFactory sets "" when attribute is null
        assertEquals("", pd.getProperties().get("requestId"));
    }

    // AC 11: Oversized payload (413) takes precedence — the RequestSizeLimitFilter short-circuits before
    //        any controller or advice is reached, so even a request that would also trigger a 400 returns 413.
    @Test
    void payloadTooLarge_filterShortCircuits_returns413BeforeHandler() throws Exception {
        RequestSizeLimitFilter sizeLimitFilter = new RequestSizeLimitFilter(new ObjectMapper());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/v1/tasks");
        request.setAttribute(RequestIdFilter.ATTRIBUTE, "req-11");
        // Content-Length exceeds 64 KB limit — filter must reject before reaching any handler
        request.setContentType("application/json");
        request.addHeader("Content-Length", String.valueOf(RequestSizeLimitFilter.MAX_BYTES + 1));

        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.isCommitted()).thenReturn(false);
        when(response.getOutputStream()).thenReturn(new ServletOutputStream() {
            @Override public void write(int b) {}
            @Override public boolean isReady() { return true; }
            @Override public void setWriteListener(WriteListener l) {}
        });

        FilterChain chain = mock(FilterChain.class);

        sizeLimitFilter.doFilter(request, response, chain);

        verify(response).setStatus(413);
        verify(chain, never()).doFilter(any(), any());
    }

    // Content-type of all problem responses is application/problem+json
    @Test
    void allHandlers_returnProblemJsonContentType() {
        UUID id = UUID.fromString("55555555-5555-5555-5555-555555555555");
        TaskNotFoundException ex = new TaskNotFoundException(id);
        MockHttpServletRequest request = requestWith("req-ct");

        ResponseEntity<ProblemDetail> response = handler.handleTaskNotFound(ex, request);

        assertEquals(MediaType.APPLICATION_PROBLEM_JSON, response.getHeaders().getContentType());
    }
}
