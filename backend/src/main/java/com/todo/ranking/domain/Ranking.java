package com.todo.ranking.domain;

public record Ranking(Long userId, String nickname, int score, long rank, boolean isMe) {}
