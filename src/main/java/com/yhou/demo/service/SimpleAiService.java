package com.yhou.demo.service;

import com.yhou.demo.dto.response.CopayAISummaryResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI-powered service for generating intelligent copay summaries and insights
 * for healthcare staff. Integrates with OpenAI's GPT models to provide
 * contextual financial analysis and actionable recommendations.
 *
 * Features:
 * - OpenAI integration with fallback to system-generated summaries
 * - Financial analysis of patient copay patterns
 * - Healthcare-specific insights and recommendations
 * - Graceful degradation when AI service is unavailable
 */
@Service
@Slf4j
public class SimpleAiService {

    private final WebClient webClient;

    /**
     * Initializes the AI service with OpenAI API configuration.
     *
     * @param apiKey OpenAI API key from configuration (defaults to "demo-key" for testing)
     */
    public SimpleAiService(@Value("${OPENAI_API_KEY:demo-key}") String apiKey) {
        this.webClient = WebClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();

        // Log API key info for debugging (safely truncated)
        log.info("API Key loaded: '{}' (length: {}, starts with sk-: {})",
                apiKey.substring(0, Math.min(10, apiKey.length())), apiKey.length(), apiKey.startsWith("sk-"));
    }

    /**
     * Generates an AI-powered copay summary with financial insights and recommendations.
     * Falls back to system-generated summary if AI service fails or is unavailable.
     *
     * @param copays list of patient copays to analyze
     * @param patientName patient name for personalized summary
     * @return structured copay summary response with insights and recommendations
     */
    public CopayAISummaryResponse generateCopaySummary(List<com.yhou.demo.dto.CopayDTO> copays, String patientName) {
        if (copays == null || copays.isEmpty()) {
            return createEmptyResponse(patientName);
        }

        try {
            // Build contextual prompt with financial data
            String prompt = buildCopayPrompt(copays, patientName);

            // Prepare OpenAI API request
            Map<String, Object> request = Map.of(
                    "model", "gpt-3.5-turbo",
                    "messages", List.of(
                            Map.of("role", "system", "content", "You are a healthcare financial assistant. Provide helpful, professional insights about patient copay status."),
                            Map.of("role", "user", "content", prompt)
                    ),
                    "max_tokens", 200
            );

            // Call OpenAI API with timeout protection
            Map<String, Object> response = webClient
                    .post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .timeout(Duration.ofSeconds(10))
                    .block();

            return parseAIResponseToStructured(response, copays, patientName);

        } catch (Exception e) {
            log.warn("AI failed, using fallback: {}", e.getMessage());
            return createStructuredFallback(copays, patientName);
        }
    }

    /**
     * Builds a comprehensive prompt for AI analysis including financial summaries
     * and healthcare context to generate relevant insights.
     */
    private String buildCopayPrompt(List<com.yhou.demo.dto.CopayDTO> copays, String patientName) {
        // Calculate financial metrics for AI context
        BigDecimal totalAmount = copays.stream()
                .map(com.yhou.demo.dto.CopayDTO::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalOutstanding = copays.stream()
                .map(com.yhou.demo.dto.CopayDTO::getRemainingBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPaid = totalAmount.subtract(totalOutstanding);

        // Count copays by payment status
        long unpaidCount = copays.stream()
                .filter(c -> c.getRemainingBalance().equals(c.getAmount()))
                .count();

        long paidCount = copays.stream()
                .filter(com.yhou.demo.dto.CopayDTO::isFullyPaid)
                .count();

        long partiallyPaidCount = copays.stream()
                .filter(com.yhou.demo.dto.CopayDTO::isPartiallyPaid)
                .count();

        // Extract department diversity for healthcare context
        String departments = copays.stream()
                .map(com.yhou.demo.dto.CopayDTO::getDepartment)
                .distinct()
                .limit(3)
                .reduce((a, b) -> a + ", " + b)
                .orElse("Various");

        // Build structured prompt for consistent AI responses
        return String.format(
                "Analyze copay status for patient %s:\n\n" +
                        "Financial Summary:\n" +
                        "- Total copays: %d (Amount: $%.2f)\n" +
                        "- Outstanding balance: $%.2f\n" +
                        "- Total paid: $%.2f\n" +
                        "- Unpaid copays: %d\n" +
                        "- Paid copays: %d\n" +
                        "- Partially paid: %d\n" +
                        "- Recent departments: %s\n\n" +
                        "Provide a brief summary with 2-3 key insights and actionable recommendations for this patient's copay situation. " +
                        "Keep it professional and helpful for healthcare staff.",
                patientName, copays.size(), totalAmount, totalOutstanding, totalPaid,
                unpaidCount, paidCount, partiallyPaidCount, departments
        );
    }

    /**
     * Extracts text content from OpenAI API response with error handling.
     */
    @SuppressWarnings("unchecked")
    private String extractResponse(Map<String, Object> response) {
        try {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            return (String) message.get("content");
        } catch (Exception e) {
            return "AI response parsing failed.";
        }
    }

    /**
     * Creates empty response for patients with no copays.
     */
    private CopayAISummaryResponse createEmptyResponse(String patientName) {
        return CopayAISummaryResponse.builder()
                .patientId(0L)
                .patientName(patientName)
                .generatedAt(LocalDateTime.now())
                .accountStatus("No copays found")
                .financialOverview(CopayAISummaryResponse.FinancialOverview.builder()
                        .outstandingBalance("$0.00")
                        .totalAmount("$0.00")
                        .totalCopays(0)
                        .paidCopays(0)
                        .unpaidCopays(0)
                        .partiallyPaidCopays(0)
                        .build())
                .recommendations(List.of("No copays to review"))
                .insights(List.of())
                .summarySource("SYSTEM")
                .build();
    }

    /**
     * Converts AI text response to structured summary format.
     */
    private CopayAISummaryResponse parseAIResponseToStructured(Map<String, Object> response, List<com.yhou.demo.dto.CopayDTO> copays, String patientName) {
        String aiText = extractResponse(response);

        // Parse AI response and create structured summary
        return createStructuredResponse(copays, patientName, parseAIInsights(aiText), "AI");
    }

    /**
     * Parses bullet points and recommendations from AI text response.
     * Looks for common bullet point formats (-, •, *) to extract actionable items.
     */
    private List<String> parseAIInsights(String aiText) {
        return Arrays.stream(aiText.split("\\n"))
                .map(String::trim)
                .filter(line -> line.startsWith("-") || line.startsWith("•") || line.startsWith("*"))
                .map(line -> line.replaceFirst("^[-•*]\\s*", ""))
                .filter(line -> !line.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * Creates fallback response when AI service is unavailable.
     */
    private CopayAISummaryResponse createStructuredFallback(List<com.yhou.demo.dto.CopayDTO> copays, String patientName) {
        List<String> recommendations = generateSystemRecommendations(copays);
        return createStructuredResponse(copays, patientName, recommendations, "SYSTEM");
    }

    /**
     * Builds structured response with financial analysis and recommendations.
     * Used for both AI and system-generated summaries to ensure consistent format.
     */
    private CopayAISummaryResponse createStructuredResponse(List<com.yhou.demo.dto.CopayDTO> copays, String patientName, List<String> recommendations, String source) {
        // Recalculate financial metrics for response
        BigDecimal totalAmount = copays.stream()
                .map(com.yhou.demo.dto.CopayDTO::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalOutstanding = copays.stream()
                .map(com.yhou.demo.dto.CopayDTO::getRemainingBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Count copays by status for summary
        long unpaidCount = copays.stream()
                .filter(c -> c.getRemainingBalance().equals(c.getAmount()))
                .count();

        long paidCount = copays.stream()
                .filter(com.yhou.demo.dto.CopayDTO::isFullyPaid)
                .count();

        long partiallyPaidCount = copays.stream()
                .filter(com.yhou.demo.dto.CopayDTO::isPartiallyPaid)
                .count();

        // Determine account status based on outstanding balance
        String accountStatus;
        if (totalOutstanding.compareTo(BigDecimal.ZERO) == 0) {
            accountStatus = "All copays are current";
        } else if (unpaidCount > 3) {
            accountStatus = "Multiple outstanding copays need attention";
        } else {
            accountStatus = "Some outstanding balances";
        }

        return CopayAISummaryResponse.builder()
                .patientId(0L) // Will be set in controller
                .patientName(patientName)
                .generatedAt(LocalDateTime.now())
                .accountStatus(accountStatus)
                .financialOverview(CopayAISummaryResponse.FinancialOverview.builder()
                        .outstandingBalance(String.format("$%.2f", totalOutstanding))
                        .totalAmount(String.format("$%.2f", totalAmount))
                        .totalCopays(copays.size())
                        .paidCopays((int) paidCount)
                        .unpaidCopays((int) unpaidCount)
                        .partiallyPaidCopays((int) partiallyPaidCount)
                        .build())
                .recommendations(recommendations)
                .insights(generateSystemInsights(copays))
                .summarySource(source)
                .build();
    }

    /**
     * Generates system-based recommendations when AI is unavailable.
     * Uses business rules to provide actionable guidance for healthcare staff.
     */
    private List<String> generateSystemRecommendations(List<com.yhou.demo.dto.CopayDTO> copays) {
        List<String> recommendations = new ArrayList<>();

        BigDecimal totalOutstanding = copays.stream()
                .map(com.yhou.demo.dto.CopayDTO::getRemainingBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long unpaidCount = copays.stream()
                .filter(c -> c.getRemainingBalance().equals(c.getAmount()))
                .count();

        // Generate recommendations based on financial analysis
        if (totalOutstanding.compareTo(BigDecimal.ZERO) > 0) {
            recommendations.add("Consider setting up a payment plan for outstanding balances");
            if (unpaidCount > 2) {
                recommendations.add("Prioritize oldest unpaid copays first");
            }
            if (totalOutstanding.compareTo(BigDecimal.valueOf(100)) > 0) {
                recommendations.add("Contact patient about large outstanding balance");
            }
        } else {
            recommendations.add("Account is current - continue good payment practices");
        }

        return recommendations;
    }

    /**
     * Generates healthcare-specific insights based on copay patterns.
     * Analyzes payment behavior and healthcare utilization for staff guidance.
     */
    private List<String> generateSystemInsights(List<com.yhou.demo.dto.CopayDTO> copays) {
        List<String> insights = new ArrayList<>();

        // Calculate average copay amount for comparison
        BigDecimal averageAmount = copays.stream()
                .map(com.yhou.demo.dto.CopayDTO::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(Math.max(1, copays.size())), 2, RoundingMode.HALF_UP);

        if (averageAmount.compareTo(BigDecimal.valueOf(50)) > 0) {
            insights.add("Higher than average copay amounts detected");
        }

        // Analyze payment behavior patterns
        long partiallyPaidCount = copays.stream()
                .filter(com.yhou.demo.dto.CopayDTO::isPartiallyPaid)
                .count();

        if (partiallyPaidCount > 0) {
            insights.add("Patient has made partial payments - shows payment intent");
        }

        // Assess healthcare utilization diversity
        long uniqueDepartments = copays.stream()
                .map(com.yhou.demo.dto.CopayDTO::getDepartment)
                .distinct()
                .count();

        if (uniqueDepartments > 2) {
            insights.add("Patient visits multiple departments - comprehensive care");
        }

        return insights;
    }
}