package dio.budgeting.domain;

import dio.budgeting.application.ai.TransactionAiResponse;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

public record TransactionPeriod(
        LocalDate startDate,
        LocalDate endDate
) {

    public static TransactionPeriod from(
            TransactionAiResponse.AiPeriod period
    ) {
        var today = LocalDate.now();

        return switch (period) {
            case TODAY -> new TransactionPeriod(today, today);
            case YESTERDAY -> {
                var yesterday = today.minusDays(1);
                yield new TransactionPeriod(yesterday, yesterday);
            }
            case THIS_WEEK -> new TransactionPeriod(
                    today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                    today
            );
            case LAST_WEEK -> {
                var start = today
                        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                        .minusWeeks(1);
                var end = start.plusDays(6);
                yield new TransactionPeriod(start, end);
            }
            case CURRENT_MONTH -> new TransactionPeriod(
                    today.withDayOfMonth(1),
                    today
            );
            case LAST_MONTH -> {
                var start = today
                        .withDayOfMonth(1)
                        .minusMonths(1);
                var end = start.with(TemporalAdjusters.lastDayOfMonth());
                yield new TransactionPeriod(start, end);
            }
            case ALL_TIME -> new TransactionPeriod(
                    LocalDate.of(1970, 1, 1),
                    today
            );
        };
    }
}