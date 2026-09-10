package com.example.todo.service;

import com.example.todo.domain.Task;
import com.example.todo.domain.TaskPriority;
import com.example.todo.domain.TaskStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * One static factory method per filter criterion. Compose with Specification.and().
 *
 * Tags containment uses Postgres @> / && array operators registered as custom Hibernate
 * functions in PostgresFunctionContributor:
 *   array_contains_all(col, literal) => col @> literal::text[]   — uses ix_tasks_tags_gin
 *   array_overlaps_any(col, literal) => col && literal::text[]   — uses ix_tasks_tags_gin
 *
 * Full-text search uses:
 *   ts_match(vector, query)          => vector @@ plainto_tsquery('simple', query)
 *                                       — uses ix_tasks_search_gin
 *
 * EXPLAIN ANALYZE spot-check (run during integration tests via JDBC on the Testcontainers PG):
 *   - status + dueDate filter: uses ix_tasks_status_due_date (Bitmap Index Scan)
 *   - overdue filter: uses ix_tasks_open_due_date partial index
 *   Both confirmed as Bitmap Index Scan or Index Scan in test notes on ListTasksIntegrationTest.
 */
public final class TaskSpecifications {

    private TaskSpecifications() {}

    public static Specification<Task> hasStatuses(List<TaskStatus> statuses) {
        return (root, query, cb) -> root.get("status").in(statuses);
    }

    public static Specification<Task> hasPriorities(List<TaskPriority> priorities) {
        return (root, query, cb) -> root.get("priority").in(priorities);
    }

    public static Specification<Task> dueDateFrom(LocalDate from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("dueDate"), from);
    }

    public static Specification<Task> dueDateTo(LocalDate to) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("dueDate"), to);
    }

    public static Specification<Task> hasDueDate(boolean present) {
        return (root, query, cb) -> present
                ? root.get("dueDate").isNotNull()
                : root.get("dueDate").isNull();
    }

    /**
     * dueDate < today(UTC) AND status IN (TODO, IN_PROGRESS).
     * Leverages ix_tasks_open_due_date partial index (WHERE status IN ('TODO','IN_PROGRESS')).
     */
    public static Specification<Task> isOverdue(LocalDate today) {
        return (root, query, cb) -> cb.and(
                root.get("status").in(List.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS)),
                cb.lessThan(root.get("dueDate"), today)
        );
    }

    /**
     * tags @> ARRAY[...] — tasks containing ALL given tags.
     * Uses ix_tasks_tags_gin GIN index.
     */
    public static Specification<Task> tagsContainAll(List<String> tags) {
        String arrayLiteral = toPostgresArrayLiteral(tags);
        return (root, query, cb) -> cb.isTrue(
                cb.function("array_contains_all", Boolean.class,
                        root.get("tags"), cb.literal(arrayLiteral))
        );
    }

    /**
     * tags && ARRAY[...] — tasks containing ANY of the given tags.
     * Uses ix_tasks_tags_gin GIN index.
     */
    public static Specification<Task> tagsOverlapAny(List<String> tags) {
        String arrayLiteral = toPostgresArrayLiteral(tags);
        return (root, query, cb) -> cb.isTrue(
                cb.function("array_overlaps_any", Boolean.class,
                        root.get("tags"), cb.literal(arrayLiteral))
        );
    }

    /**
     * search_vector @@ plainto_tsquery('simple', q).
     * Uses ix_tasks_search_gin GIN index.
     */
    public static Specification<Task> fullTextSearch(String searchTerm) {
        return (root, query, cb) -> cb.isTrue(
                cb.function("ts_match", Boolean.class,
                        root.get("searchVector"), cb.literal(searchTerm))
        );
    }

    public static Specification<Task> updatedSince(OffsetDateTime since) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("updatedAt"), since);
    }

    private static String toPostgresArrayLiteral(List<String> tags) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < tags.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(tags.get(i).replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        }
        sb.append('}');
        return sb.toString();
    }
}
