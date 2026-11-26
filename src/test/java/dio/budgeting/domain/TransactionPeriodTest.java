package dio.budgeting.domain;

import dio.budgeting.application.ai.TransactionAiResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.Clock;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TransactionPeriodTest {

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

        assertEquals(
                Instant.parse("2026-11-23T03:00:00Z"),
                period.startDate()
        );
        assertEquals(
                Instant.parse("2026-11-24T03:00:00Z"),
                period.endDate()
        );
    }

    @Test
    void shouldReturnYesterdayRange() {
        var period = TransactionPeriod.from(
                TransactionAiResponse.AiPeriod.YESTERDAY,
                TEST_CLOCK
        );

        assertEquals(
                Instant.parse("2026-11-22T03:00:00Z"),
                period.startDate()
        );
        assertEquals(
                Instant.parse("2026-11-23T03:00:00Z"),
                period.endDate()
        );
    }

    @Test
    void shouldReturnCurrentMonthRange() {
        var period = TransactionPeriod.from(
                TransactionAiResponse.AiPeriod.CURRENT_MONTH,
                TEST_CLOCK
        );

        assertEquals(
                Instant.parse("2026-11-01T03:00:00Z"),
                period.startDate()
        );
        assertEquals(
                Instant.parse("2026-11-24T03:00:00Z"),
                period.endDate()
        );
    }
}