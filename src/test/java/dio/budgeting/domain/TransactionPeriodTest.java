package dio.budgeting.domain;

import dio.budgeting.application.ai.TransactionAiResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TransactionPeriodTest {

    @Test
    void shouldReturnTodayForTodayPeriod() {
        var period = TransactionPeriod.from(
                TransactionAiResponse.AiPeriod.TODAY
        );

        var today = LocalDate.now();

        assertEquals(today, period.startDate());
        assertEquals(today, period.endDate());
    }

    @Test
    void shouldReturnYesterdayForYesterdayPeriod() {
        var period = TransactionPeriod.from(
                TransactionAiResponse.AiPeriod.YESTERDAY
        );

        var yesterday = LocalDate.now().minusDays(1);

        assertEquals(yesterday, period.startDate());
        assertEquals(yesterday, period.endDate());
    }

    @Test
    void shouldReturnCurrentMonthRange() {
        var period = TransactionPeriod.from(
                TransactionAiResponse.AiPeriod.CURRENT_MONTH
        );

        var today = LocalDate.now();

        assertEquals(today.withDayOfMonth(1), period.startDate());
        assertEquals(today, period.endDate());
    }

    @Test
    void shouldReturnLastMonthRange() {
        var period = TransactionPeriod.from(
                TransactionAiResponse.AiPeriod.LAST_MONTH
        );

        var today = LocalDate.now();
        var expectedStart = today.withDayOfMonth(1).minusMonths(1);
        var expectedEnd = expectedStart.withDayOfMonth(
                expectedStart.lengthOfMonth()
        );

        assertEquals(expectedStart, period.startDate());
        assertEquals(expectedEnd, period.endDate());
    }
}