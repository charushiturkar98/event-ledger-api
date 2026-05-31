package com.ledger.dto;

import com.ledger.model.LedgerEvent;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Schema(description = "Stored event representation")
public class EventResponse {

    private String eventId;
    private String accountId;
    private String type;
    private BigDecimal amount;
    private String currency;
    private Instant eventTimestamp;
    private Instant receivedAt;
    private Map<String, Object> metadata;

    public static EventResponse from(LedgerEvent event) {
        EventResponse r = new EventResponse();
        r.eventId        = event.getEventId();
        r.accountId      = event.getAccountId();
        r.type           = event.getType().name();
        r.amount         = event.getAmount();
        r.currency       = event.getCurrency();
        r.eventTimestamp = event.getEventTimestamp();
        r.receivedAt     = event.getReceivedAt();
        r.metadata       = event.getMetadata();
        return r;
    }

    // Getters
    public String getEventId()              { return eventId; }
    public String getAccountId()            { return accountId; }
    public String getType()                 { return type; }
    public BigDecimal getAmount()           { return amount; }
    public String getCurrency()             { return currency; }
    public Instant getEventTimestamp()      { return eventTimestamp; }
    public Instant getReceivedAt()          { return receivedAt; }
    public Map<String, Object> getMetadata(){ return metadata; }
}
