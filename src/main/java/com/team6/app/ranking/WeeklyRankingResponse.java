package com.team6.app.ranking;

import java.util.List;

public record WeeklyRankingResponse(String week, List<Entry> entries) {

    public record Entry(int rank, Long userId, String nickname, long minutes) {
    }
}
