package com.team6.app.ranking;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;

// 주간 랭킹 키 규칙: ranking:weekly:{yyyy}-W{ww} (ISO 주차, 월요일 시작, Asia/Seoul)
public final class WeeklyRankingKey {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final String PREFIX = "ranking:weekly:";

    private WeeklyRankingKey() {
    }

    // 예: 2026-09-28 -> "2026-W40"
    public static String weekLabel(LocalDate date) {
        int year = date.get(IsoFields.WEEK_BASED_YEAR);
        int week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return String.format("%d-W%02d", year, week);
    }

    public static String of(LocalDate date) {
        return PREFIX + weekLabel(date);
    }

    public static LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
