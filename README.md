# Credit Engine Service

A stateless Spring Boot microservice that provides credit limit recommendations and repayment schedule projections. Designed for the Bahraini Dinar (BHD) currency with 3 decimal place precision.

## Features

- **Credit Limit Recommendation** — Calculates a recommended credit limit based on income, existing debt, account balance, and employment type using a risk-tiered model.
- **Repayment Schedule Projection** — Projects compound interest accumulation on overdue balances and generates a day-by-day repayment schedule.

## Prerequisites

- Java 21
- Maven 3.8+

## Running the Service

```bash
mvn spring-boot:run
```

The service starts on port 8080. Swagger UI is available at: http://localhost:8080/swagger-ui.html

## API Endpoints

### POST /api/v1/credit/recommend-limit

Recommends a credit limit based on the applicant's financial profile.

```bash
curl -X POST http://localhost:8080/api/v1/credit/recommend-limit \
  -H "Content-Type: application/json" \
  -d '{
    "monthlyIncome": 800.000,
    "existingMonthlyDebt": 150.000,
    "accountAverageBalance": 2000.000,
    "employmentType": "EMPLOYED"
  }'
```

Example response:

```json
{
  "recommendedLimit": 2000.000,
  "riskTier": "LOW_RISK",
  "debtToIncomeRatio": 0.1875,
  "reasoning": "DTI ratio: 0.1875. Employment type: EMPLOYED. Risk tier: LOW_RISK. Base recommended limit: 2000.000 BHD."
}
```

### POST /api/v1/credit/repayment-schedule

Projects a repayment schedule with compound interest for an overdue balance.

```bash
curl -X POST http://localhost:8080/api/v1/credit/repayment-schedule \
  -H "Content-Type: application/json" \
  -d '{
    "outstandingBalance": 1000.000,
    "annualInterestRate": 24.0,
    "dueDateDaysAgo": 15,
    "projectionDays": 90
  }'
```

Example response:

```json
{
  "originalBalance": 1000.000,
  "currentAmountOwed": 1009.826,
  "dailyInterestRate": 0.0006575342,
  "schedule": [
    {
      "dayNumber": 0,
      "date": "2024-01-15",
      "totalOwed": 1009.826,
      "interestAccrued": 9.826,
      "percentageIncrease": 0.983
    }
  ]
}
```

## Configuration

| Property | Default | Description |
|---|---|---|
| `credit.engine.default-apr` | `24.0` | Default annual percentage rate used when not specified in request |

## Running Tests

```bash
mvn test
```
