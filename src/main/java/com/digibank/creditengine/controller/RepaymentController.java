package com.digibank.creditengine.controller;

import com.digibank.creditengine.model.request.RepaymentRequest;
import com.digibank.creditengine.model.response.RepaymentScheduleResponse;
import com.digibank.creditengine.service.RepaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the repayment schedule projection endpoint.
 *
 * Given an overdue balance and interest rate, projects how the amount owed
 * grows over time using daily compound interest. Useful for showing customers
 * the cost of delayed payment.
 */
@RestController
@RequestMapping("/api/v1/credit")
@Tag(name = "Repayment Schedule", description = "Late payment interest and repayment schedule projection API")
public class RepaymentController {

    private final RepaymentService repaymentService;

    public RepaymentController(RepaymentService repaymentService) {
        this.repaymentService = repaymentService;
    }

    @PostMapping("/repayment-schedule")
    @Operation(summary = "Calculate repayment schedule with compound interest projection")
    public ResponseEntity<RepaymentScheduleResponse> repaymentSchedule(
            @Valid @RequestBody RepaymentRequest request) {
        return ResponseEntity.ok(repaymentService.calculate(request));
    }
}
