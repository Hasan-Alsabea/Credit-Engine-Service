package com.digibank.creditengine.model.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * Input for the credit limit recommendation engine.
 * All monetary values are in Bahraini Dinar (BHD).
 */
public record CreditLimitRequest(

        // Gross declared monthly income before deductions
        @NotNull(message = "Monthly income is required")
        @PositiveOrZero(message = "Monthly income must be zero or positive")
        BigDecimal monthlyIncome,

        // Total monthly obligations (loans, rent, other credit lines)
        @NotNull(message = "Existing monthly debt is required")
        @PositiveOrZero(message = "Existing monthly debt must be zero or positive")
        BigDecimal existingMonthlyDebt,

        // Rolling 3-month average of the customer's primary account balance
        @NotNull(message = "Account average balance is required")
        @PositiveOrZero(message = "Account average balance must be zero or positive")
        BigDecimal accountAverageBalance,

        @NotNull(message = "Employment type is required")
        EmploymentType employmentType
) {

    public enum EmploymentType {
        EMPLOYED, SELF_EMPLOYED, UNEMPLOYED
    }
}
