package com.team6.app.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class WeeklyRankingKeyTest {

    @Test
    void 같은_주의_월요일과_일요일은_같은_키() {
        assertThat(WeeklyRankingKey.of(LocalDate.of(2026, 9, 28)))
                .isEqualTo("ranking:weekly:2026-W40")
                .isEqualTo(WeeklyRankingKey.of(LocalDate.of(2026, 10, 4)));
    }

    @Test
    void 월요일이_되면_다음_주_키() {
        assertThat(WeeklyRankingKey.of(LocalDate.of(2026, 10, 5))).isEqualTo("ranking:weekly:2026-W41");
    }

    @Test
    void 연말은_ISO_주차_연도를_따름() {
        // 2027-01-01(금)은 2026년의 53번째 주에 속함
        assertThat(WeeklyRankingKey.of(LocalDate.of(2027, 1, 1))).isEqualTo("ranking:weekly:2026-W53");
    }

    @Test
    void 주_시작일은_월요일() {
        assertThat(WeeklyRankingKey.weekStart(LocalDate.of(2026, 10, 1))).isEqualTo(LocalDate.of(2026, 9, 28));
    }
}
