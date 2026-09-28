package com.team6.app.ranking;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rankings")
@RequiredArgsConstructor
public class RankingController {

    private static final int TOP_SIZE = 10;

    private final RankingService rankingService;

    // date를 생략하면 이번 주, 지정하면 그 날짜가 속한 주의 TOP 10
    @GetMapping("/weekly")
    public WeeklyRankingResponse weekly(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate target = date != null ? date : LocalDate.now(WeeklyRankingKey.ZONE);
        return rankingService.getWeeklyTop(target, TOP_SIZE);
    }
}
