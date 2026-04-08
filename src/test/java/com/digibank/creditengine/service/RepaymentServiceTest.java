package com.digibank.creditengine.service;

import com.digibank.creditengine.model.request.RepaymentRequest;
import com.digibank.creditengine.model.response.RepaymentScheduleResponse;
import com.digibank.creditengine.model.response.RepaymentScheduleResponse.ScheduleEntry;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RepaymentServiceTest {

    @Autowired
    private RepaymentService service;

    // ── Daily Rate Calculation ──────────────────────────────────────────

    @Nested
    class DailyRateCalculation {

        @Test
        void dailyRateFor24PercentApr() {
            var response = calculate("1000", "24.0", 0, 1);
            // 24 / 365 / 100 = 0.0006575342 (rounded to 10 dp)
            assertEquals(new BigDecimal("0.0006575342"), response.dailyInterestRate());
        }

        @Test
        void dailyRateFor12PercentApr() {
            var response = calculate("1000", "12.0", 0, 1);
            // 12 / 365 / 100 = 0.0003287671
            assertEquals(new BigDecimal("0.0003287671"), response.dailyInterestRate());
        }

        @Test
        void dailyRateFor1PercentApr() {
            var response = calculate("1000", "1.0", 0, 1);
            // 1 / 365 / 100 = 0.0000273973
            assertEquals(new BigDecimal("0.0000273973"), response.dailyInterestRate());
        }
    }

    // ── Current Amount Owed (overdue compound interest) ─────────────────

    @Nested
    class CurrentAmountOwed {

        @Test
        void noOverdue_currentEqualsOriginal() {
            var response = calculate("1000", "24.0", 0, 90);
            assertEquals(new BigDecimal("1000.000"), response.currentAmountOwed());
            assertEquals(new BigDecimal("1000.000"), response.originalBalance());
        }

        @Test
        void thirtyDaysOverdue_compoundInterestApplied() {
            var response = calculate("1000", "24.0", 30, 30);
            // daily rate ~0.0006575342
            // current = 1000 * (1.0006575342)^30
            // = 1000 * 1.01989... ≈ 1019.893
            assertTrue(response.currentAmountOwed().compareTo(new BigDecimal("1019")) > 0);
            assertTrue(response.currentAmountOwed().compareTo(new BigDecimal("1020")) < 0);
        }

        @Test
        void ninetyDaysOverdue() {
            var response = calculate("1000", "24.0", 90, 1);
            // current = 1000 * (1.0006575342)^90 ≈ 1061.01
            assertTrue(response.currentAmountOwed().compareTo(new BigDecimal("1060")) > 0);
            assertTrue(response.currentAmountOwed().compareTo(new BigDecimal("1062")) < 0);
        }

        @Test
        void zeroDaysOverdue() {
            var response = calculate("500", "24.0", 0, 90);
            assertEquals(new BigDecimal("500.000"), response.currentAmountOwed());
        }
    }

    // ── Schedule Generation ─────────────────────────────────────────────

    @Nested
    class ScheduleGeneration {

        @Test
        void fullScheduleWith90DayProjection() {
            var response = calculate("1000", "24.0", 0, 90);
            assertEquals(6, response.schedule().size());

            int[] expectedDays = {0, 7, 14, 30, 60, 90};
            for (int i = 0; i < expectedDays.length; i++) {
                assertEquals(expectedDays[i], response.schedule().get(i).dayNumber());
            }
        }

        @Test
        void projectionExactlyAtScheduleDay_30() {
            var response = calculate("1000", "24.0", 0, 30);
            // Should include: 0, 7, 14, 30 (30 is <= 30, so included)
            assertEquals(4, response.schedule().size());
            assertEquals(30, response.schedule().get(3).dayNumber());
        }

        @Test
        void projectionExactlyAtScheduleDay_14() {
            var response = calculate("1000", "24.0", 0, 14);
            // Should include: 0, 7, 14
            assertEquals(3, response.schedule().size());
            assertEquals(14, response.schedule().get(2).dayNumber());
        }

        @Test
        void projectionBetweenScheduleDays() {
            var response = calculate("500", "12.0", 0, 20);
            // 20 is between 14 and 30, so includes: 0, 7, 14
            assertEquals(3, response.schedule().size());
        }

        @Test
        void minimumProjection_1Day() {
            var response = calculate("1000", "24.0", 0, 1);
            // Only day 0 (0 <= 1)
            assertEquals(1, response.schedule().size());
            assertEquals(0, response.schedule().get(0).dayNumber());
        }

        @Test
        void projectionOf7Days() {
            var response = calculate("1000", "24.0", 0, 7);
            // Includes: 0, 7
            assertEquals(2, response.schedule().size());
        }

        @Test
        void scheduleDatesStartFromToday() {
            var response = calculate("1000", "24.0", 0, 90);
            LocalDate today = LocalDate.now();
            assertEquals(today, response.schedule().get(0).date());
            assertEquals(today.plusDays(7), response.schedule().get(1).date());
            assertEquals(today.plusDays(90), response.schedule().get(5).date());
        }
    }

    // ── Schedule Entry Values ───────────────────────────────────────────

    @Nested
    class ScheduleEntryValues {

        @Test
        void dayZero_noOverdue_totalEqualsOriginal() {
            var response = calculate("1000", "24.0", 0, 90);
            ScheduleEntry day0 = response.schedule().get(0);
            assertEquals(new BigDecimal("1000.000"), day0.totalOwed());
            assertEquals(new BigDecimal("0.000"), day0.interestAccrued());
            assertEquals(new BigDecimal("0.000"), day0.percentageIncrease());
        }

        @Test
        void dayZero_withOverdue_reflectsAccruedInterest() {
            var response = calculate("1000", "24.0", 30, 90);
            ScheduleEntry day0 = response.schedule().get(0);
            // day0 totalOwed = currentAmountOwed (already includes 30 days of interest)
            assertEquals(response.currentAmountOwed(), day0.totalOwed());
            assertTrue(day0.interestAccrued().compareTo(BigDecimal.ZERO) > 0);
            assertTrue(day0.percentageIncrease().compareTo(BigDecimal.ZERO) > 0);
        }

        @Test
        void interestAccruesMonotonically() {
            var response = calculate("1000", "24.0", 0, 90);
            for (int i = 1; i < response.schedule().size(); i++) {
                ScheduleEntry prev = response.schedule().get(i - 1);
                ScheduleEntry curr = response.schedule().get(i);
                assertTrue(curr.totalOwed().compareTo(prev.totalOwed()) > 0,
                        "totalOwed must increase: day " + prev.dayNumber() + " → " + curr.dayNumber());
                assertTrue(curr.interestAccrued().compareTo(prev.interestAccrued()) >= 0,
                        "interestAccrued must not decrease");
                assertTrue(curr.percentageIncrease().compareTo(prev.percentageIncrease()) >= 0,
                        "percentageIncrease must not decrease");
            }
        }

        @Test
        void percentageIncreaseIsCorrect() {
            var response = calculate("1000", "24.0", 0, 90);
            BigDecimal original = response.originalBalance();
            for (ScheduleEntry entry : response.schedule()) {
                BigDecimal expectedPct = entry.totalOwed().subtract(original)
                        .multiply(new BigDecimal("100"))
                        .divide(original, 3, RoundingMode.HALF_UP);
                assertEquals(expectedPct, entry.percentageIncrease(),
                        "percentageIncrease mismatch at day " + entry.dayNumber());
            }
        }

        @Test
        void interestAccruedEqualsTotal_minusOriginal() {
            var response = calculate("1000", "24.0", 10, 90);
            BigDecimal original = response.originalBalance();
            for (ScheduleEntry entry : response.schedule()) {
                BigDecimal expected = entry.totalOwed().subtract(original)
                        .setScale(3, RoundingMode.HALF_UP);
                assertEquals(expected, entry.interestAccrued(),
                        "interestAccrued mismatch at day " + entry.dayNumber());
            }
        }
    }

    // ── Compound Interest Math Verification ─────────────────────────────

    @Nested
    class CompoundInterestMath {

        @Test
        void verifyDay90_24PercentApr_noOverdue() {
            var response = calculate("1000", "24.0", 0, 90);
            ScheduleEntry day90 = response.schedule().get(5);

            // Manual: 1000 * (1 + 24/365/100)^90
            BigDecimal dailyRate = new BigDecimal("24")
                    .divide(new BigDecimal("365"), 10, RoundingMode.HALF_UP)
                    .divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP);
            BigDecimal factor = BigDecimal.ONE.add(dailyRate).pow(90, MathContext.DECIMAL128);
            BigDecimal expected = new BigDecimal("1000").multiply(factor)
                    .setScale(3, RoundingMode.HALF_UP);

            assertEquals(expected, day90.totalOwed());
        }

        @Test
        void verifyWithOverdue_day30_projected30More() {
            var response = calculate("500", "24.0", 15, 30);
            // Service computes in two stages (with intermediate rounding):
            // 1. currentOwed = round3(500 * (1+r)^15)
            // 2. day30 = round3(currentOwed * (1+r)^30)
            BigDecimal dailyRate = new BigDecimal("24")
                    .divide(new BigDecimal("365"), 10, RoundingMode.HALF_UP)
                    .divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP);
            BigDecimal onePlusRate = BigDecimal.ONE.add(dailyRate);
            BigDecimal currentOwed = new BigDecimal("500")
                    .multiply(onePlusRate.pow(15, MathContext.DECIMAL128))
                    .setScale(3, RoundingMode.HALF_UP);
            BigDecimal expected = currentOwed
                    .multiply(onePlusRate.pow(30, MathContext.DECIMAL128))
                    .setScale(3, RoundingMode.HALF_UP);

            assertEquals(currentOwed, response.currentAmountOwed());
            ScheduleEntry day30 = response.schedule().get(3); // 0, 7, 14, 30
            assertEquals(expected, day30.totalOwed());
        }
    }

    // ── BHD Scale Consistency ───────────────────────────────────────────

    @Nested
    class BhdScaleConsistency {

        @Test
        void allMonetaryValuesHave3DecimalPlaces() {
            var response = calculate("1000", "24.0", 15, 90);
            assertEquals(3, response.originalBalance().scale());
            assertEquals(3, response.currentAmountOwed().scale());

            for (ScheduleEntry entry : response.schedule()) {
                assertEquals(3, entry.totalOwed().scale(),
                        "totalOwed scale at day " + entry.dayNumber());
                assertEquals(3, entry.interestAccrued().scale(),
                        "interestAccrued scale at day " + entry.dayNumber());
                assertEquals(3, entry.percentageIncrease().scale(),
                        "percentageIncrease scale at day " + entry.dayNumber());
            }
        }
    }

    // ── Helper ──────────────────────────────────────────────────────────

    private RepaymentScheduleResponse calculate(String balance, String apr,
                                                int dueDaysAgo, int projectionDays) {
        return service.calculate(new RepaymentRequest(
                new BigDecimal(balance),
                new BigDecimal(apr),
                dueDaysAgo,
                projectionDays
        ));
    }
}
