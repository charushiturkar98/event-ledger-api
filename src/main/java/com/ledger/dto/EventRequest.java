package com.ledger.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Schema(description = "Financial transaction event payload")
public class EventRequest {

    @NotBlank(message = "eventId is required")
    @Schema(description = "Unique identifier for the event", example = "evt-001")
    private String eventId;

    @NotBlank(message = "accountId is required")
    @Schema(description = "Account this event belongs to", example = "acct-123")
    private String accountId;

    @NotBlank(message = "type is required")
    @Pattern(regexp = "CREDIT|DEBIT", message = "type must be CREDIT or DEBIT")
    @Schema(description = "Transaction direction", example = "CREDIT", allowableValues = {"CREDIT", "DEBIT"})
    private String type;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "amount must be greater than 0")
    @Schema(description = "Transaction amount (must be > 0)", example = "150.00")
    private BigDecimal amount;

    @NotBlank(message = "currency is required")
    @Schema(description = "ISO 4217 currency code", example = "USD")
    private String currency;

    @NotNull(message = "eventTimestamp is required")
    @Schema(description = "ISO 8601 timestamp of when the event occurred upstream",
            example = "2026-05-15T14:02:11Z")
    private Instant eventTimestamp;

    @Schema(description = "Optional metadata from the source system")
    private Map<String, Object> metadata;

    // -------------------------------------------------------------------------
    // Getters & Setters
    // -------------------------------------------------------------------------

    public String getEventId()              { return eventId; }
    public void setEventId(String v)        { this.eventId = v; }

    public String getAccountId()            { return accountId; }
    public void setAccountId(String v)      { this.accountId = v; }

    public String getType()                 { return type; }
    public void setType(String v)           { this.type = v; }

    public BigDecimal getAmount()           { return amount; }
    public void setAmount(BigDecimal v)     { this.amount = v; }

    public String getCurrency()             { return currency; }
    public void setCurrency(String v)       { this.currency = v; }

    public Instant getEventTimestamp()      { return eventTimestamp; }
    public void setEventTimestamp(Instant v){ this.eventTimestamp = v; }

    public Map<String, Object> getMetadata()           { return metadata; }
    public void setMetadata(Map<String, Object> v)     { this.metadata = v; }
}
