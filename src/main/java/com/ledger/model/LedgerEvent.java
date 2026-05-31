package com.ledger.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Persistent representation of a single financial transaction event.
 *
 * Two timestamps are stored intentionally:
 *  - eventTimestamp : when the event OCCURRED upstream (business time, caller-supplied)
 *  - receivedAt     : when this API received the event (wall-clock, set by us)
 *
 * eventId is the primary key — the database enforces uniqueness at the
 * storage layer, which prevents race conditions on concurrent duplicate POSTs.
 */
@Entity
@Table(name = "ledger_events")
public class LedgerEvent {

    /** Caller-supplied unique identifier. Primary key — enforces idempotency at DB level. */
    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private String eventId;

    @Column(name = "account_id", nullable = false, updatable = false)
    private String accountId;

    /** CREDIT or DEBIT */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false)
    private EventType type;

    /** Stored as DECIMAL(19,4) — never float, never lose pennies. */
    @Column(name = "amount", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    private String currency;

    /** Business time — used for ordering and balance computation. */
    @Column(name = "event_timestamp", nullable = false, updatable = false)
    private Instant eventTimestamp;

    /** Arrival time — audit trail, never used for business logic. */
    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "metadata", columnDefinition = "TEXT")
    @Convert(converter = com.ledger.model.MetadataConverter.class)
    private Map<String, Object> metadata;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    protected LedgerEvent() {}

    public LedgerEvent(String eventId,
                       String accountId,
                       EventType type,
                       BigDecimal amount,
                       String currency,
                       Instant eventTimestamp,
                       Instant receivedAt,
                       Map<String, Object> metadata) {
        this.eventId        = eventId;
        this.accountId      = accountId;
        this.type           = type;
        this.amount         = amount;
        this.currency       = currency;
        this.eventTimestamp = eventTimestamp;
        this.receivedAt     = receivedAt;
        this.metadata       = metadata;
    }

    // -------------------------------------------------------------------------
    // Getters (no setters — entity is immutable after creation)
    // -------------------------------------------------------------------------

    public String getEventId()          { return eventId; }
    public String getAccountId()        { return accountId; }
    public EventType getType()          { return type; }
    public BigDecimal getAmount()       { return amount; }
    public String getCurrency()         { return currency; }
    public Instant getEventTimestamp()  { return eventTimestamp; }
    public Instant getReceivedAt()      { return receivedAt; }
    public Map<String, Object> getMetadata() { return metadata; }
}
