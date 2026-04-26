package com.todo.service;

import com.todo.dto.BatchCompleteRequest;
import com.todo.dto.TodoUpdateRequest;
import com.todo.entity.Todo;
import com.todo.entity.User;
import com.todo.event.TodoCanceledEvent;
import com.todo.event.TodoCompletedEvent;
import com.todo.exception.CustomException;
import com.todo.exception.ErrorCode;
import com.todo.repository.TodoRepository;
import com.todo.repository.UserRepository;
import com.todo.stats.infrastructure.TodoEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TodoServiceTest {

    @Mock private TodoRepository todoRepository;
    @Mock private UserRepository userRepository;
    @Mock private TodoEventRepository todoEventRepository;
    @Mock private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private TodoService todoService;

    // ─── Elevation of Privilege ──────────────────────────────────────────────

    @Test
    @DisplayName("[Elevation of Privilege] 타인의 todo 삭제 시도 시 FORBIDDEN")
    void shouldReturnForbidden_whenDeletingAnotherUsersTodo() {
        when(todoRepository.findById(1L)).thenReturn(Optional.of(todoOwnedBy(2L)));

        assertThatThrownBy(() -> todoService.deleteTodo(1L, 1L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("[Elevation of Privilege] 타인의 todo 수정 시도 시 FORBIDDEN")
    void shouldReturnForbidden_whenUpdatingAnotherUsersTodo() {
        when(todoRepository.findById(1L)).thenReturn(Optional.of(todoOwnedBy(2L)));

        assertThatThrownBy(() -> todoService.updateTodo(1L, 1L, new TodoUpdateRequest()))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    // ─── batchComplete 이벤트 발행 ────────────────────────────────────────────

    @Test
    @DisplayName("batchComplete — 완료 처리 후 TodoCompletedEvent 발행")
    void batchComplete_publishesCompletedEvent() {
        Todo todo = uncompletedTodo(1L, 1L);
        when(todoRepository.findAllById(List.of(1L))).thenReturn(List.of(todo));
        when(todoRepository.save(any())).thenReturn(todo);
        when(userRepository.findById(1L)).thenReturn(Optional.of(userWithScore(1L, 0)));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BatchCompleteRequest request = new BatchCompleteRequest();
        request.setIds(List.of(1L));

        todoService.batchComplete(1L, request);

        ArgumentCaptor<TodoCompletedEvent> captor = ArgumentCaptor.forClass(TodoCompletedEvent.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(1L);
        assertThat(captor.getValue().getTodoId()).isEqualTo(1L);
        assertThat(captor.getValue().getOccurredAt()).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("batchComplete — 이미 완료된 todo는 이벤트를 발행하지 않음")
    void batchComplete_doesNotPublishEvent_whenAlreadyCompleted() {
        Todo todo = completedTodo(1L, 1L, LocalDate.now());
        when(todoRepository.findAllById(List.of(1L))).thenReturn(List.of(todo));

        BatchCompleteRequest request = new BatchCompleteRequest();
        request.setIds(List.of(1L));

        todoService.batchComplete(1L, request);

        verifyNoInteractions(applicationEventPublisher);
    }

    // ─── batchUndo 이벤트 발행 ────────────────────────────────────────────────

    @Test
    @DisplayName("batchUndo — 완료 취소 후 TodoCanceledEvent 발행")
    void batchUndo_publishesCanceledEvent() {
        LocalDate completedAt = LocalDate.now().minusDays(1);
        Todo todo = completedTodo(1L, 1L, completedAt);
        when(todoRepository.findAllById(List.of(1L))).thenReturn(List.of(todo));
        when(todoRepository.save(any())).thenReturn(todo);
        when(userRepository.findById(1L)).thenReturn(Optional.of(userWithScore(1L, 10)));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BatchCompleteRequest request = new BatchCompleteRequest();
        request.setIds(List.of(1L));

        todoService.batchUndo(1L, request);

        ArgumentCaptor<TodoCanceledEvent> captor = ArgumentCaptor.forClass(TodoCanceledEvent.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(1L);
        assertThat(captor.getValue().getTodoId()).isEqualTo(1L);
        assertThat(captor.getValue().getOccurredAt()).isEqualTo(completedAt);
    }

    @Test
    @DisplayName("batchUndo — 완료되지 않은 todo는 이벤트를 발행하지 않음")
    void batchUndo_doesNotPublishEvent_whenNotCompleted() {
        Todo todo = uncompletedTodo(1L, 1L);
        when(todoRepository.findAllById(List.of(1L))).thenReturn(List.of(todo));

        BatchCompleteRequest request = new BatchCompleteRequest();
        request.setIds(List.of(1L));

        todoService.batchUndo(1L, request);

        verifyNoInteractions(applicationEventPublisher);
    }

    // ─── 헬퍼 ─────────────────────────────────────────────────────────────────

    private Todo todoOwnedBy(Long ownerId) {
        Todo todo = new Todo();
        todo.setUserId(ownerId);
        todo.setTitle("test");
        return todo;
    }

    private Todo uncompletedTodo(Long id, Long userId) {
        Todo todo = new Todo();
        todo.setId(id);
        todo.setUserId(userId);
        todo.setTitle("test");
        todo.setCompleted(false);
        return todo;
    }

    private Todo completedTodo(Long id, Long userId, LocalDate completedAt) {
        Todo todo = new Todo();
        todo.setId(id);
        todo.setUserId(userId);
        todo.setTitle("test");
        todo.setCompleted(true);
        todo.setCompletedAt(completedAt);
        return todo;
    }

    private User userWithScore(Long id, int score) {
        User user = new User();
        user.setId(id);
        user.setScore(score);
        return user;
    }
}