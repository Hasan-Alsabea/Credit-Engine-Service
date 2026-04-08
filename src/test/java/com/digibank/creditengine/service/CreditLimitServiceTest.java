package com.digibank.creditengine.service;

import com.digibank.creditengine.exception.InvalidInputException;
import com.digibank.creditengine.model.request.CreditLimitRequest;
import com.digibank.creditengine.model.request.CreditLimitRequest.EmploymentType;
import com.digibank.creditengine.model.response.CreditLimitResponse;
import com.digibank.creditengine.model.response.CreditLimitResponse.RiskTier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class CreditLimitServiceTest {

    private final CreditLimitService service = new CreditLimitService();

    // ── Risk Tier Priority Cascade ──────────────────────────────────────

    @Nested
    class RiskTierCascade {

        @Test
        void highRisk_whenDtiAbove50Percent_employed() {
            var response = recommend("500", "260", "200", EmploymentType.EMPLOYED);
            // DTI = 260/500 = 0.5200 > 0.50 — declined with zero limit
            assertEquals(RiskTier.HIGH_RISK, response.riskTier());
            assertEquals(new BigDecimal("0.5200"), response.debtToIncomeRatio());
            assertEquals(new BigDecimal("0.000"), response.recommendedLimit());
        }

        @Test
        void highRisk_whenDtiAbove50Percent_selfEmployed() {
            // The exact conflict case: SELF_EMPLOYED with DTI > 0.5
            // HIGH_RISK must win over MEDIUM_RISK — declined with zero limit
            var response = recommend("500", "300", "200", EmploymentType.SELF_EMPLOYED);
            // DTI = 300/500 = 0.6000 > 0.50
            assertEquals(RiskTier.HIGH_RISK, response.riskTier());
            assertEquals(new BigDecimal("0.6000"), response.debtToIncomeRatio());
            assertEquals(new BigDecimal("0.000"), response.recommendedLimit());
        }

        @Test
        void highRisk_whenUnemployed_evenWithLowDti() {
            var response = recommend("500", "10", "200", EmploymentType.UNEMPLOYED);
            // DTI = 10/500 = 0.0200, but UNEMPLOYED always triggers HIGH_RISK
            assertEquals(RiskTier.HIGH_RISK, response.riskTier());
            assertEquals(new BigDecimal("0.0200"), response.debtToIncomeRatio());
        }

        @Test
        void highRisk_whenUnemployed_andHighDti() {
            var response = recommend("500", "400", "200", EmploymentType.UNEMPLOYED);
            assertEquals(RiskTier.HIGH_RISK, response.riskTier());
        }

        @Test
        void mediumRisk_whenSelfEmployed_lowDti() {
            var response = recommend("500", "50", "200", EmploymentType.SELF_EMPLOYED);
            // DTI = 50/500 = 0.1000 < 0.30, but SELF_EMPLOYED forces MEDIUM_RISK
            assertEquals(RiskTier.MEDIUM_RISK, response.riskTier());
            assertEquals(new BigDecimal("0.1000"), response.debtToIncomeRatio());
        }

        @Test
        void mediumRisk_whenDtiBetween30And50_employed() {
            var response = recommend("500", "200", "200", EmploymentType.EMPLOYED);
            assertEquals(RiskTier.MEDIUM_RISK, response.riskTier());
            assertEquals(new BigDecimal("0.4000"), response.debtToIncomeRatio());
        }

        @Test
        void lowRisk_whenDtiBelow30_employed() {
            var response = recommend("500", "100", "200", EmploymentType.EMPLOYED);
            assertEquals(RiskTier.LOW_RISK, response.riskTier());
            assertEquals(new BigDecimal("0.2000"), response.debtToIncomeRatio());
        }

        @Test
        void lowRisk_whenZeroDebt_employed() {
            var response = recommend("500", "0", "200", EmploymentType.EMPLOYED);
            assertEquals(RiskTier.LOW_RISK, response.riskTier());
            assertEquals(new BigDecimal("0.0000"), response.debtToIncomeRatio());
        }
    }

    // ── DTI Boundary Tests ──────────────────────────────────────────────

    @Nested
    class DtiBoundaries {

        @Test
        void dtiExactly030_employed_isMediumRisk() {
            // 150/500 = 0.3000 — sits on the boundary, >= 0.30 triggers MEDIUM_RISK
            var response = recommend("500", "150", "200", EmploymentType.EMPLOYED);
            assertEquals(RiskTier.MEDIUM_RISK, response.riskTier());
            assertEquals(new BigDecimal("0.3000"), response.debtToIncomeRatio());
        }

        @Test
        void dtiJustBelow030_employed_isLowRisk() {
            // 149/500 = 0.2980 — just under the boundary
            var response = recommend("500", "149", "200", EmploymentType.EMPLOYED);
            assertEquals(RiskTier.LOW_RISK, response.riskTier());
            assertEquals(new BigDecimal("0.2980"), response.debtToIncomeRatio());
        }

        @Test
        void dtiExactly050_employed_isMediumRisk() {
            // 250/500 = 0.5000 — not strictly > 0.50, so HIGH_RISK doesn't kick in
            var response = recommend("500", "250", "200", EmploymentType.EMPLOYED);
            assertEquals(RiskTier.MEDIUM_RISK, response.riskTier());
            assertEquals(new BigDecimal("0.5000"), response.debtToIncomeRatio());
        }

        @Test
        void dtiJustAbove050_employed_isHighRisk() {
            // 251/500 = 0.5020 — crosses into HIGH_RISK territory
            var response = recommend("500", "251", "200", EmploymentType.EMPLOYED);
            assertEquals(RiskTier.HIGH_RISK, response.riskTier());
            assertEquals(new BigDecimal("0.5020"), response.debtToIncomeRatio());
        }

        @Test
        void dtiExactly050_selfEmployed_isMediumRisk() {
            var response = recommend("500", "250", "200", EmploymentType.SELF_EMPLOYED);
            assertEquals(RiskTier.MEDIUM_RISK, response.riskTier());
        }

        @Test
        void dtiJustAbove050_selfEmployed_isHighRisk() {
            // DTI 0.5020 > 0.50 — HIGH_RISK beats SELF_EMPLOYED's MEDIUM_RISK
            var response = recommend("500", "251", "200", EmploymentType.SELF_EMPLOYED);
            assertEquals(RiskTier.HIGH_RISK, response.riskTier());
        }
    }

    // ── Credit Limit Calculation & Caps ─────────────────────────────────

    @Nested
    class CreditLimitCalculation {

        @Test
        void lowRisk_multiplierApplied() {
            var response = recommend("500", "100", "200", EmploymentType.EMPLOYED);
            // 500 * 3 = 1500 (under the 2000 cap)
            assertEquals(new BigDecimal("1500.000"), response.recommendedLimit());
        }

        @Test
        void lowRisk_cappedAt2000() {
            var response = recommend("1000", "100", "200", EmploymentType.EMPLOYED);
            // 1000 * 3 = 3000, but LOW_RISK cap is 2000
            assertEquals(new BigDecimal("2000.000"), response.recommendedLimit());
        }

        @Test
        void mediumRisk_multiplierApplied() {
            var response = recommend("500", "100", "200", EmploymentType.SELF_EMPLOYED);
            // 500 * 1.5 = 750
            assertEquals(new BigDecimal("750.000"), response.recommendedLimit());
        }

        @Test
        void mediumRisk_cappedAt1000() {
            var response = recommend("800", "100", "200", EmploymentType.SELF_EMPLOYED);
            // 800 * 1.5 = 1200, but MEDIUM_RISK cap is 1000
            assertEquals(new BigDecimal("1000.000"), response.recommendedLimit());
        }

        @Test
        void highRisk_alwaysZero() {
            var response = recommend("500", "100", "200", EmploymentType.UNEMPLOYED);
            // HIGH_RISK is always declined — zero limit regardless of income
            assertEquals(new BigDecimal("0.000"), response.recommendedLimit());
        }

        @Test
        void highRisk_alwaysZero_evenHighIncome() {
            var response = recommend("1200", "100", "200", EmploymentType.UNEMPLOYED);
            assertEquals(new BigDecimal("0.000"), response.recommendedLimit());
        }
    }

    // ── Balance Alternative Logic ───────────────────────────────────────

    @Nested
    class BalanceAlternative {

        @Test
        void usedWhenBalanceExceedsBaseLimit() {
            // income 200, LOW_RISK → 200 * 3 = 600
            // balance 1000 > 600, alt = 1000 * 0.8 = 800 > 600 → use 800
            var response = recommend("200", "10", "1000", EmploymentType.EMPLOYED);
            assertEquals(new BigDecimal("800.000"), response.recommendedLimit());
            assertTrue(response.reasoning().contains("account balance method"));
        }

        @Test
        void notUsedWhenBalanceLowerThanBaseLimit() {
            // income 500, LOW_RISK → 1500; balance 200 < 1500 → stick with income-based
            var response = recommend("500", "100", "200", EmploymentType.EMPLOYED);
            assertEquals(new BigDecimal("1500.000"), response.recommendedLimit());
            assertTrue(response.reasoning().contains("income-based calculation"));
        }

        @Test
        void notUsedWhenAlternativeIsLower() {
            // income 500, LOW_RISK → 1500
            // balance 1600 > 1500, but alt = 1600 * 0.8 = 1280 < 1500
            var response = recommend("500", "100", "1600", EmploymentType.EMPLOYED);
            assertEquals(new BigDecimal("1500.000"), response.recommendedLimit());
        }

        @Test
        void balanceAlternativeAlsoRespectsBhdScale() {
            // balance 555.555 * 0.8 = 444.444 — must remain at 3dp
            var response = recommend("100", "10", "555.555", EmploymentType.EMPLOYED);
            assertEquals(new BigDecimal("444.444"), response.recommendedLimit());
        }
    }

    // ── Reasoning Format ────────────────────────────────────────────────

    @Nested
    class ReasoningFormat {

        @Test
        void incomeBasedReasoning_noCap() {
            // 500 * 3 = 1500, under the 2000 cap — no cap language expected
            var response = recommend("500", "100", "200", EmploymentType.EMPLOYED);
            String r = response.reasoning();

            assertTrue(r.contains("Customer earns"));
            assertTrue(r.contains("500.000 BHD/month"));
            assertTrue(r.contains("debt-to-income ratio of 20.00%"));
            assertTrue(r.contains("LOW_RISK category"));
            assertTrue(r.contains("income-based calculation (3x monthly income)"));
            assertFalse(r.contains("capped at"));
            assertTrue(r.contains("Admin review is recommended before final approval"));
        }

        @Test
        void incomeBasedReasoning_withCap() {
            // 1000 * 3 = 3000, exceeds the 2000 cap — must mention the cap
            var response = recommend("1000", "100", "200", EmploymentType.EMPLOYED);
            String r = response.reasoning();

            assertTrue(r.contains("income-based calculation (3x monthly income, capped at the maximum of 2000.000 BHD)"));
            assertFalse(r.contains("income-based calculation (3x monthly income)."),
                    "Should not claim 3x income without mentioning the cap");
        }

        @Test
        void balanceBasedReasoning_containsAllRequiredParts() {
            var response = recommend("200", "10", "1000", EmploymentType.EMPLOYED);
            String r = response.reasoning();

            assertTrue(r.contains("Customer earns"));
            assertTrue(r.contains("200.000 BHD/month"));
            assertTrue(r.contains("LOW_RISK category"));
            assertTrue(r.contains("account balance method"));
            assertTrue(r.contains("average balance of 1000.000 BHD"));
            assertTrue(r.contains("stronger indicator of financial stability"));
            assertTrue(r.contains("Admin review is recommended before final approval"));
        }

        @Test
        void highRisk_reasoningShowsDeclineMessage() {
            var response = recommend("500", "100", "200", EmploymentType.UNEMPLOYED);
            assertTrue(response.reasoning().contains("exceeds acceptable thresholds"));
            assertTrue(response.reasoning().contains("Admin should decline this application"));
        }

        @Test
        void mediumRisk_reasoningShowsCorrectMultiplier() {
            var response = recommend("500", "100", "200", EmploymentType.SELF_EMPLOYED);
            assertTrue(response.reasoning().contains("MEDIUM_RISK category"));
            assertTrue(response.reasoning().contains("1.5x monthly income"));
        }

        @Test
        void mediumRisk_cappedAt1000_mentionedInReasoning() {
            // 800 * 1.5 = 1200, exceeds the 1000 cap
            var response = recommend("800", "100", "200", EmploymentType.SELF_EMPLOYED);
            assertTrue(response.reasoning().contains("capped at the maximum of 1000.000 BHD"));
        }

        @Test
        void highRisk_reasoningIsDeclineOnly() {
            var response = recommend("1200", "100", "200", EmploymentType.UNEMPLOYED);
            // HIGH_RISK should never mention income-based or balance methods
            assertFalse(response.reasoning().contains("income-based"));
            assertFalse(response.reasoning().contains("account balance"));
            assertTrue(response.reasoning().contains("Admin should decline"));
        }
    }

    // ── Edge Cases ──────────────────────────────────────────────────────

    @Nested
    class EdgeCases {

        @Test
        void zeroIncomeThrowsInvalidInputException() {
            var request = new CreditLimitRequest(
                    BigDecimal.ZERO, new BigDecimal("100"),
                    new BigDecimal("200"), EmploymentType.EMPLOYED
            );
            InvalidInputException ex = assertThrows(
                    InvalidInputException.class, () -> service.recommend(request));
            assertTrue(ex.getMessage().contains("Monthly income must be greater than zero"));
        }

        @Test
        void verySmallIncome() {
            // 1 fils (0.001 BHD) — smallest possible BHD amount
            var response = recommend("0.001", "0", "0", EmploymentType.EMPLOYED);
            assertEquals(RiskTier.LOW_RISK, response.riskTier());
            assertEquals(new BigDecimal("0.003"), response.recommendedLimit());
        }

        @Test
        void debtExceedsIncome_dtiOver100Percent() {
            // DTI = 200/100 = 2.0000 — debt is double the income, declined
            var response = recommend("100", "200", "0", EmploymentType.EMPLOYED);
            assertEquals(RiskTier.HIGH_RISK, response.riskTier());
            assertEquals(new BigDecimal("2.0000"), response.debtToIncomeRatio());
            assertEquals(new BigDecimal("0.000"), response.recommendedLimit());
        }

        @Test
        void highRisk_ignoresHighBalance() {
            // HIGH_RISK must return 0 even when account balance is massive
            var response = recommend("1000", "600", "50000", EmploymentType.EMPLOYED);
            assertEquals(RiskTier.HIGH_RISK, response.riskTier());
            assertEquals(new BigDecimal("0.000"), response.recommendedLimit());
            assertFalse(response.reasoning().contains("account balance"));
        }

        @Test
        void largeValues() {
            // income 99999, LOW_RISK → 99999 * 3 = 299997, capped at 2000
            var response = recommend("99999", "1000", "500", EmploymentType.EMPLOYED);
            assertEquals(RiskTier.LOW_RISK, response.riskTier());
            assertEquals(new BigDecimal("2000.000"), response.recommendedLimit());
        }
    }

    // ── Parameterized Full Matrix ───────────────────────────────────────

    @ParameterizedTest(name = "income={0}, debt={1}, employment={2} -> {3}")
    @CsvSource({
            // EMPLOYED: DTI drives the tier
            "1000, 0,   EMPLOYED,       LOW_RISK",
            "1000, 299, EMPLOYED,       LOW_RISK",
            "1000, 300, EMPLOYED,       MEDIUM_RISK",
            "1000, 500, EMPLOYED,       MEDIUM_RISK",
            "1000, 501, EMPLOYED,       HIGH_RISK",
            "1000, 900, EMPLOYED,       HIGH_RISK",
            // SELF_EMPLOYED: floor is MEDIUM_RISK, DTI > 0.50 escalates to HIGH
            "1000, 0,   SELF_EMPLOYED,  MEDIUM_RISK",
            "1000, 200, SELF_EMPLOYED,  MEDIUM_RISK",
            "1000, 500, SELF_EMPLOYED,  MEDIUM_RISK",
            "1000, 501, SELF_EMPLOYED,  HIGH_RISK",
            // UNEMPLOYED: always HIGH_RISK regardless of DTI
            "1000, 0,   UNEMPLOYED,     HIGH_RISK",
            "1000, 100, UNEMPLOYED,     HIGH_RISK",
            "1000, 600, UNEMPLOYED,     HIGH_RISK",
    })
    void riskTierMatrix(String income, String debt, EmploymentType employment, RiskTier expected) {
        var response = recommend(income, debt, "0", employment);
        assertEquals(expected, response.riskTier(),
                () -> String.format("DTI=%s, employment=%s should be %s",
                        response.debtToIncomeRatio(), employment, expected));
    }

    // ── Helper ──────────────────────────────────────────────────────────

    private CreditLimitResponse recommend(String income, String debt,
                                          String balance, EmploymentType employment) {
        return service.recommend(new CreditLimitRequest(
                new BigDecimal(income),
                new BigDecimal(debt),
                new BigDecimal(balance),
                employment
        ));
    }
}
