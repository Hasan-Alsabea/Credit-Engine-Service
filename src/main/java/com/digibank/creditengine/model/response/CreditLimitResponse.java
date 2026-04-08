package com.digibank.creditengine.model.response;

import java.math.BigDecimal;

/**
 * Result of the credit limit recommendation, including the decision rationale.
 */
public record CreditLimitResponse(
        BigDecimal recommendedLimit,       // Final recommended limit in BHD
        RiskTier riskTier,                 // Assigned risk classification
        BigDecimal debtToIncomeRatio,      // DTI as a decimal (e.g. 0.3000 = 30%)
        String reasoning                   // Human-readable explanation of the decision
) {

    /**
     * Risk classification tiers, ordered from least to most restrictive.
     * The tier determines both the income multiplier and the maximum cap.
     */
    public enum RiskTier {
        LOW_RISK, MEDIUM_RISK, HIGH_RISK
    }
}
