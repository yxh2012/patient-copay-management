# Patient Copay Payment API Documentation

## Base URL
`http://localhost:8080/api/v1`

## Endpoints

### 1. List Patient Copays
**GET** `/patients/{id}/copays`

**Description:** Retrieve all copays for a patient with optional status filtering.

**Path Parameters:**
- `id` (required): Patient ID

**Query Parameters:**
- `status` (optional): Filter by copay status
    - Values: payable, paid, write_off, partially_paid

**Response:** List of copays with financial summary including total amounts and payment statistics.

---

### 2. Submit Payment
**POST** `/patients/{id}/payments`

**Description:** Submit a payment that can be allocated across one or more copays.

**Path Parameters:**
- `id` (required): Patient ID

**Headers:**
- `Content-Type: application/json`
- `Duplicate-Request-Key` (optional): Idempotency key to prevent duplicate charges

**Request Body:** Contains paymentMethodId, currency, and allocations array with copayId and amount for each copay.

**Response:** Payment processing results with payment ID and status.

---

### 3. AI Copay Summary
**GET** `/patients/{id}/copayAISummary`

**Description:** Generate AI-powered copay summary with financial insights and payment recommendations.

**Path Parameters:**
- `id` (required): Patient ID

**Response:** AI-generated analysis including:
- Account status overview
- Financial summary with outstanding balances
- Personalized payment recommendations
- Insights into payment patterns

---

### 4. Payment Webhook
**POST** `/webhooks/processor`

**Description:** Handle payment processor callbacks for charge status updates.

**Headers:**
- `Content-Type: application/json`

**Request Body:** Contains event type, processor charge ID, amount, and optional failure code.

**Supported Event Types:**
- charge.succeeded - Payment completed successfully
- charge.failed - Payment failed with failure code

**Response:** Empty 200 response on successful processing.