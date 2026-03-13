package com.todo.dto;

import com.todo.entity.Todo;

public record TodoUpdateResponse(Todo todo, int pointsEarned) {}
