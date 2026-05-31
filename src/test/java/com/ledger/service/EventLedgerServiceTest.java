package com.ledger.service;

import com.ledger.dto.BalanceResponse;
import com.ledger.dto.EventRequest;
import com.ledger.dto.EventResponse;
import com.ledger.exception.EventNotFoundException;
import com.ledger.model.EventType;
import com.ledger.model.LedgerEvent;
import com.ledger.repository.LedgerEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EventLedgerService unit tests")
class EventLedgerServiceTest {

    @Mock
    LedgerEventRepository repository;

    EventLedgerService service;

    @BeforeEach
    void setUp() {
        service = new EventLedgerService(repository);
    }

    // ─── submitEvent ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("new event is saved and marked as not duplicate")
    void newEventIsSaved() {
        EventRequest req = request("evt-1", "acct-1", "CREDIT", 100.0);
        when(repository.findById("evt-1")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EventLedgerService.IngestResult result = service.submitEvent(req);

        assertThat(result.duplicate()).isFalse();
        assertThat(result.event().getEventId()).isEqualTo("evt-1");
        verify(repository).save(any(LedgerEvent.class));
    }

    @Test
    @DisplayName("duplicate eventId is detected, no second save is issued")
    void duplicateEventIsNotSaved() {
        LedgerEvent existing = event("evt-dup", "acct-1", EventType.CREDIT, 200.0);
        when(repository.findById("evt-dup")).thenReturn(Optional.of(existing));

        EventRequest req = request("evt-dup", "acct-1", "CREDIT", 200.0);
        EventLedgerService.IngestResult result = service.submitEvent(req);

        assertThat(result.duplicate()).isTrue();
        assertThat(result.event().getEventId()).isEqualTo("evt-dup");
        verify(repository, never()).save(any());
    }

    // ─── getEvent ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getEvent returns event when found")
    void getEventFound() {
        when(repository.findById("evt-1")).thenReturn(Optional.of(event("evt-1", "acct-1", EventType.DEBIT, 50.0)));

        EventResponse response = service.getEvent("evt-1");
        assertThat(response.getEventId()).isEqualTo("evt-1");
        assertThat(response.getType()).isEqualTo("DEBIT");
    }

    @Test
    @DisplayName("getEvent throws EventNotFoundException for unknown id")
    void getEventNotFound() {
        when(repository.findById("no-such")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getEvent("no-such"))
                .isInstanceOf(EventNotFoundException.class)
                .hasMessageContaining("no-such");
    }

    // ─── getBalance ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("balance = credits - debits")
    void balanceIsCreditsMinusDebits() {
        when(repository.sumAmountByAccountIdAndType("acct-1", EventType.CREDIT))
                .thenReturn(BigDecimal.valueOf(1000.0));
        when(repository.sumAmountByAccountIdAndType("acct-1", EventType.DEBIT))
                .thenReturn(BigDecimal.valueOf(300.0));
        when(repository.findByAccountIdOrderByEventTimestampAsc("acct-1"))
                .thenReturn(List.of(event("e1", "acct-1", EventType.CREDIT, 1000.0)));

        BalanceResponse balance = service.getBalance("acct-1");
        assertThat(balance.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(700.0));
    }

    @Test
    @DisplayName("balance defaults to 0 when no events exist")
    void zeroBalanceForNewAccount() {
        when(repository.sumAmountByAccountIdAndType(any(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(repository.findByAccountIdOrderByEventTimestampAsc("new-acct"))
                .thenReturn(List.of());

        BalanceResponse balance = service.getBalance("new-acct");
        assertThat(balance.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(balance.getCurrency()).isEqualTo("USD");
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private EventRequest request(String id, String account, String type, double amount) {
        EventRequest r = new EventRequest();
        r.setEventId(id);
        r.setAccountId(account);
        r.setType(type);
        r.setAmount(BigDecimal.valueOf(amount));
        r.setCurrency("USD");
        r.setEventTimestamp(Instant.now());
        return r;
    }

    private LedgerEvent event(String id, String account, EventType type, double amount) {
        return new LedgerEvent(id, account, type, BigDecimal.valueOf(amount),
                "USD", Instant.now(), Instant.now(), null);
    }
}
