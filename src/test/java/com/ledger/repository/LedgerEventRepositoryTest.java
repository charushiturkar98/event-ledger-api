package com.ledger.repository;

import com.ledger.model.EventType;
import com.ledger.model.LedgerEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("LedgerEventRepository tests")
class LedgerEventRepositoryTest {

    @Autowired
    LedgerEventRepository repository;

    // ─── findByAccountId (ordering) ──────────────────────────────────────────

    @Test
    @DisplayName("findByAccountIdOrderByEventTimestampAsc returns events in chronological order")
    void returnsEventsInChronologicalOrder() {
        String acct = "acct-order";

        // Insert in REVERSE order
        save("e3", acct, EventType.CREDIT, 300.0, "2026-03-01T00:00:00Z");
        save("e1", acct, EventType.CREDIT, 100.0, "2026-01-01T00:00:00Z");
        save("e2", acct, EventType.CREDIT, 200.0, "2026-02-01T00:00:00Z");

        List<LedgerEvent> results = repository.findByAccountIdOrderByEventTimestampAsc(acct);

        assertThat(results).extracting(LedgerEvent::getEventId)
                .containsExactly("e1", "e2", "e3");
    }

    @Test
    @DisplayName("findByAccountId only returns events for that account")
    void filtersByAccountId() {
        save("ea1", "account-a", EventType.CREDIT, 100.0, "2026-01-01T00:00:00Z");
        save("eb1", "account-b", EventType.CREDIT, 999.0, "2026-01-01T00:00:00Z");

        List<LedgerEvent> results = repository.findByAccountIdOrderByEventTimestampAsc("account-a");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getEventId()).isEqualTo("ea1");
    }

    // ─── sumAmountByAccountIdAndType ─────────────────────────────────────────

    @Test
    @DisplayName("sumAmountByAccountIdAndType correctly sums CREDIT amounts")
    void sumsCreditAmounts() {
        save("c1", "acct-sum", EventType.CREDIT,  500.0, "2026-01-01T00:00:00Z");
        save("c2", "acct-sum", EventType.CREDIT,  250.0, "2026-02-01T00:00:00Z");
        save("d1", "acct-sum", EventType.DEBIT,   100.0, "2026-03-01T00:00:00Z");

        BigDecimal credits = repository.sumAmountByAccountIdAndType("acct-sum", EventType.CREDIT);
        assertThat(credits).isEqualByComparingTo(BigDecimal.valueOf(750.0));
    }

    @Test
    @DisplayName("sumAmountByAccountIdAndType correctly sums DEBIT amounts")
    void sumsDebitAmounts() {
        save("c1", "acct-dsum", EventType.CREDIT, 1000.0, "2026-01-01T00:00:00Z");
        save("d1", "acct-dsum", EventType.DEBIT,   300.0, "2026-02-01T00:00:00Z");
        save("d2", "acct-dsum", EventType.DEBIT,   150.0, "2026-03-01T00:00:00Z");

        BigDecimal debits = repository.sumAmountByAccountIdAndType("acct-dsum", EventType.DEBIT);
        assertThat(debits).isEqualByComparingTo(BigDecimal.valueOf(450.0));
    }

    @Test
    @DisplayName("sumAmountByAccountIdAndType returns 0 for account with no events")
    void returnsZeroForEmptyAccount() {
        BigDecimal result = repository.sumAmountByAccountIdAndType("acct-empty", EventType.CREDIT);
        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ─── existsByEventId ─────────────────────────────────────────────────────

    @Test
    @DisplayName("existsByEventId returns true for stored event, false otherwise")
    void existsByEventId() {
        save("evt-exists", "acct-1", EventType.CREDIT, 100.0, "2026-01-01T00:00:00Z");

        assertThat(repository.existsByEventId("evt-exists")).isTrue();
        assertThat(repository.existsByEventId("evt-ghost")).isFalse();
    }

    // ─── Helper ──────────────────────────────────────────────────────────────

    private void save(String id, String account, EventType type, double amount, String ts) {
        repository.save(new LedgerEvent(
                id, account, type, BigDecimal.valueOf(amount),
                "USD", Instant.parse(ts), Instant.now(), null));
    }
}
