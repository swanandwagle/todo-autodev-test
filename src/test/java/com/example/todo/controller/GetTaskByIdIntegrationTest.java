package com.example.todo.controller;

import com.example.todo.AbstractIntegrationTest;
import com.example.todo.domain.TaskPriority;
import com.example.todo.domain.TaskStatus;
import com.example.todo.dto.CreateTaskRequest;
import com.example.todo.dto.TaskResponse;
import com.example.todo.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GetTaskByIdIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    WebApplicationContext wac;

    @Autowired
    TaskService taskService;

    MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
    }

    // AC1: existing task → 200 with all fields matching
    @Test
    void getById_existingTask_returns200WithAllFields() throws Exception {
        CreateTaskRequest req = new CreateTaskRequest();
        req.setTitle("Buy groceries");
        req.setDescription("Milk and eggs");
        req.setStatus(TaskStatus.IN_PROGRESS);
        req.setPriority(TaskPriority.HIGH);
        req.setDueDate(LocalDate.of(2027, 1, 15));
        req.setTags(List.of("shopping", "urgent"));
        TaskResponse created = taskService.create(req);

        mockMvc.perform(get("/api/v1/tasks/{id}", created.getId()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(created.getId().toString()))
                .andExpect(jsonPath("$.title").value("Buy groceries"))
                .andExpect(jsonPath("$.description").value("Milk and eggs"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.dueDate").value("2027-01-15"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.overdue").value(false));
    }

    // AC2: dueDate in past, status=TODO → overdue=true
    @Test
    void getById_pastDueDateAndTodo_overdueTrue() throws Exception {
        CreateTaskRequest req = new CreateTaskRequest();
        req.setTitle("Overdue task");
        req.setStatus(TaskStatus.TODO);
        req.setDueDate(LocalDate.of(2020, 1, 1));
        TaskResponse created = taskService.create(req);

        mockMvc.perform(get("/api/v1/tasks/{id}", created.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overdue").value(true));
    }

    // AC3: dueDate in past, status=DONE → overdue=false
    @Test
    void getById_pastDueDateAndDone_overdueFalse() throws Exception {
        CreateTaskRequest req = new CreateTaskRequest();
        req.setTitle("Done task");
        req.setStatus(TaskStatus.DONE);
        req.setDueDate(LocalDate.of(2020, 1, 1));
        TaskResponse created = taskService.create(req);

        mockMvc.perform(get("/api/v1/tasks/{id}", created.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overdue").value(false));
    }

    // AC4: dueDate=null → overdue=false
    @Test
    void getById_noDueDate_overdueFalse() throws Exception {
        CreateTaskRequest req = new CreateTaskRequest();
        req.setTitle("No due date");
        TaskResponse created = taskService.create(req);

        mockMvc.perform(get("/api/v1/tasks/{id}", created.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overdue").value(false));
    }

    // AC5: valid UUID that matches no row → 404 TASK_NOT_FOUND with id in detail
    @Test
    void getById_unknownId_returns404TaskNotFound() throws Exception {
        UUID missingId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/tasks/{id}", missingId))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString(missingId.toString())));
    }

    // AC6: non-UUID path value → 400 INVALID_ID_FORMAT
    @Test
    void getById_nonUuidPath_returns400InvalidIdFormat() throws Exception {
        mockMvc.perform(get("/api/v1/tasks/abc123"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_ID_FORMAT"));
    }
}
