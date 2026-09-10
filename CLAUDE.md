# todo-autodev-test

A Spring Boot REST API for managing todo tasks, backed by PostgreSQL.

## Stack

- Java 21
- Spring Boot 4.1.x
- Spring Data JPA / Hibernate 7
- Flyway (sole schema owner — `spring.jpa.hibernate.ddl-auto` must be `validate` or `none`)
- PostgreSQL 16+
- JUnit 5 + Testcontainers (integration tests against a real Postgres container)
- springdoc-openapi (OpenAPI/Swagger annotations required on all endpoints)
- jackson-databind-nullable (for tri-state `JsonNullable<T>` fields in PATCH DTOs)

## Project structure

```
src/
  main/
    java/com/example/todo/
      domain/           # Task entity, TaskStatus, TaskPriority enums, TaskRepository
      controller/       # TaskController (@RestController)
      service/          # TaskService, TaskStatusTransitionValidator
      dto/              # CreateTaskRequest, UpdateTaskRequest, PatchTaskRequest, TaskResponse, PageResponse
      validation/       # TagNormalizer, @ValidTag constraint, DateRangeValidator
      exception/        # TaskNotFoundException, VersionConflictException, InvalidStatusTransitionException, etc.
      error/            # GlobalExceptionHandler (@RestControllerAdvice), RequestIdFilter, ProblemDetailFactory
    resources/
      db/migration/     # Flyway SQL migrations (V1__create_tasks.sql, ...)
  test/
    java/com/example/todo/
      AbstractIntegrationTest.java   # shared Testcontainers base class
      controller/                    # per-endpoint MockMvc + Testcontainers tests
      validation/                    # unit tests for TagNormalizer, @ValidTag, DateRangeValidator
      error/                         # unit/WebMvcTest tests for GlobalExceptionHandler
```

## API base path

`/api/v1/tasks`

## Build & verify

```
./mvnw verify
```

This runs compilation, unit tests, and all Testcontainers-backed integration tests. The build must be fully green before opening a PR.

## Coding rules

- Every new class must have corresponding tests. Integration tests use MockMvc + Testcontainers Postgres (never H2 or mocked repositories for persistence tests). Unit tests are plain JUnit 5 (no Spring context) for validators, normalizers, and the exception handler.
- Flyway is the sole schema owner. Never use `ddl-auto=create` or `ddl-auto=update`.
- All endpoints must have OpenAPI (`springdoc`) annotations.
- Jackson must be configured globally with `FAIL_ON_UNKNOWN_PROPERTIES = true`. Unknown fields in any request body are a `400 VALIDATION_FAILED`, not a silent ignore.
- Enum deserialization is case-sensitive. `"todo"` for a `TaskStatus` field must fail with `400 MALFORMED_REQUEST`.
- All error responses use RFC 9457 `application/problem+json` with a `code` extension field. See TODO-9 for the full error catalogue and shape.
- Tag normalization (trim → lowercase → dedupe preserving first-seen order) is handled by `TagNormalizer` and must happen before `@ValidTag` validation. Never duplicate this logic.
- The status state machine (TODO → IN_PROGRESS → DONE etc.) is enforced by `TaskStatusTransitionValidator`. Reuse it; never duplicate the transition table.
- `completedAt` is set to `now()` when transitioning into DONE, cleared to null when transitioning out of DONE. This is a service-layer side effect, not a client-settable field.
- `overdue` is a derived read field: `dueDate < today(UTC) AND status IN (TODO, IN_PROGRESS)`. Never persist it.
- Request body size limit: 64 KB. Exceeded → `413 PAYLOAD_TOO_LARGE`.
- Request correlation: every response (success or error) must echo `X-Request-Id` (generate a UUID if the client didn't send one). Include it in every `ProblemDetail` as `requestId`.
- Do not add external dependencies beyond those listed in the stack above without flagging it in the PR description.

## Testing rules

- Integration tests extend `AbstractIntegrationTest`, which provides a singleton Testcontainers `PostgreSQLContainer` and a full Spring context with Flyway migrations applied.
- Truncate the `tasks` table (or use rolled-back transactions) between tests for isolation.
- Concurrency tests (optimistic-lock scenario in TODO-11) must use real threads against the running app — not mocked — and be run at least 5 times locally to confirm they are not flaky before merging.

## Error response shape

```json
{
  "type": "https://api.example.com/problems/validation-failed",
  "title": "Validation failed",
  "status": 400,
  "detail": "One or more request fields are invalid.",
  "instance": "/api/v1/tasks",
  "code": "VALIDATION_FAILED",
  "timestamp": "2026-09-09T10:15:30.123Z",
  "requestId": "0b6a1c2f-...",
  "errors": [
    { "field": "title", "rejectedValue": "", "message": "must not be blank" }
  ]
}
```

`errors[]` is present only for `VALIDATION_FAILED`.
