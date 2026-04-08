package com.digibank.creditengine.model.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Compound interest projection showing how an outstanding balance grows over time.
 */
public record RepaymentScheduleResponse(
        BigDecimal originalBalance,     // The principal as submitted in the request
        BigDecimal currentAmountOwed,   // Balance after accounting for overdue days
        BigDecimal dailyInterestRate,   // Daily rate derived from the APR (10 decimal places)
        List<ScheduleEntry> schedule    // Projected amounts at fixed day checkpoints
) {

    /**
     * A single row in the repayment schedule, representing the state of
     * the debt at a specific future date.
     */
    public record ScheduleEntry(
            int dayNumber,                 // Days from today (0, 7, 14, 30, 60, 90)
            LocalDate date,                // Calendar date for this checkpoint
            BigDecimal totalOwed,          // Total amount owed at this point
            BigDecimal interestAccrued,    // Interest accumulated above the original principal
            BigDecimal percentageIncrease  // Growth as a percentage of the original principal
    ) {
    }
}
