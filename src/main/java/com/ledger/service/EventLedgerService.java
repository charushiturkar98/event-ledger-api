package com.ledger.service;

import com.ledger.dto.BalanceResponse;
import com.ledger.dto.EventRequest;
import com.ledger.dto.EventResponse;
import com.ledger.dto.PagedEventResponse;
import com.ledger.exception.EventNotFoundException;
import com.ledger.model.EventType;
import com.ledger.model.LedgerEvent;
import com.ledger.repository.LedgerEventRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class EventLedgerService {

    private final LedgerEventRepository repository;

    public EventLedgerService(LedgerEventRepository repository) {
        this.repository = repository;
    }

    // -------------------------------------------------------------------------
    // Submit event
    //
    // Idempotency strategy:
    //   - The eventId is the DB primary key. A second INSERT for the same key
    //     will throw a DataIntegrityViolationException, which we catch and
    //     convert to a "duplicate" response.
    //   - We also do an explicit pre-check so we can return the original event
    //     cleanly without relying on exception control flow for the happy path.
    //
    // The @Transactional annotation ensures the read + write are atomic,
    // preventing a TOCTOU race where two concurrent requests both see "not found"
    // and both attempt to insert. The DB PK constraint is the final safety net.
    // -------------------------------------------------------------------------

    @Transactional
    public IngestResult submitEvent(EventRequest request) {
        // Idempotency check — return original if already stored
        Optional<LedgerEvent> existing = repository.findById(request.getEventId());
        if (existing.isPresent()) {
            return new IngestResult(EventResponse.from(existing.get()), true);
        }

        LedgerEvent event = new LedgerEvent(
                request.getEventId(),
                request.getAccountId(),
                EventType.valueOf(request.getType()),
                request.getAmount(),
                request.getCurrency(),
                request.getEventTimestamp(),
                Instant.now(),            // receivedAt — wall-clock, set by us
                request.getMetadata()
        );

        try {
            LedgerEvent saved = repository.saveAndFlush(event);
            return new IngestResult(EventResponse.from(saved), false);
        } catch (DataIntegrityViolationException e) {
            // A concurrent thread won the race and inserted first.
            // The PK constraint fired — recover by fetching and returning the winner's event.
            LedgerEvent stored = repository.findById(request.getEventId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Race condition: insert failed but event not found: " + request.getEventId()));
            return new IngestResult(EventResponse.from(stored), true);
        }
    }

    // -------------------------------------------------------------------------
    // Retrieve single event by ID
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public EventResponse getEvent(String eventId) {
        return repository.findById(eventId)
                .map(EventResponse::from)
                .orElseThrow(() -> new EventNotFoundException(eventId));
    }

    // -------------------------------------------------------------------------
    // List events for account
    //
    // Results are always ordered by eventTimestamp ASC (business time).
    // A late-arriving event with an earlier timestamp slots into the correct
    // chronological position automatically — no special logic required because
    // we sort on eventTimestamp, not receivedAt.
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<EventResponse> getEventsForAccount(String accountId) {
        return repository.findByAccountIdOrderByEventTimestampAsc(accountId)
                .stream()
                .map(EventResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PagedEventResponse getEventsForAccountPaged(String accountId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<LedgerEvent> result = repository.findByAccountIdOrderByEventTimestampAsc(accountId, pageable);

        List<EventResponse> events = result.getContent()
                .stream()
                .map(EventResponse::from)
                .collect(Collectors.toList());

        return new PagedEventResponse(events, page, size,
                result.getTotalElements(), result.getTotalPages());
    }

    // -------------------------------------------------------------------------
    // Balance computation
    //
    // balance = SUM(CREDIT amounts) - SUM(DEBIT amounts)
    //
    // Because we store all events and never update them, the balance is always
    // recomputed from the full ledger. Out-of-order arrivals don't affect
    // correctness — CREDIT/DEBIT sums are commutative.
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public BalanceResponse getBalance(String accountId) {
        BigDecimal totalCredits = repository.sumAmountByAccountIdAndType(accountId, EventType.CREDIT);
        BigDecimal totalDebits  = repository.sumAmountByAccountIdAndType(accountId, EventType.DEBIT);

        BigDecimal balance = totalCredits.subtract(totalDebits);

        // Derive currency from the account's events; default to "USD" if no events yet.
        String currency = repository.findByAccountIdOrderByEventTimestampAsc(accountId)
                .stream()
                .findFirst()
                .map(LedgerEvent::getCurrency)
                .orElse("USD");

        return new BalanceResponse(accountId, balance, currency);
    }

    // -------------------------------------------------------------------------
    // Inner result type — carries the stored event + a duplicate flag
    // -------------------------------------------------------------------------

    public record IngestResult(EventResponse event, boolean duplicate) {}
}
