package com.todo.dto;

import com.todo.ranking.domain.Ranking;
import java.util.List;

public record RankingResponse(List<Ranking> rankings) {}
