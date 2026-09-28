package com.team6.app.mypage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public final class MyPageDtos {

    private MyPageDtos() {
    }

    // 닉네임 규칙은 회원가입과 같음
    public record UpdateProfileRequest(@NotBlank @Size(max = 30) String nickname) {
    }

    public record MeResponse(Long id, String email, String nickname, String photoUrl, LocalDate joinedAt) {
    }

    public record PhotoResponse(String photoUrl) {
    }

    // 종류별 합계. type은 CARDIO · STRENGTH
    public record TypeTotal(String type, long count, long minutes) {
    }

    // 마이페이지 운동 요약. weekRank는 이번 주 랭킹 순위(1부터), 랭킹에 없거나 Redis를 못 쓰면 null
    public record SummaryResponse(
            long totalCount,
            long totalMinutes,
            long weekCount,
            long weekMinutes,
            Integer weekRank,
            int streakDays,
            List<TypeTotal> byType) {
    }

    public record DayMinutes(LocalDate date, long minutes) {
    }

    // 11. 내 주간 운동 통계. days는 월~일 7칸 (기록 없는 날은 0)
    public record WeeklyStatsResponse(
            String week,
            LocalDate start,
            LocalDate end,
            long workoutCount,
            long totalMinutes,
            List<TypeTotal> byType,
            List<DayMinutes> days) {
    }
}
