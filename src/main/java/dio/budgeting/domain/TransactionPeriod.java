package dio.budgeting.domain;

import dio.budgeting.application.ai.TransactionAiResponse;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

public record TransactionPeriod(
        Instant startDate,
        Instant endDate
) {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    public static TransactionPeriod from(
            TransactionAiResponse.AiPeriod period
    ) {
        var today = LocalDate.now(ZONE);

        return switch (period) {
            case TODAY -> new TransactionPeriod(
                    startOfDay(today),
                    endOfDay(today)
            );
            case YESTERDAY -> {
                var yesterday = today.minusDays(1);
                yield new TransactionPeriod(
                        startOfDay(yesterday),
                        endOfDay(yesterday)
                );
            }
            case THIS_WEEK -> {
                var start = today.with(
                        TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
                );
                yield new TransactionPeriod(
                        startOfDay(start),
                        endOfDay(today)
                );
            }
            case LAST_WEEK -> {
                var start = today
                        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                        .minusWeeks(1);
                var end = start.plusDays(6);
                yield new TransactionPeriod(
                        startOfDay(start),
                        endOfDay(end)
                );
            }
            case CURRENT_MONTH -> {
                var start = today.withDayOfMonth(1);
                yield new TransactionPeriod(
                        startOfDay(start),
                        endOfDay(today)
                );
            }
            case LAST_MONTH -> {
                var start = today.withDayOfMonth(1).minusMonths(1);
                var end = start.with(TemporalAdjusters.lastDayOfMonth());
                yield new TransactionPeriod(
                        startOfDay(start),
                        endOfDay(end)
                );
            }
            case ALL_TIME -> new TransactionPeriod(
                    Instant.EPOCH,
                    Instant.now()
            );
        };
    }

    private static Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(ZONE).toInstant();
    }

    private static Instant endOfDay(LocalDate date) {
        return date.plusDays(1).atStartOfDay(ZONE).toInstant();
    }
}