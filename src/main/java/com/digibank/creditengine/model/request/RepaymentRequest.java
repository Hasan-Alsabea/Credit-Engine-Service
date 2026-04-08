package com.digibank.creditengine.model.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Input for the repayment schedule projection.
 * Describes the current debt situation and how far forward to project.
 */
public record RepaymentRequest(

        // The principal amount currently owed, in BHD
        @NotNull(message = "Outstanding balance is required")
        @Positive(message = "Outstanding balance must be positive")
        BigDecimal outstandingBalance,

        // Annual percentage rate as a whole number (e.g. 24.0 for 24%)
        @NotNull(message = "Annual interest rate is required")
        @Positive(message = "Annual interest rate must be positive")
        BigDecimal annualInterestRate,

        // How many days past the due date the payment already is (0 = due today)
        @NotNull(message = "Due date days ago is required")
        @Min(value = 0, message = "Due date days ago must be at least 0")
        Integer dueDateDaysAgo,

        // How many days forward to project the schedule (capped at 90)
        @NotNull(message = "Projection days is required")
        @Min(value = 1, message = "Projection days must be at least 1")
        @Max(value = 90, message = "Projection days must not exceed 90")
        Integer projectionDays
) {
}
