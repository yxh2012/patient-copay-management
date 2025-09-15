# Patient Copay Payment API Documentation

## Base URL
`http://localhost:8080/api/v1`

## Authentication
No authentication required for this demonstration project.

## Common Response Codes
- `200 OK` - Request successful
- `201 Created` - Resource created successfully
- `400 Bad Request` - Invalid input or malformed request
- `404 Not Found` - Patient, copay, or payment method not found
- `409 Conflict` - Duplicate request detected
- `422 Unprocessable Entity` - Business validation failed
- `500 Internal Server Error` - Unexpected system error

## Error Response Format
All errors return a consistent JSON structure:
```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "path": "/api/v1/patients/123/payments",
  "errorCode": "RESOURCE_NOT_FOUND",
  "message": "Patient with ID 123 not found",
  "retryable": false
}
```

---

## Endpoints

### 1. List Patient Copays
**GET** `/patients/{id}/copays`

Retrieve all copays for a patient with optional status filtering, including comprehensive financial summaries.

**Path Parameters:**
- `id` (Long, required) - Patient identifier

**Query Parameters:**
- `status` (String, optional) - Filter by copay status
    - Allowed values: `payable`, `paid`, `write_off`, `partially_paid`

**Example Request:**
```
GET /api/v1/patients/1/copays?status=payable
```

**Success Response (200):**
```json
{
  "copays": [
    {
      "id": 3,
      "visitId": 3,
      "patientId": 1,
      "amount": 75.00,
      "remainingBalance": 75.00,
      "status": "PAYABLE",
      "visitDate": "2024-02-10",
      "doctorName": "Dr. Wilson",
      "department": "Emergency",
      "visitType": "EMERGENCY_VISIT",
      "createdAt": "2025-09-14T22:19:32.733046",
      "fullyPaid": false,
      "partiallyPaid": false,
      "paidAmount": 0.00
    },
    {
      "id": 2,
      "visitId": 2,
      "patientId": 1,
      "amount": 45.00,
      "remainingBalance": 45.00,
      "status": "PAYABLE",
      "visitDate": "2024-01-20",
      "doctorName": "Dr. Lee",
      "department": "Cardiology",
      "visitType": "SPECIALIST_VISIT",
      "createdAt": "2025-09-14T22:19:32.733046",
      "fullyPaid": false,
      "partiallyPaid": false,
      "paidAmount": 0.00
    }
  ],
  "totalAmount": 145.00,
  "totalRemainingBalance": 145.00,
  "totalPaidAmount": 0.00,
  "count": 3,
  "summary": {
    "fullyPaidCount": 0,
    "partiallyPaidCount": 0,
    "unpaidCount": 3,
    "writeOffCount": 0
  }
}
```

**Response Fields:**
- `copays` - Array of copay objects with visit details and payment status
- `totalAmount` - Sum of all copay amounts
- `totalRemainingBalance` - Total outstanding balance
- `totalPaidAmount` - Total amount paid across all copays
- `count` - Number of copays returned
- `summary` - Count breakdown by payment status

---

### 2. Submit Payment
**POST** `/patients/{id}/payments`

Submit a payment that can be allocated across one or more copays.

**Path Parameters:**
- `id` (Long, required) - Patient identifier

**Headers:**
- `Content-Type: application/json` (required)
- `Duplicate-Request-Key` (String, optional) - Idempotency key to prevent duplicate charges

**Request Body:**
```json
{
  "paymentMethodId": 1,
  "currency": "USD",
  "allocations": [
    {
      "copayId": 1,
      "amount": 25.00
    },
    {
      "copayId": 2,
      "amount": 45.00
    }
  ]
}
```

**Field Descriptions:**
- `paymentMethodId` (Long, required) - ID of patient's payment method
- `currency` (String, required) - Currency code (currently only "USD" supported)
- `allocations` (Array, required) - List of copay allocations
    - `copayId` (Long, required) - ID of copay to pay
    - `amount` (Decimal, required) - Amount to allocate to this copay

**Success Response (200):**
```json
{
  "paymentId": 456,
  "status": "PENDING"
}
```

**Business Rules:**
- Supports partial payments (amount less than remaining balance)
- Supports overpayments (amount greater than remaining balance creates patient credit)
- Payment processing is asynchronous - status updates via webhook
- Idempotency prevents duplicate payments with same request key

---

### 3. AI Copay Summary
**GET** `/patients/{id}/copayAISummary`

Generate AI-powered copay summary with financial insights and payment recommendations.

**Path Parameters:**
- `id` (Long, required) - Patient identifier

**Example Request:**
```
GET /api/v1/patients/1/copayAISummary
```

**Success Response (200):**
```json
{
  "patientId": 1,
  "patientName": "John Doe",
  "generatedAt": "2024-01-15T10:30:00Z",
  "accountStatus": "Some outstanding balances",
  "financialOverview": {
    "outstandingBalance": "$145.00",
    "totalAmount": "$145.00",
    "totalCopays": 3,
    "paidCopays": 0,
    "unpaidCopays": 3,
    "partiallyPaidCopays": 0
  },
  "recommendations": [
    "Consider setting up a payment plan for outstanding balances",
    "Prioritize oldest unpaid copays first"
  ],
  "insights": [
    "Patient visits multiple departments - comprehensive care",
    "Higher than average copay amounts detected"
  ],
  "summarySource": "AI"
}
```

**Features:**
- Uses OpenAI GPT-3.5-turbo for intelligent analysis
- Fallback to system-generated summaries if AI unavailable
- Analyzes payment patterns and provides actionable recommendations
- Healthcare-specific insights based on visit patterns

---

### 4. Payment Webhook
**POST** `/webhooks/processor`

Handle payment processor callbacks for charge status updates. This endpoint is called by the payment processor simulation.

**Headers:**
- `Content-Type: application/json` (required)

**Request Body:**
```json
{
  "type": "charge.succeeded",
  "processorChargeId": "ch_abc123",
  "amount": 70.00,
  "failureCode": null
}
```

**Field Descriptions:**
- `type` (String, required) - Event type
    - `charge.succeeded` - Payment completed successfully
    - `charge.failed` - Payment failed
- `processorChargeId` (String, required) - Unique charge identifier from processor
- `amount` (Decimal, required) - Payment amount
- `failureCode` (String, optional) - Failure reason if payment failed
    - Examples: `card_declined`, `insufficient_funds`, `card_expired`

**Success Response (200):**
Empty response body with 200 status code.

**Webhook Processing:**
- Updates payment status (PENDING → SUCCEEDED/FAILED)
- Updates copay remaining balances and status
- Handles credit reversals for failed overpayments
- Implements duplicate webhook protection
- All database changes are transactional

---

## Payment Processing Flow

1. **Submit Payment** - Client calls POST `/patients/{id}/payments`
2. **Immediate Response** - API returns payment ID with "PENDING" status
3. **Async Processing** - Payment submitted to processor simulation (2-5 second delay)
4. **Webhook Callback** - Processor calls POST `/webhooks/processor` with result
5. **Status Update** - Payment and copay statuses updated in database
6. **Client Polling** - Client can query copays to see updated payment status

## Idempotency

All payment submissions support idempotency via the `Duplicate-Request-Key` header. Submitting the same request key multiple times will return the same payment result without creating duplicate charges.

## Rate Limiting

No rate limiting implemented in this demonstration version. Production deployment would include appropriate throttling.