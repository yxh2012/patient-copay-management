-- Complete Flyway migration script for patient copay payment system

-- Patient table
CREATE TABLE patient (
    id BIGSERIAL PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    phone VARCHAR(20),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Visit table
CREATE TABLE visit (
    id BIGSERIAL PRIMARY KEY,
    patient_id BIGINT NOT NULL REFERENCES patient(id),
    visit_date DATE NOT NULL,
    doctor_name VARCHAR(100) NOT NULL,
    department VARCHAR(100),
    visit_type VARCHAR(50) NOT NULL DEFAULT 'OFFICE_VISIT'
        CHECK (visit_type IN ('OFFICE_VISIT', 'SPECIALIST_VISIT', 'EMERGENCY_VISIT', 'TELEHEALTH')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Payment method table
CREATE TABLE payment_method (
    id BIGSERIAL PRIMARY KEY,
    patient_id BIGINT NOT NULL REFERENCES patient(id),
    type VARCHAR(50) NOT NULL
        CHECK (type IN ('CARD', 'BANK_ACCOUNT', 'DIGITAL_WALLET')),
    provider VARCHAR(50) NOT NULL,
    last_four VARCHAR(4) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(patient_id, type, provider, last_four)
);

-- Copay table
CREATE TABLE copay (
    id BIGSERIAL PRIMARY KEY,
    visit_id BIGINT NOT NULL REFERENCES visit(id),
    amount DECIMAL(10,2) NOT NULL CHECK (amount > 0),
    remaining_balance DECIMAL(10,2) NOT NULL CHECK (remaining_balance >= 0),
    status VARCHAR(50) NOT NULL DEFAULT 'PAYABLE'
        CHECK (status IN ('PAYABLE', 'PARTIALLY_PAID', 'PAID', 'WRITE_OFF')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_remaining_balance_valid CHECK (remaining_balance <= amount)
);

-- Payment table
CREATE TABLE payment (
    id BIGSERIAL PRIMARY KEY,
    patient_id BIGINT NOT NULL REFERENCES patient(id),
    payment_method_id BIGINT NOT NULL REFERENCES payment_method(id),
    amount DECIMAL(10,2) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED')),
    processor_charge_id VARCHAR(255) UNIQUE,
    request_key VARCHAR(255) NOT NULL UNIQUE,
    failure_code VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Payment allocation table
CREATE TABLE payment_allocation (
    id BIGSERIAL PRIMARY KEY,
    payment_id BIGINT NOT NULL REFERENCES payment(id) ON DELETE CASCADE,
    copay_id BIGINT NOT NULL REFERENCES copay(id),
    amount DECIMAL(10,2) NOT NULL CHECK (amount > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(payment_id, copay_id)
);

-- Patient credit balance table for overpayments
CREATE TABLE patient_credit (
    id BIGSERIAL PRIMARY KEY,
    patient_id BIGINT NOT NULL REFERENCES patient(id),
    amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(patient_id)
);

-- Credit transaction log for audit trail
CREATE TABLE credit_transaction (
    id BIGSERIAL PRIMARY KEY,
    patient_id BIGINT NOT NULL REFERENCES patient(id),
    payment_id BIGINT REFERENCES payment(id),
    amount DECIMAL(10,2) NOT NULL,
    transaction_type VARCHAR(20) NOT NULL CHECK (transaction_type IN ('CREDIT_APPLIED', 'OVERPAYMENT_CREDIT')),
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes
CREATE INDEX idx_patient_email ON patient(email);
CREATE INDEX idx_visit_patient_date ON visit(patient_id, visit_date DESC);
CREATE INDEX idx_copay_visit_status ON copay(visit_id, status);
CREATE INDEX idx_copay_status_amount ON copay(status, amount);
CREATE INDEX idx_copay_remaining_balance ON copay(remaining_balance);
CREATE INDEX idx_payment_patient_id ON payment(patient_id);
CREATE INDEX idx_payment_status ON payment(status);
CREATE INDEX idx_payment_request_key ON payment(request_key);
CREATE INDEX idx_payment_processor_charge_id ON payment(processor_charge_id) WHERE processor_charge_id IS NOT NULL;
CREATE INDEX idx_payment_method_patient_active ON payment_method(patient_id, is_active);
CREATE INDEX idx_payment_allocation_payment_id ON payment_allocation(payment_id);
CREATE INDEX idx_payment_allocation_copay_id ON payment_allocation(copay_id);
CREATE INDEX idx_patient_credit_patient_id ON patient_credit(patient_id);
CREATE INDEX idx_credit_transaction_patient_id ON credit_transaction(patient_id);
CREATE INDEX idx_credit_transaction_payment_id ON credit_transaction(payment_id);

-- Timestamp update function
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Triggers for automatic timestamp updates
CREATE TRIGGER update_patient_updated_at
    BEFORE UPDATE ON patient
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_visit_updated_at
    BEFORE UPDATE ON visit
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_payment_method_updated_at
    BEFORE UPDATE ON payment_method
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_copay_updated_at
    BEFORE UPDATE ON copay
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_payment_updated_at
    BEFORE UPDATE ON payment
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_patient_credit_updated_at
    BEFORE UPDATE ON patient_credit
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Comments for documentation
COMMENT ON TABLE patient IS 'Patient basic information';
COMMENT ON TABLE visit IS 'Healthcare visit that generate copay';
COMMENT ON TABLE copay IS 'Individual copay charges per visit with partial payment support';
COMMENT ON TABLE payment IS 'Payment transactions with processor integration';
COMMENT ON TABLE payment_allocation IS 'Allocation of payment to specific copay';
COMMENT ON TABLE patient_credit IS 'Patient credit balance from overpayments';
COMMENT ON TABLE credit_transaction IS 'Audit log of all credit transactions';

COMMENT ON COLUMN copay.amount IS 'Original copay amount in dollars (decimal)';
COMMENT ON COLUMN copay.remaining_balance IS 'Remaining unpaid amount for partial payments';
COMMENT ON COLUMN payment.amount IS 'Payment amount in dollars (decimal)';
COMMENT ON COLUMN payment_allocation.amount IS 'Allocated amount in dollars (decimal)';
COMMENT ON COLUMN patient_credit.amount IS 'Available credit balance from overpayments';


-- Sample data for testing the patient copay payment system
-- Insert sample patients
INSERT INTO patient (id, first_name, last_name, email, phone) VALUES
(1, 'John', 'Doe', 'john.doe@email.com', '555-0101'),
(2, 'Jane', 'Smith', 'jane.smith@email.com', '555-0102'),
(3, 'Bob', 'Johnson', 'bob.johnson@email.com', '555-0103');

-- Insert sample visits
INSERT INTO visit (id, patient_id, visit_date, doctor_name, department, visit_type) VALUES
(1, 1, '2024-01-15', 'Dr. Smith', 'Internal Medicine', 'OFFICE_VISIT'),
(2, 1, '2024-01-20', 'Dr. Lee', 'Cardiology', 'SPECIALIST_VISIT'),
(3, 1, '2024-02-10', 'Dr. Wilson', 'Emergency', 'EMERGENCY_VISIT'),
(4, 2, '2024-08-18', 'Dr. Brown', 'Pediatrics', 'SPECIALIST_VISIT'),
(5, 2, '2024-09-22', 'Dr. Davis', 'Family Medicine', 'TELEHEALTH'),
(6, 3, '2024-10-25', 'Dr. Miller', 'Dermatology', 'SPECIALIST_VISIT');

-- Insert sample payment methods
INSERT INTO payment_method (id, patient_id, type, provider, last_four, is_active) VALUES
(1, 1, 'CARD', 'VISA', '5656', true),
(2, 1, 'CARD', 'MASTERCARD', '4444', false),
(3, 1, 'CARD', 'AMEX', '8008', true),
(4, 2, 'BANK_ACCOUNT', 'CHASE', '1200', true),
(5, 3, 'DIGITAL_WALLET', 'Apple Pay', '----', true);

-- Insert sample copays with various payment states
INSERT INTO copay (id, visit_id, amount, remaining_balance, status) VALUES
(1, 1, 25.00, 25.00, 'PAYABLE'),         -- Dr. Smith visit - unpaid
(2, 2, 45.00, 45.00, 'PAYABLE'),         -- Dr. Lee visit - unpaid
(3, 3, 75.00, 75.00, 'PAYABLE'),         -- Dr. Wilson visit - unpaid
(4, 4, 35.00, 35.00, 'PAYABLE'),         -- Dr. Brown visit - unpaid
(5, 5, 20.00, 0.00, 'PAID'),             -- Dr. Davis visit - fully paid
(6, 6, 50.00, 0.00, 'WRITE_OFF');        -- Dr. Miller visit - written off

-- Insert patient credit balances
INSERT INTO patient_credit (patient_id, amount) VALUES
(1, 0.00),   -- John Doe    - no credit
(2, 10.00),  -- Jane Smith  - $10 credit from overpayment
(3, 30.00);  -- Bob Johnson - $30 credit

-- Insert sample payment (Paid)
INSERT INTO payment (id, patient_id, payment_method_id, amount, status, processor_charge_id, request_key) VALUES
(1, 2, 4, 20.00, 'SUCCEEDED', 'ch-id-999', 'req-key-999');

-- Insert sample payment allocation (Paid)
INSERT INTO payment_allocation (payment_id, copay_id, amount) VALUES
(1, 5, 20.00);  -- $20 payment toward $20 copay

-- Insert sample credit transaction
INSERT INTO credit_transaction (patient_id, payment_id, amount, transaction_type, description) VALUES
(2, NULL, 10.00, 'OVERPAYMENT_CREDIT', 'Credit from overpayment on previous visit');

-- Update sequences to match inserted IDs
SELECT setval('patient_id_seq', COALESCE((SELECT MAX(id) FROM patient), 0) + 1, false);
SELECT setval('visit_id_seq', COALESCE((SELECT MAX(id) FROM visit), 0) + 1, false);
SELECT setval('copay_id_seq', COALESCE((SELECT MAX(id) FROM copay), 0) + 1, false);
SELECT setval('payment_id_seq', COALESCE((SELECT MAX(id) FROM payment), 0) + 1, false);
SELECT setval('payment_allocation_id_seq', COALESCE((SELECT MAX(id) FROM payment_allocation), 0) + 1, false);