package com.digibank.creditengine.service;

import com.digibank.creditengine.exception.InvalidInputException;
import com.digibank.creditengine.model.request.CreditLimitRequest;
import com.digibank.creditengine.model.request.CreditLimitRequest.EmploymentType;
import com.digibank.creditengine.model.response.CreditLimitResponse;
import com.digibank.creditengine.model.response.CreditLimitResponse.RiskTier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Evaluates a customer's financial profile and recommends a credit limit.
 *
 * The recommendation pipeline works in three stages:
 * 1. Calculate the debt-to-income (DTI) ratio
 * 2. Assign a risk tier using a strict priority cascade (HIGH > MEDIUM > LOW)
 * 3. Derive a credit limit from the tier's multiplier, optionally overridden
 *    by the account balance method when the average balance signals stronger
 *    financial stability than income alone
 *
 * All monetary amounts follow BHD convention (3 decimal places).
 */
@Service
public class CreditLimitService {

    // Bahraini Dinar uses 3 decimal places (1 BHD = 1000 fils)
    private static final int BHD_SCALE = 3;

    // DTI is reported to 4 decimal places for precision in boundary decisions
    private static final int DTI_SCALE = 4;

    // Risk tier DTI boundaries (inclusive/exclusive defined in determineRiskTier)
    private static final BigDecimal DTI_LOW_THRESHOLD = new BigDecimal("0.3");
    private static final BigDecimal DTI_HIGH_THRESHOLD = new BigDecimal("0.5");

    // Each tier has a multiplier applied to monthly income, subject to a hard cap
    private static final BigDecimal LOW_RISK_MULTIPLIER = new BigDecimal("3");
    private static final BigDecimal LOW_RISK_CAP = new BigDecimal("2000");

    private static final BigDecimal MEDIUM_RISK_MULTIPLIER = new BigDecimal("1.5");
    private static final BigDecimal MEDIUM_RISK_CAP = new BigDecimal("1000");

    private static final BigDecimal HIGH_RISK_MULTIPLIER = new BigDecimal("0.5");
    private static final BigDecimal HIGH_RISK_CAP = new BigDecimal("500");

    // When the average account balance exceeds the income-based limit, we offer
    // 80% of that balance as an alternative — it indicates the customer can
    // sustain a higher credit line than their income alone suggests.
    private static final BigDecimal BALANCE_ALTERNATIVE_FACTOR = new BigDecimal("0.8");

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public CreditLimitResponse recommend(CreditLimitRequest request) {
        if (request.monthlyIncome().compareTo(BigDecimal.ZERO) == 0) {
            throw new InvalidInputException(
                    "Monthly income must be greater than zero to calculate debt-to-income ratio");
        }

        // Stage 1: DTI = existing obligations / gross monthly income
        BigDecimal dti = request.existingMonthlyDebt()
                .divide(request.monthlyIncome(), DTI_SCALE, RoundingMode.HALF_UP);

        // Stage 2: Map DTI + employment type to a risk tier
        RiskTier riskTier = determineRiskTier(dti, request.employmentType());

        // HIGH_RISK customers are declined outright — no credit extension regardless of balance
        if (riskTier == RiskTier.HIGH_RISK) {
            return new CreditLimitResponse(
                    BigDecimal.ZERO.setScale(BHD_SCALE),
                    riskTier,
                    dti,
                    "Customer's debt-to-income ratio exceeds acceptable thresholds for credit extension. " +
                    "Credit limit cannot be recommended. Admin should decline this application."
            );
        }

        // Stage 3: Derive the base credit limit from the tier (LOW_RISK / MEDIUM_RISK only)
        BigDecimal multiplier = getMultiplier(riskTier);
        BigDecimal uncappedLimit = request.monthlyIncome().multiply(multiplier);
        BigDecimal cap = getCap(riskTier);
        BigDecimal incomeBasedLimit = uncappedLimit.min(cap);
        boolean wasCapped = uncappedLimit.compareTo(cap) > 0;

        // Account balance override — only available for LOW_RISK and MEDIUM_RISK
        BigDecimal balanceAlternative = request.accountAverageBalance()
                .multiply(BALANCE_ALTERNATIVE_FACTOR)
                .setScale(BHD_SCALE, RoundingMode.HALF_UP);

        boolean useBalanceMethod = request.accountAverageBalance().compareTo(incomeBasedLimit) > 0
                && balanceAlternative.compareTo(incomeBasedLimit) > 0;

        BigDecimal recommendedLimit = useBalanceMethod ? balanceAlternative : incomeBasedLimit;

        // Build a human-readable explanation of how the decision was made
        String reasoning = buildReasoning(
                request.monthlyIncome(), dti, riskTier,
                recommendedLimit, multiplier, wasCapped, cap,
                useBalanceMethod, request.accountAverageBalance());

        return new CreditLimitResponse(
                recommendedLimit.setScale(BHD_SCALE, RoundingMode.HALF_UP),
                riskTier,
                dti,
                reasoning
        );
    }

    /**
     * Strict priority cascade — the highest applicable risk tier always wins.
     *
     * 1. DTI > 50% OR unemployed           → HIGH_RISK
     * 2. DTI >= 30% OR self-employed        → MEDIUM_RISK
     * 3. Everything else (DTI < 30%, employed) → LOW_RISK
     */
    private RiskTier determineRiskTier(BigDecimal dti, EmploymentType employmentType) {
        if (dti.compareTo(DTI_HIGH_THRESHOLD) > 0 || employmentType == EmploymentType.UNEMPLOYED) {
            return RiskTier.HIGH_RISK;
        }
        if (dti.compareTo(DTI_LOW_THRESHOLD) >= 0 || employmentType == EmploymentType.SELF_EMPLOYED) {
            return RiskTier.MEDIUM_RISK;
        }
        return RiskTier.LOW_RISK;
    }

    private BigDecimal getMultiplier(RiskTier riskTier) {
        return switch (riskTier) {
            case LOW_RISK -> LOW_RISK_MULTIPLIER;
            case MEDIUM_RISK -> MEDIUM_RISK_MULTIPLIER;
            case HIGH_RISK -> HIGH_RISK_MULTIPLIER;
        };
    }

    private BigDecimal getCap(RiskTier riskTier) {
        return switch (riskTier) {
            case LOW_RISK -> LOW_RISK_CAP;
            case MEDIUM_RISK -> MEDIUM_RISK_CAP;
            case HIGH_RISK -> HIGH_RISK_CAP;
        };
    }

    /**
     * Produces a plain-English explanation suitable for customer-facing output
     * or internal audit logs. Describes the input data, the risk classification,
     * and which calculation method determined the final limit.
     */
    private String buildReasoning(BigDecimal monthlyIncome, BigDecimal dti,
                                  RiskTier riskTier, BigDecimal recommendedLimit,
                                  BigDecimal multiplier, boolean wasCapped,
                                  BigDecimal cap, boolean useBalanceMethod,
                                  BigDecimal accountAverageBalance) {

        BigDecimal dtiPercent = dti.multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);
        String tierLabel = formatTierLabel(riskTier);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(
                "Customer earns %s BHD/month with a debt-to-income ratio of %s%%. ",
                monthlyIncome.setScale(BHD_SCALE, RoundingMode.HALF_UP), dtiPercent));

        sb.append(String.format(
                "This places them in the %s category. ", tierLabel));

        sb.append(String.format(
                "The recommended limit of %s BHD was calculated using ",
                recommendedLimit.setScale(BHD_SCALE, RoundingMode.HALF_UP)));

        if (useBalanceMethod) {
            sb.append(String.format(
                    "account balance method, as the average balance of %s BHD exceeded " +
                    "the income-based figure, providing a stronger indicator of financial stability. ",
                    accountAverageBalance.setScale(BHD_SCALE, RoundingMode.HALF_UP)));
        } else if (wasCapped) {
            sb.append(String.format(
                    "income-based calculation (%sx monthly income, capped at the maximum of %s BHD). ",
                    multiplier, cap.setScale(BHD_SCALE, RoundingMode.HALF_UP)));
        } else {
            sb.append(String.format(
                    "income-based calculation (%sx monthly income). ", multiplier));
        }

        sb.append("Admin review is recommended before final approval.");
        return sb.toString();
    }

    private String formatTierLabel(RiskTier riskTier) {
        return switch (riskTier) {
            case LOW_RISK -> "LOW_RISK";
            case MEDIUM_RISK -> "MEDIUM_RISK";
            case HIGH_RISK -> "HIGH_RISK";
        };
    }
}
