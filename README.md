# Copay Payment System

A **Spring Boot** application for managing patient copayments, visit records, and payment processing.The system simulates payment allocation, processor callbacks (webhooks), and integrates with **OpenAI** for AI-generated summaries of payment and webhook events.

## ✨ Features

- **Patient Management**: store patients, visits, and copay obligations.
- **Payment Processing**: submit payments, allocate them to copays, and handle processor webhooks.
- **Webhook Handling**: receive asynchronous processor events (SUCCEEDED, FAILED, etc.).
- **AI Summaries**: leverage OpenAI to generate natural language summary and recommendations for a patient
- **Database-backed**: uses PostgreSQL with schema migrations (Flyway).
- **REST API**: JSON-based endpoints for easy integration with frontends or other systems.

## 🏗️ Architecture

- **Spring Boot (3.x)** – Core application framework.
- **Spring Data JPA** – ORM with PostgreSQL.
- **Spring Web** – REST APIs.
- **Flyway** – Database migrations.
- **OpenAI Java SDK** – AI summaries.

## 📂 Project Structure
```text
src/main/java/com/yhou/demo
 ├── controller/       # REST controllers
 ├── dto/              # Request/response DTOs
 ├── entity/           # JPA entities
 ├── repository/       # Data access
 ├── service/          # Business logic + AI summary service
 └── DemoApplication   # Spring Boot main entrypoint
```

## ⚙️ Setup & Run

### 1\. Prerequisites

- Java 17+
- Maven 3.8+
- PostgreSQL

### 2\. Configure Environment

Set your OpenAI API key (required for AI summaries):

export OPENAI_API_KEY=sk-xxxxxx

For IntelliJ/IDE runs, add it to **Run Configurations → Environment Variables**.

### 3\. Database

Create a PostgreSQL database

Migrations will be applied automatically on startup using migration script (via Flyway migrations).

### 4\. Run the app

`  ./mvnw spring-boot:run  `

Server runs at: [http://localhost:8080](http://localhost:8080)

## 🧪 Sample Data

The project includes **seed data** for patients, visits, copays, payments, payment method (via Flyway migrations).

## 🔌 API Endpoints

### Payments

- POST /payments/{id}/payments – make payment to copays
- GET /api/v1/patients/{id}/copays – retrieve copay details
- GET /api/v1/patients/{id}/copayAISummary – AI-powered copay summary

### Webhooks

- POST /webhook/processor – Handles payment processor webhook events

## 🤖 AI Integration

AI is used in main way:

1.  **Copay/Payment Insights** – aggregate summaries of patient balances or pending payments.

Implementation is in:

- AISummaryService
- OpenAIClient
