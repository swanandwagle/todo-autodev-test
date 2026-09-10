package com.example.todo.service;

import com.example.todo.domain.Task;
import com.example.todo.dto.TaskListQuery;
import jakarta.persistence.criteria.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds Pageable and JPA Criteria Order list from TaskListQuery.
 *
 * Priority ordering maps enum names to severity rank:
 *   URGENT=1, HIGH=2, MEDIUM=3, LOW=4  (asc = lowest rank first = URGENT first)
 *
 * dueDate NULLS LAST is achieved via a CASE expression:
 *   CASE WHEN dueDate IS NULL THEN 1 ELSE 0 END ASC, dueDate ASC/DESC
 */
public final class TaskSortBuilder {

    private TaskSortBuilder() {}

    public static Pageable buildPageable(TaskListQuery q) {
        int page = q.getPage() != null ? q.getPage() : 0;
        int size = q.getSize() != null ? q.getSize() : 20;
        return PageRequest.of(page, size);
    }

    /**
     * Builds JPA Criteria Orders. Called by TaskQueryRepository.
     */
    public static List<Order> buildCriteriaOrders(TaskListQuery q,
                                                   Root<Task> root,
                                                   CriteriaBuilder cb) {
        List<Order> orders = new ArrayList<>();
        String field = q.getSortField();
        boolean desc = "desc".equalsIgnoreCase(q.getSortDir());

        if (field == null) {
            // Default: dueDate ASC NULLS LAST, then createdAt DESC
            addDueDateOrder(orders, root, cb, false);
            orders.add(cb.desc(root.get("createdAt")));
        } else {
            switch (field) {
                case "dueDate" -> addDueDateOrder(orders, root, cb, desc);
                case "priority" -> addPriorityOrder(orders, root, cb, desc);
                default -> orders.add(desc
                        ? cb.desc(root.get(field))
                        : cb.asc(root.get(field)));
            }
        }
        return orders;
    }

    /**
     * Adds dueDate ordering with NULLS LAST via a CASE expression:
     *   CASE WHEN due_date IS NULL THEN 1 ELSE 0 END ASC, due_date ASC|DESC
     */
    private static void addDueDateOrder(List<Order> orders, Root<Task> root,
                                         CriteriaBuilder cb, boolean desc) {
        Expression<Integer> nullFlag = cb.selectCase()
                .<Integer>when(root.get("dueDate").isNull(), 1)
                .otherwise(0)
                .as(Integer.class);
        orders.add(cb.asc(nullFlag)); // nulls always go last
        orders.add(desc ? cb.desc(root.get("dueDate")) : cb.asc(root.get("dueDate")));
    }

    /**
     * Adds priority ordering by severity rank (not alphabetical):
     *   URGENT=1, HIGH=2, MEDIUM=3, LOW=4
     *   desc=true  → 4,3,2,1 = LOW first (rank desc)... actually sort desc on priority
     *   means "highest priority first" = URGENT,HIGH,MEDIUM,LOW = rank ASC.
     */
    private static void addPriorityOrder(List<Order> orders, Root<Task> root,
                                          CriteriaBuilder cb, boolean desc) {
        Expression<Integer> priorityRank = cb.selectCase()
                .when(cb.equal(root.get("priority").as(String.class), "URGENT"), 1)
                .when(cb.equal(root.get("priority").as(String.class), "HIGH"), 2)
                .when(cb.equal(root.get("priority").as(String.class), "MEDIUM"), 3)
                .otherwise(4)
                .as(Integer.class);
        // sort=priority,desc means URGENT first → rank ascending
        // sort=priority,asc means LOW first → rank descending
        orders.add(desc ? cb.asc(priorityRank) : cb.desc(priorityRank));
    }
}
