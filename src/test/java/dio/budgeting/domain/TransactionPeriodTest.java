package dio.budgeting.domain;

import dio.budgeting.application.ai.TransactionAiResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.Clock;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionPeriodTest {

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final Clock TEST_CLOCK = Clock.fixed(
            Instant.parse("2026-11-23T15:00:00Z"),
            ZoneId.of("America/Sao_Paulo")
    );

    @Test
    void shouldUseBrazilTimezoneAtUtcMonthBoundary() {
        var brazil = ZoneId.of("America/Sao_Paulo");
        var clock = Clock.fixed(
                Instant.parse("2026-12-01T02:30:00Z"),
                brazil
        );

        var today = TransactionPeriod.from(
                TransactionAiResponse.AiPeriod.TODAY,
                clock
        );
        var month = TransactionPeriod.from(
                TransactionAiResponse.AiPeriod.CURRENT_MONTH,
                clock
        );

        assertEquals(
                Instant.parse("2026-11-30T03:00:00Z"),
                today.startDate()
        );
        assertEquals(
                Instant.parse("2026-11-01T03:00:00Z"),
                month.startDate()
        );
    }

    @Test
    void shouldUseDecemberAfterMidnightInBrazil() {
        var brazil = ZoneId.of("America/Sao_Paulo");
        var clock = Clock.fixed(
                Instant.parse("2026-12-01T03:30:00Z"),
                brazil
        );

        var today = TransactionPeriod.from(
                TransactionAiResponse.AiPeriod.TODAY,
                clock
        );
        var month = TransactionPeriod.from(
                TransactionAiResponse.AiPeriod.CURRENT_MONTH,
                clock
        );

        assertEquals(
                Instant.parse("2026-12-01T03:00:00Z"),
                today.startDate()
        );
        assertEquals(
                Instant.parse("2026-12-01T03:00:00Z"),
                month.startDate()
        );
    }

    @Test
    void shouldIgnoreJvmDefaultTimezone() {
        var previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            var brazil = ZoneId.of("America/Sao_Paulo");
            var clock = Clock.fixed(
                    Instant.parse("2026-12-01T02:30:00Z"),
                    brazil
            );

            var period = TransactionPeriod.from(
                    TransactionAiResponse.AiPeriod.CURRENT_MONTH,
                    clock
            );

            assertEquals(
                    Instant.parse("2026-11-01T03:00:00Z"),
                    period.startDate()
            );
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    @Test
    void shouldReturnTodayRange() {
        var period = TransactionPeriod.from(
                TransactionAiResponse.AiPeriod.TODAY,
                TEST_CLOCK
        );

        var today = LocalDate.now(ZONE);

        assertEquals(today.atStartOfDay(ZONE).toInstant(), period.startDate());
        assertEquals(
                today.plusDays(1).atStartOfDay(ZONE).toInstant(),
                period.endDate()
        );
    }

    @Test
    void shouldReturnYesterdayRange() {
        var period = TransactionPeriod.from(
                TransactionAiResponse.AiPeriod.YESTERDAY,
                TEST_CLOCK
        );

        var yesterday = LocalDate.now(ZONE).minusDays(1);

        assertEquals(
                yesterday.atStartOfDay(ZONE).toInstant(),
                period.startDate()
        );
        assertEquals(
                yesterday.plusDays(1).atStartOfDay(ZONE).toInstant(),
                period.endDate()
        );
    }

    @Test
    void shouldReturnCurrentMonthRange() {
        var period = TransactionPeriod.from(
                TransactionAiResponse.AiPeriod.CURRENT_MONTH,
                TEST_CLOCK
        );

        var today = LocalDate.now(ZONE);

        assertEquals(
                today.withDayOfMonth(1).atStartOfDay(ZONE).toInstant(),
                period.startDate()
        );
        assertTrue(period.endDate().isAfter(period.startDate()));
    }
}