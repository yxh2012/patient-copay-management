package com.yhou.demo.constants;

/**
 * Application-wide constants for the patient copay payment system.
 * TODO: Extract more hardcoded strings to improve maintainability
 */
public final class ApplicationConstants {

    // Webhook Events
    public static final String CHARGE_SUCCEEDED = "charge.succeeded";
    public static final String CHARGE_FAILED = "charge.failed";

    // Payment Processor
    public static final String DEFAULT_CURRENCY = "USD";

    // Business Rules
    public static final int OVERPAYMENT_MULTIPLIER = 5;
    public static final String OVERPAYMENT_DESCRIPTION = "Overpayment credit from payment %d";

    // TODO: Extract remaining hardcoded strings (error messages, failure codes, etc.)

    private ApplicationConstants() {
        // Utility class - prevent instantiation
    }
}