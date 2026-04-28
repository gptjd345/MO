package com.todo.repository;

import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.todo.entity.QTodo;
import com.todo.entity.Todo;
import com.querydsl.core.BooleanBuilder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class TodoQueryRepository {

    private final JPAQueryFactory queryFactory;

    public TodoQueryRepository(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    public Page<Todo> findTodosPaged(Long userId, boolean completed, String search, int page, int size, String sort) {
        QTodo todo = QTodo.todo;

        BooleanBuilder where = new BooleanBuilder();
        where.and(todo.userId.eq(userId));
        where.and(todo.completed.eq(completed));

        if (search != null && !search.isBlank()) {
            where.and(
                todo.title.containsIgnoreCase(search)
                    .or(todo.content.coalesce("").containsIgnoreCase(search))
            );
        }

        List<Todo> content = queryFactory
                .selectFrom(todo)
                .where(where)
                .orderBy(buildOrderSpecifiers(sort, todo))
                .offset((long) page * size)
                .limit(size)
                .fetch();

        Long total = queryFactory
                .select(todo.count())
                .from(todo)
                .where(where)
                .fetchOne();

        return new PageImpl<>(content, PageRequest.of(page, size), total != null ? total : 0L);
    }

    private OrderSpecifier<?>[] buildOrderSpecifiers(String sort, QTodo todo) {
        return switch (sort) {
            case "priority" -> new OrderSpecifier<?>[] {
                new CaseBuilder()
                    .when(todo.priority.eq("HIGH")).then(0)
                    .when(todo.priority.eq("MEDIUM")).then(1)
                    .otherwise(2).asc(),
                todo.id.desc()
            };
            case "oldest" -> new OrderSpecifier<?>[] { todo.id.asc() };
            case "name"   -> new OrderSpecifier<?>[] { todo.title.asc() };
            case "deadline" -> new OrderSpecifier<?>[] {
                new OrderSpecifier<>(Order.ASC, todo.endDate, OrderSpecifier.NullHandling.NullsLast)
            };
            default -> new OrderSpecifier<?>[] { todo.id.desc() };
        };
    }
}