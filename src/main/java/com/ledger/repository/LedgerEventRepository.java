package com.ledger.repository;

import com.ledger.model.EventType;
import com.ledger.model.LedgerEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface LedgerEventRepository extends JpaRepository<LedgerEvent, String> {

    /**
     * Returns all events for an account in strict chronological order
     * (by eventTimestamp — business time, not arrival time).
     * Out-of-order arrivals are automatically handled here.
     */
    List<LedgerEvent> findByAccountIdOrderByEventTimestampAsc(String accountId);

    /**
     * Paginated variant — used by the bonus pagination feature.
     */
    Page<LedgerEvent> findByAccountIdOrderByEventTimestampAsc(String accountId, Pageable pageable);

    /**
     * Computes the sum of all amounts for a given account and event type.
     * Returns null (not zero) when no rows match — callers must handle this.
     */
    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM LedgerEvent e " +
           "WHERE e.accountId = :accountId AND e.type = :type")
    BigDecimal sumAmountByAccountIdAndType(
            @Param("accountId") String accountId,
            @Param("type") EventType type);

    boolean existsByEventId(String eventId);
}
