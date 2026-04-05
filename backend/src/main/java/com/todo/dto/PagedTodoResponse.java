package com.todo.dto;

import com.todo.entity.Todo;
import java.util.List;

public class PagedTodoResponse {

    private final List<Todo> content;
    private final long totalCount;
    private final int totalPages;

    public PagedTodoResponse(List<Todo> content, long totalCount, int totalPages) {
        this.content = content;
        this.totalCount = totalCount;
        this.totalPages = totalPages;
    }

    public List<Todo> getContent() { return content; }
    public long getTotalCount() { return totalCount; }
    public int getTotalPages() { return totalPages; }
}