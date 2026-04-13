package com.todo.service;

import com.todo.dto.TodoUpdateRequest;
import com.todo.entity.Todo;
import com.todo.exception.CustomException;
import com.todo.exception.ErrorCode;
import com.todo.publisher.TodoEventPublisher;
import com.todo.repository.TodoRepository;
import com.todo.repository.UserRepository;
import com.todo.stats.infrastructure.TodoEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * STRIDE — Elevation of Privilege (타인의 리소스 접근)
 *
 * 인증은 통과했지만 다른 사용자의 todo를 수정/삭제하려는 시도를
 * 서비스 계층에서 FORBIDDEN으로 차단하는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class TodoServiceTest {

    @Mock private TodoRepository todoRepository;
    @Mock private UserRepository userRepository;
    @Mock private TodoEventRepository todoEventRepository;
    @Mock private TodoEventPublisher todoEventPublisher;

    @InjectMocks
    private TodoService todoService;

    private Todo todoOwnedBy(Long ownerId) {
        Todo todo = new Todo();
        todo.setUserId(ownerId);
        todo.setTitle("test");
        return todo;
    }

    @Test
    @DisplayName("[Elevation of Privilege] 타인의 todo 삭제 시도 시 FORBIDDEN")
    void shouldReturnForbidden_whenDeletingAnotherUsersTodo() {
        // given — todo는 user 2의 소유
        when(todoRepository.findById(1L)).thenReturn(Optional.of(todoOwnedBy(2L)));

        // when & then — user 1이 삭제 시도
        assertThatThrownBy(() -> todoService.deleteTodo(1L, 1L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("[Elevation of Privilege] 타인의 todo 수정 시도 시 FORBIDDEN")
    void shouldReturnForbidden_whenUpdatingAnotherUsersTodo() {
        // given — todo는 user 2의 소유
        when(todoRepository.findById(1L)).thenReturn(Optional.of(todoOwnedBy(2L)));

        // when & then — user 1이 수정 시도
        assertThatThrownBy(() -> todoService.updateTodo(1L, 1L, new TodoUpdateRequest()))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }
}