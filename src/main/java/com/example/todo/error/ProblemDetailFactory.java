package com.example.todo.error;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.time.Instant;

public class ProblemDetailFactory {

    private static final String BASE_TYPE = "https://api.example.com/problems/";

    private ProblemDetailFactory() {}

    public static ProblemDetail create(int status, String code, String title, String detail,
                                       HttpServletRequest request) {
        ProblemDetail pd = ProblemDetail.forStatus(status);
        pd.setType(URI.create(BASE_TYPE + toKebab(code)));
        pd.setTitle(title);
        pd.setDetail(detail);
        pd.setInstance(URI.create(request.getRequestURI()));
        pd.setProperty("code", code);
        pd.setProperty("timestamp", Instant.now().toString());
        Object requestId = request.getAttribute(RequestIdFilter.ATTRIBUTE);
        pd.setProperty("requestId", requestId != null ? requestId.toString() : "");
        return pd;
    }

    private static String toKebab(String code) {
        return code.toLowerCase().replace('_', '-');
    }
}
