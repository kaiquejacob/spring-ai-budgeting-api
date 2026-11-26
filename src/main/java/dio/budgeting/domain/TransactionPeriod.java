package dio.budgeting.domain;

import dio.budgeting.application.ai.TransactionAiResponse;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.Clock;
import java.time.temporal.TemporalAdjusters;

public record TransactionPeriod(
        Instant startDate,
        Instant endDate
) {

    public static TransactionPeriod from(
            TransactionAiResponse.AiPeriod period,
            Clock clock
    ) {
        var zone = clock.getZone();
        var today = LocalDate.now(clock);

        return switch (period) {
            case TODAY -> new TransactionPeriod(
                    startOfDay(today, zone),
                    endOfDay(today, zone)
            );
            case YESTERDAY -> {
                var yesterday = today.minusDays(1);
                yield new TransactionPeriod(
                        startOfDay(yesterday, zone),
                        endOfDay(yesterday, zone)
                );
            }
            case THIS_WEEK -> {
                var start = today.with(
                        TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
                );
                yield new TransactionPeriod(
                        startOfDay(start, zone),
                        endOfDay(today, zone)
                );
            }
            case LAST_WEEK -> {
                var start = today
                        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                        .minusWeeks(1);
                var end = start.plusDays(6);
                yield new TransactionPeriod(
                        startOfDay(start, zone),
                        endOfDay(end, zone)
                );
            }
            case CURRENT_MONTH -> {
                var start = today.withDayOfMonth(1);
                yield new TransactionPeriod(
                        startOfDay(start, zone),
                        endOfDay(today, zone)
                );
            }
            case LAST_MONTH -> {
                var start = today.withDayOfMonth(1).minusMonths(1);
                var end = start.with(TemporalAdjusters.lastDayOfMonth());
                yield new TransactionPeriod(
                        startOfDay(start, zone),
                        endOfDay(end, zone)
                );
            }
            case ALL_TIME -> new TransactionPeriod(
                    Instant.EPOCH,
                    clock.instant()
            );
        };
    }

    private static Instant startOfDay(LocalDate date, ZoneId zone) {
        return date.atStartOfDay(zone).toInstant();
    }

    private static Instant endOfDay(LocalDate date, ZoneId zone) {
        return date.plusDays(1).atStartOfDay(zone).toInstant();
    }
}