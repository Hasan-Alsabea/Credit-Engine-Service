package com.digibank.creditengine.service;

import com.digibank.creditengine.model.request.RepaymentRequest;
import com.digibank.creditengine.model.response.RepaymentScheduleResponse;
import com.digibank.creditengine.model.response.RepaymentScheduleResponse.ScheduleEntry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Projects how an outstanding balance grows over time under compound interest.
 *
 * The service first accounts for any days the payment is already overdue,
 * then generates a forward-looking schedule at fixed checkpoints (day 0, 7,
 * 14, 30, 60, 90) so the customer can see what they'll owe if they delay
 * further. Daily compounding uses the formula: balance * (1 + dailyRate)^days.
 */
@Service
public class RepaymentService {

    // Bahraini Dinar uses 3 decimal places (1 BHD = 1000 fils)
    private static final int BHD_SCALE = 3;

    private static final BigDecimal DAYS_IN_YEAR = new BigDecimal("365");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    // Fixed schedule checkpoints — common intervals customers care about
    private static final int[] SCHEDULE_DAYS = {0, 7, 14, 30, 60, 90};

    // Fallback APR when the request doesn't specify one; configurable via application.yml
    @Value("${credit.engine.default-apr}")
    private BigDecimal defaultApr;

    public RepaymentScheduleResponse calculate(RepaymentRequest request) {
        BigDecimal annualRate = request.annualInterestRate() != null
                ? request.annualInterestRate()
                : defaultApr;

        // Convert APR percentage to a daily decimal rate (e.g. 24% → 0.0006575...)
        BigDecimal dailyRate = annualRate
                .divide(DAYS_IN_YEAR, 10, RoundingMode.HALF_UP)
                .divide(HUNDRED, 10, RoundingMode.HALF_UP);

        BigDecimal onePlusDailyRate = BigDecimal.ONE.add(dailyRate);

        // If the payment is overdue, compound interest has already been accruing
        BigDecimal currentAmountOwed = request.outstandingBalance()
                .multiply(pow(onePlusDailyRate, request.dueDateDaysAgo()))
                .setScale(BHD_SCALE, RoundingMode.HALF_UP);

        LocalDate today = LocalDate.now();
        List<ScheduleEntry> schedule = new ArrayList<>();

        // Build the forward projection at each checkpoint up to the requested horizon
        for (int day : SCHEDULE_DAYS) {
            if (day > request.projectionDays()) {
                break;
            }

            BigDecimal totalOwed = currentAmountOwed
                    .multiply(pow(onePlusDailyRate, day))
                    .setScale(BHD_SCALE, RoundingMode.HALF_UP);

            // Interest accrued relative to the original principal (not current amount)
            BigDecimal interestAccrued = totalOwed
                    .subtract(request.outstandingBalance())
                    .setScale(BHD_SCALE, RoundingMode.HALF_UP);

            BigDecimal percentageIncrease = BigDecimal.ZERO;
            if (request.outstandingBalance().compareTo(BigDecimal.ZERO) > 0) {
                percentageIncrease = totalOwed.subtract(request.outstandingBalance())
                        .multiply(HUNDRED)
                        .divide(request.outstandingBalance(), BHD_SCALE, RoundingMode.HALF_UP);
            }

            schedule.add(new ScheduleEntry(
                    day,
                    today.plusDays(day),
                    totalOwed,
                    interestAccrued,
                    percentageIncrease
            ));
        }

        return new RepaymentScheduleResponse(
                request.outstandingBalance().setScale(BHD_SCALE, RoundingMode.HALF_UP),
                currentAmountOwed,
                dailyRate.setScale(10, RoundingMode.HALF_UP),
                schedule
        );
    }

    /**
     * BigDecimal exponentiation with 128-bit precision to avoid drift in
     * compound interest calculations over long periods (up to 90 days).
     */
    private BigDecimal pow(BigDecimal base, int exponent) {
        if (exponent == 0) {
            return BigDecimal.ONE;
        }
        return base.pow(exponent, MathContext.DECIMAL128);
    }
}
