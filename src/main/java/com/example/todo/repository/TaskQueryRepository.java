package com.example.todo.repository;

import com.example.todo.domain.Task;
import com.example.todo.dto.TaskListQuery;
import com.example.todo.service.TaskSortBuilder;
import com.example.todo.service.TaskSpecifications;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Custom repository implementation that applies JPA Criteria queries with
 * Postgres-specific ordering (NULLS LAST, CASE-based priority rank) and pagination.
 *
 * EXPLAIN ANALYZE spot-check (Testcontainers Postgres 16):
 *   - status + dueDate filter  → Bitmap Index Scan on ix_tasks_status_due_date
 *   - overdue filter           → Bitmap Index Scan on ix_tasks_open_due_date (partial index)
 *   Both confirmed via ListTasksIntegrationTest#explainAnalyze* helper.
 */
@Repository
public class TaskQueryRepository {

    @PersistenceContext
    private EntityManager em;

    public Page<Task> findAll(Specification<Task> spec, TaskListQuery query) {
        int page = query.getPage() != null ? query.getPage() : 0;
        int size = query.getSize() != null ? query.getSize() : 20;

        // Count query
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> countQ = cb.createQuery(Long.class);
        Root<Task> countRoot = countQ.from(Task.class);
        Predicate countPredicate = spec.toPredicate(countRoot, countQ, cb);
        countQ.select(cb.count(countRoot));
        if (countPredicate != null) countQ.where(countPredicate);
        long total = em.createQuery(countQ).getSingleResult();

        // Data query
        CriteriaQuery<Task> dataQ = cb.createQuery(Task.class);
        Root<Task> root = dataQ.from(Task.class);
        Predicate predicate = spec.toPredicate(root, dataQ, cb);
        dataQ.select(root);
        if (predicate != null) dataQ.where(predicate);

        List<Order> orders = TaskSortBuilder.buildCriteriaOrders(query, root, cb);
        dataQ.orderBy(orders);

        TypedQuery<Task> typedQuery = em.createQuery(dataQ);
        typedQuery.setFirstResult(page * size);
        typedQuery.setMaxResults(size);

        List<Task> content = typedQuery.getResultList();
        return new PageImpl<>(content, org.springframework.data.domain.PageRequest.of(page, size), total);
    }
}
