package com.yhou.demo.entity.enums;

/**
 * Enumeration of credit transaction types for patient account operations.
 */
public enum CreditTransactionType {
    CREDIT_APPLIED,    // Credit was used to pay copay
    OVERPAYMENT_CREDIT // Overpayment converted to credit
}