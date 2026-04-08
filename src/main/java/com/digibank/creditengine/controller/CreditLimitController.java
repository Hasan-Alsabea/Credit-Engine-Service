package com.digibank.creditengine.controller;

import com.digibank.creditengine.model.request.CreditLimitRequest;
import com.digibank.creditengine.model.response.CreditLimitResponse;
import com.digibank.creditengine.service.CreditLimitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the credit limit recommendation endpoint.
 *
 * Accepts a customer's financial profile (income, debt, balance, employment)
 * and returns a recommended credit limit with a full explanation of how
 * the decision was reached.
 */
@RestController
@RequestMapping("/api/v1/credit")
@Tag(name = "Credit Limit", description = "Credit limit recommendation API")
public class CreditLimitController {

    private final CreditLimitService creditLimitService;

    public CreditLimitController(CreditLimitService creditLimitService) {
        this.creditLimitService = creditLimitService;
    }

    @PostMapping("/recommend-limit")
    @Operation(summary = "Get a credit limit recommendation based on financial profile")
    public ResponseEntity<CreditLimitResponse> recommendLimit(
            @Valid @RequestBody CreditLimitRequest request) {
        return ResponseEntity.ok(creditLimitService.recommend(request));
    }
}
