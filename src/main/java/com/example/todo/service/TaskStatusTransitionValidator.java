package com.example.todo.service;

import com.example.todo.domain.TaskStatus;
import com.example.todo.exception.InvalidStatusTransitionException;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class TaskStatusTransitionValidator {

    private static final Map<TaskStatus, Set<TaskStatus>> ALLOWED = Map.of(
            TaskStatus.TODO,        EnumSet.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.DONE, TaskStatus.CANCELLED),
            TaskStatus.IN_PROGRESS, EnumSet.of(TaskStatus.IN_PROGRESS, TaskStatus.TODO, TaskStatus.DONE, TaskStatus.CANCELLED),
            TaskStatus.DONE,        EnumSet.of(TaskStatus.DONE, TaskStatus.TODO, TaskStatus.IN_PROGRESS),
            TaskStatus.CANCELLED,   EnumSet.of(TaskStatus.CANCELLED)
    );

    public void validate(TaskStatus from, TaskStatus to) {
        Set<TaskStatus> allowed = ALLOWED.getOrDefault(from, EnumSet.noneOf(TaskStatus.class));
        if (!allowed.contains(to)) {
            throw new InvalidStatusTransitionException(from, to);
        }
    }
}
