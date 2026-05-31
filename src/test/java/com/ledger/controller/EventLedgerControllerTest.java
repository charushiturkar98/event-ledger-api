package com.ledger.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledger.dto.EventRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full integration tests — Spring context is loaded, H2 in-memory DB is used.
 * Each test method runs in a transaction that is rolled back after the test,
 * so tests are isolated from each other.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EventLedgerControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private EventRequest buildEvent(String eventId, String accountId, String type,
                                    double amount, String timestamp) {
        EventRequest req = new EventRequest();
        req.setEventId(eventId);
        req.setAccountId(accountId);
        req.setType(type);
        req.setAmount(BigDecimal.valueOf(amount));
        req.setCurrency("USD");
        req.setEventTimestamp(Instant.parse(timestamp));
        return req;
    }

    private void post(EventRequest req) throws Exception {
        mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)));
    }

    // ─── POST /events ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /events")
    class SubmitEvent {

        @Test
        @DisplayName("returns 201 for a new valid event")
        void newEventReturns201() throws Exception {
            EventRequest req = buildEvent("evt-001", "acct-001", "CREDIT", 100.00,
                    "2026-01-01T10:00:00Z");

            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.eventId").value("evt-001"))
                    .andExpect(jsonPath("$.accountId").value("acct-001"))
                    .andExpect(jsonPath("$.type").value("CREDIT"))
                    .andExpect(jsonPath("$.amount").value(100.00))
                    .andExpect(jsonPath("$.receivedAt").exists());
        }

        // ─── Idempotency ─────────────────────────────────────────────────────

        @Test
        @DisplayName("IDEMPOTENCY: duplicate eventId returns 200 with the original event")
        void duplicateEventReturns200() throws Exception {
            EventRequest req = buildEvent("evt-dup", "acct-001", "CREDIT", 200.00,
                    "2026-01-01T10:00:00Z");

            // First submission — 201
            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated());

            // Second submission of same eventId — 200, same body
            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.eventId").value("evt-dup"))
                    .andExpect(jsonPath("$.amount").value(200.00));
        }

        @Test
        @DisplayName("IDEMPOTENCY: duplicate submission does not alter the account balance")
        void duplicateDoesNotChangeBalance() throws Exception {
            EventRequest req = buildEvent("evt-bal", "acct-idem", "CREDIT", 500.00,
                    "2026-01-01T10:00:00Z");

            // Submit twice
            post(req);
            post(req);

            // Balance should reflect the amount only once
            mockMvc.perform(get("/accounts/acct-idem/balance"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.balance").value(500.00));
        }

        // ─── Validation ──────────────────────────────────────────────────────

        @Test
        @DisplayName("VALIDATION: missing eventId returns 400")
        void missingEventIdReturns400() throws Exception {
            EventRequest req = buildEvent(null, "acct-001", "CREDIT", 100.00,
                    "2026-01-01T10:00:00Z");

            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details", hasItem(containsString("eventId"))));
        }

        @Test
        @DisplayName("VALIDATION: missing accountId returns 400")
        void missingAccountIdReturns400() throws Exception {
            EventRequest req = buildEvent("evt-x", null, "CREDIT", 100.00,
                    "2026-01-01T10:00:00Z");

            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details", hasItem(containsString("accountId"))));
        }

        @Test
        @DisplayName("VALIDATION: zero amount returns 400")
        void zeroAmountReturns400() throws Exception {
            EventRequest req = buildEvent("evt-zero", "acct-001", "CREDIT", 0.00,
                    "2026-01-01T10:00:00Z");

            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details", hasItem(containsString("amount"))));
        }

        @Test
        @DisplayName("VALIDATION: negative amount returns 400")
        void negativeAmountReturns400() throws Exception {
            EventRequest req = buildEvent("evt-neg", "acct-001", "CREDIT", -50.00,
                    "2026-01-01T10:00:00Z");

            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details", hasItem(containsString("amount"))));
        }

        @Test
        @DisplayName("VALIDATION: unknown event type returns 400")
        void unknownTypeReturns400() throws Exception {
            EventRequest req = buildEvent("evt-type", "acct-001", "TRANSFER", 100.00,
                    "2026-01-01T10:00:00Z");

            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details", hasItem(containsString("CREDIT or DEBIT"))));
        }

        @Test
        @DisplayName("VALIDATION: missing eventTimestamp returns 400")
        void missingTimestampReturns400() throws Exception {
            EventRequest req = new EventRequest();
            req.setEventId("evt-ts");
            req.setAccountId("acct-001");
            req.setType("CREDIT");
            req.setAmount(BigDecimal.TEN);
            req.setCurrency("USD");
            // eventTimestamp intentionally omitted

            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details", hasItem(containsString("eventTimestamp"))));
        }

        @Test
        @DisplayName("VALIDATION: missing currency returns 400")
        void missingCurrencyReturns400() throws Exception {
            EventRequest req = new EventRequest();
            req.setEventId("evt-curr");
            req.setAccountId("acct-001");
            req.setType("CREDIT");
            req.setAmount(BigDecimal.TEN);
            req.setEventTimestamp(Instant.now());
            // currency intentionally omitted

            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details", hasItem(containsString("currency"))));
        }

        @Test
        @DisplayName("accepts optional metadata field")
        void acceptsMetadata() throws Exception {
            EventRequest req = buildEvent("evt-meta", "acct-001", "CREDIT", 75.00,
                    "2026-01-01T10:00:00Z");
            req.setMetadata(Map.of("source", "mainframe-batch", "batchId", "B-9042"));

            mockMvc.perform(post("/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.metadata.source").value("mainframe-batch"))
                    .andExpect(jsonPath("$.metadata.batchId").value("B-9042"));
        }
    }

    // ─── GET /events/{id} ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /events/{id}")
    class GetEvent {

        @Test
        @DisplayName("returns stored event by ID")
        void returnsEventById() throws Exception {
            post(buildEvent("evt-get", "acct-001", "DEBIT", 30.00, "2026-02-01T08:00:00Z"));

            mockMvc.perform(get("/events/evt-get"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.eventId").value("evt-get"))
                    .andExpect(jsonPath("$.type").value("DEBIT"))
                    .andExpect(jsonPath("$.amount").value(30.00));
        }

        @Test
        @DisplayName("returns 404 for unknown event ID")
        void returns404ForUnknownId() throws Exception {
            mockMvc.perform(get("/events/does-not-exist"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value(containsString("does-not-exist")));
        }
    }

    // ─── GET /events?account= ─────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /events?account=")
    class GetEventsForAccount {

        @Test
        @DisplayName("returns empty list for account with no events")
        void emptyListForUnknownAccount() throws Exception {
            mockMvc.perform(get("/events").param("account", "acct-empty"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("OUT-OF-ORDER: events are returned in eventTimestamp order regardless of arrival order")
        void outOfOrderEventsReturnedChronologically() throws Exception {
            String account = "acct-ooo";

            // Arrive in REVERSE chronological order
            post(buildEvent("evt-c", account, "CREDIT", 300.00, "2026-03-01T12:00:00Z"));
            post(buildEvent("evt-b", account, "CREDIT", 200.00, "2026-02-01T12:00:00Z"));
            post(buildEvent("evt-a", account, "CREDIT", 100.00, "2026-01-01T12:00:00Z"));

            // Must be returned in chronological order by eventTimestamp
            mockMvc.perform(get("/events").param("account", account))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(3)))
                    .andExpect(jsonPath("$[0].eventId").value("evt-a"))
                    .andExpect(jsonPath("$[1].eventId").value("evt-b"))
                    .andExpect(jsonPath("$[2].eventId").value("evt-c"));
        }

        @Test
        @DisplayName("OUT-OF-ORDER: late-arriving early event is inserted at correct position")
        void lateArrivingEarlyEventSlotsProperly() throws Exception {
            String account = "acct-late";

            // Submit later event first
            post(buildEvent("evt-late-b", account, "CREDIT", 200.00, "2026-06-01T00:00:00Z"));
            post(buildEvent("evt-late-c", account, "CREDIT", 300.00, "2026-09-01T00:00:00Z"));

            // Now submit an earlier event that arrived late
            post(buildEvent("evt-late-a", account, "CREDIT", 100.00, "2026-03-01T00:00:00Z"));

            mockMvc.perform(get("/events").param("account", account))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].eventId").value("evt-late-a"))
                    .andExpect(jsonPath("$[1].eventId").value("evt-late-b"))
                    .andExpect(jsonPath("$[2].eventId").value("evt-late-c"));
        }

        @Test
        @DisplayName("only returns events for the requested account")
        void isolatedByAccount() throws Exception {
            post(buildEvent("evt-x1", "acct-x", "CREDIT", 100.00, "2026-01-01T10:00:00Z"));
            post(buildEvent("evt-y1", "acct-y", "CREDIT", 999.00, "2026-01-01T10:00:00Z"));

            mockMvc.perform(get("/events").param("account", "acct-x"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].eventId").value("evt-x1"));
        }

        @Test
        @DisplayName("PAGINATION: returns correct page and metadata")
        void paginationWorksCorrectly() throws Exception {
            String account = "acct-page";
            for (int i = 1; i <= 5; i++) {
                post(buildEvent("evt-p" + i, account, "CREDIT", i * 10.0,
                        "2026-0" + i + "-01T00:00:00Z"));
            }

            // Page 0, size 2 — first two events
            mockMvc.perform(get("/events")
                            .param("account", account)
                            .param("page", "0")
                            .param("size", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.events", hasSize(2)))
                    .andExpect(jsonPath("$.events[0].eventId").value("evt-p1"))
                    .andExpect(jsonPath("$.events[1].eventId").value("evt-p2"))
                    .andExpect(jsonPath("$.totalElements").value(5))
                    .andExpect(jsonPath("$.totalPages").value(3))
                    .andExpect(jsonPath("$.page").value(0));

            // Page 1, size 2 — next two events
            mockMvc.perform(get("/events")
                            .param("account", account)
                            .param("page", "1")
                            .param("size", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.events[0].eventId").value("evt-p3"))
                    .andExpect(jsonPath("$.events[1].eventId").value("evt-p4"));
        }
    }

    // ─── GET /accounts/{accountId}/balance ────────────────────────────────────

    @Nested
    @DisplayName("GET /accounts/{accountId}/balance")
    class GetBalance {

        @Test
        @DisplayName("BALANCE: returns zero for account with no events")
        void zeroBalanceForEmptyAccount() throws Exception {
            mockMvc.perform(get("/accounts/acct-new/balance"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.balance").value(0));
        }

        @Test
        @DisplayName("BALANCE: credit-only account has positive balance")
        void creditOnlyBalance() throws Exception {
            String account = "acct-cr";
            post(buildEvent("evt-cr1", account, "CREDIT", 500.00, "2026-01-01T10:00:00Z"));
            post(buildEvent("evt-cr2", account, "CREDIT", 250.00, "2026-01-02T10:00:00Z"));

            mockMvc.perform(get("/accounts/" + account + "/balance"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.balance").value(750.00));
        }

        @Test
        @DisplayName("BALANCE: debit reduces balance correctly")
        void debitReducesBalance() throws Exception {
            String account = "acct-dr";
            post(buildEvent("evt-dr1", account, "CREDIT", 1000.00, "2026-01-01T10:00:00Z"));
            post(buildEvent("evt-dr2", account, "DEBIT",   300.00, "2026-01-02T10:00:00Z"));

            mockMvc.perform(get("/accounts/" + account + "/balance"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.balance").value(700.00));
        }

        @Test
        @DisplayName("BALANCE: negative balance is allowed (debits exceed credits)")
        void negativeBalanceIsAllowed() throws Exception {
            String account = "acct-neg";
            post(buildEvent("evt-neg1", account, "DEBIT", 500.00, "2026-01-01T10:00:00Z"));

            mockMvc.perform(get("/accounts/" + account + "/balance"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.balance").value(-500.00));
        }

        @Test
        @DisplayName("BALANCE: out-of-order arrivals do not affect balance correctness")
        void balanceCorrectRegardlessOfArrivalOrder() throws Exception {
            String account = "acct-bal-ooo";

            // Arrive out of order — timestamps deliberately mixed
            post(buildEvent("evt-bal3", account, "DEBIT",  200.00, "2026-03-01T00:00:00Z"));
            post(buildEvent("evt-bal1", account, "CREDIT", 1000.00, "2026-01-01T00:00:00Z"));
            post(buildEvent("evt-bal2", account, "CREDIT",  500.00, "2026-02-01T00:00:00Z"));
            post(buildEvent("evt-bal4", account, "DEBIT",   100.00, "2026-04-01T00:00:00Z"));

            // balance = (1000 + 500) - (200 + 100) = 1200
            mockMvc.perform(get("/accounts/" + account + "/balance"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.balance").value(1200.00));
        }

        @Test
        @DisplayName("BALANCE: duplicate submissions do not double-count")
        void duplicatesNotDoubleCounted() throws Exception {
            String account = "acct-dedup";
            EventRequest req = buildEvent("evt-dedup", account, "CREDIT", 400.00,
                    "2026-01-01T10:00:00Z");

            // Same event submitted three times
            post(req);
            post(req);
            post(req);

            mockMvc.perform(get("/accounts/" + account + "/balance"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.balance").value(400.00));
        }

        @Test
        @DisplayName("BALANCE: mixed debits and credits compute correct net")
        void mixedNetBalance() throws Exception {
            String account = "acct-mixed";
            post(buildEvent("evt-m1", account, "CREDIT", 1500.00, "2026-01-01T10:00:00Z"));
            post(buildEvent("evt-m2", account, "DEBIT",   450.75, "2026-01-02T10:00:00Z"));
            post(buildEvent("evt-m3", account, "CREDIT",  200.25, "2026-01-03T10:00:00Z"));
            post(buildEvent("evt-m4", account, "DEBIT",   100.00, "2026-01-04T10:00:00Z"));

            // (1500 + 200.25) - (450.75 + 100) = 1149.50
            mockMvc.perform(get("/accounts/" + account + "/balance"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.balance").value(1149.50));
        }
    }
}
