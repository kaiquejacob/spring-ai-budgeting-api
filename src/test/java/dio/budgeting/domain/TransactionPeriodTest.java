package dio.budgeting.domain;

import dio.budgeting.application.ai.TransactionAiResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionPeriodTest {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    @Test
    void shouldReturnTodayRange() {
        var period = TransactionPeriod.from(
                TransactionAiResponse.AiPeriod.TODAY
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
                TransactionAiResponse.AiPeriod.YESTERDAY
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
                TransactionAiResponse.AiPeriod.CURRENT_MONTH
        );

        var today = LocalDate.now(ZONE);

        assertEquals(
                today.withDayOfMonth(1).atStartOfDay(ZONE).toInstant(),
                period.startDate()
        );
        assertTrue(period.endDate().isAfter(period.startDate()));
    }
}