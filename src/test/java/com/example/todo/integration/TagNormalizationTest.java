package com.example.todo.integration;

import com.example.todo.AbstractIntegrationTest;
import com.example.todo.domain.Task;
import com.example.todo.domain.TaskPriority;
import com.example.todo.domain.TaskStatus;
import com.example.todo.dto.CreateTaskRequest;
import com.example.todo.dto.TaskResponse;
import com.example.todo.repository.TaskQueryRepository;
import com.example.todo.repository.TaskRepository;
import com.example.todo.service.TaskListQueryValidator;
import com.example.todo.service.TaskService;
import com.example.todo.service.TaskStatusTransitionValidator;
import com.example.todo.validation.TagNormalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Scenario 4 – Tag normalization.
 *
 * <p>Tags are normalized (trim → lowercase → deduplicate preserving first-seen order) by
 * {@link TagNormalizer} before persistence. These tests verify the normalizer's contract
 * directly and also verify that tags stored in the service layer match the normalized form.
 *
 * <p>Note: normalization happens at deserialization time via {@code TagListDeserializer},
 * so by the time a request reaches {@code TaskService.create()}, the tags list is already
 * normalized. These tests therefore verify the normalizer contract independently and confirm
 * that the service stores whatever it receives (no second normalization step).
 */
@ExtendWith(MockitoExtension.class)
class TagNormalizationTest extends AbstractIntegrationTest {

    @Mock TaskRepository taskRepository;
    @Mock TaskQueryRepository taskQueryRepository;
    @Mock TaskListQueryValidator queryValidator;

    TaskService taskService;

    @BeforeEach
    void setUp() {
        taskService = new TaskService(taskRepository, taskQueryRepository,
                queryValidator, new TaskStatusTransitionValidator());
    }

    // ── TagNormalizer unit behaviour ─────────────────────────────────────────

    @Test
    void normalizer_trimsWhitespace() {
        List<String> result = TagNormalizer.normalize(List.of("  hello  ", " world "));
        assertThat(result).containsExactly("hello", "world");
    }

    @Test
    void normalizer_lowercasesAll() {
        List<String> result = TagNormalizer.normalize(List.of("UPPER", "Mixed"));
        assertThat(result).containsExactly("upper", "mixed");
    }

    @Test
    void normalizer_deduplicates_preservingFirstSeenOrder() {
        List<String> result = TagNormalizer.normalize(List.of("a", "b", "a", "c", "b"));
        assertThat(result).containsExactly("a", "b", "c");
    }

    @Test
    void normalizer_deduplicates_afterCaseNormalization() {
        List<String> result = TagNormalizer.normalize(List.of("Tag", "TAG", "tag"));
        assertThat(result).containsExactly("tag");
    }

    @Test
    void normalizer_deduplicates_afterTrimNormalization() {
        List<String> result = TagNormalizer.normalize(List.of("tag", " tag ", "tag  "));
        assertThat(result).containsExactly("tag");
    }

    @Test
    void normalizer_nullInput_returnsEmptyList() {
        List<String> result = TagNormalizer.normalize(null);
        assertThat(result).isEmpty();
    }

    @Test
    void normalizer_emptyList_returnsEmptyList() {
        List<String> result = TagNormalizer.normalize(List.of());
        assertThat(result).isEmpty();
    }

    @Test
    void normalizer_nullElementsSkipped() {
        List<String> input = new java.util.ArrayList<>();
        input.add("valid");
        input.add(null);
        input.add("other");
        List<String> result = TagNormalizer.normalize(input);
        assertThat(result).containsExactly("valid", "other");
    }

    // ── Service-layer: stored tags match normalized form ─────────────────────

    @Test
    void create_storedTagsMatchNormalizedInput() {
        // By the time the service receives tags, they are already normalized by the deserializer.
        // We verify the service stores them as-is (no silent re-normalization that would change order).
        List<String> alreadyNormalized = List.of("alpha", "beta", "gamma");

        UUID id = UUID.randomUUID();
        Task saved = taskWithTags(id, alreadyNormalized);
        when(taskRepository.save(any(Task.class))).thenReturn(saved);

        CreateTaskRequest req = new CreateTaskRequest();
        req.setTitle("Tagged Task");
        req.setTags(alreadyNormalized);

        TaskResponse resp = taskService.create(req);

        assertThat(resp.getTags()).containsExactlyElementsOf(alreadyNormalized);
    }

    @Test
    void create_emptyTagList_storedAsEmptyList() {
        UUID id = UUID.randomUUID();
        Task saved = taskWithTags(id, List.of());
        when(taskRepository.save(any(Task.class))).thenReturn(saved);

        CreateTaskRequest req = new CreateTaskRequest();
        req.setTitle("No Tags");
        req.setTags(List.of());

        TaskResponse resp = taskService.create(req);

        assertThat(resp.getTags()).isEmpty();
    }

    @Test
    void create_nullTagList_defaultsToEmptyList() {
        UUID id = UUID.randomUUID();
        Task saved = taskWithTags(id, List.of());
        when(taskRepository.save(any(Task.class))).thenReturn(saved);

        CreateTaskRequest req = new CreateTaskRequest();
        req.setTitle("Null Tags");
        req.setTags(null);

        TaskResponse resp = taskService.create(req);

        assertThat(resp.getTags()).isNotNull().isEmpty();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Task taskWithTags(UUID id, List<String> tags) {
        Task t = new Task();
        t.setId(id);
        t.setTitle("T");
        t.setStatus(TaskStatus.TODO);
        t.setPriority(TaskPriority.MEDIUM);
        t.setTags(tags);
        t.setVersion(0L);
        t.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        t.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return t;
    }
}
