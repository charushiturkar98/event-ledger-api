package com.ledger.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledger.dto.EventRequest;
import com.ledger.repository.LedgerEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Concurrency test — NOT transactional, because we need real commits so that
 * concurrent threads see each other's writes. The repository is cleaned up
 * manually after each test.
 *
 * This test verifies that simultaneous POSTs for the same eventId:
 *  - Result in exactly one stored event
 *  - Do not corrupt the account balance
 *  - Each return either 201 (winner) or 200 (duplicate), never 500
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConcurrencyTest {

    private static final int THREAD_COUNT = 20;

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired LedgerEventRepository repository;

    @Test
    @DisplayName("CONCURRENCY: simultaneous POSTs for same eventId store exactly one event")
    void concurrentDuplicatesStoredOnce() throws Exception {
        String eventId  = "evt-concurrent";
        String accountId = "acct-concurrent";

        EventRequest req = new EventRequest();
        req.setEventId(eventId);
        req.setAccountId(accountId);
        req.setType("CREDIT");
        req.setAmount(BigDecimal.valueOf(100.00));
        req.setCurrency("USD");
        req.setEventTimestamp(Instant.parse("2026-01-01T10:00:00Z"));

        String body = objectMapper.writeValueAsString(req);

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch done      = new CountDownLatch(THREAD_COUNT);

        AtomicInteger status201 = new AtomicInteger(0);
        AtomicInteger status200 = new AtomicInteger(0);
        AtomicInteger errors    = new AtomicInteger(0);
        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < THREAD_COUNT; i++) {
            futures.add(executor.submit(() -> {
                startGate.await(); // all threads start simultaneously
                try {
                    int status = mockMvc.perform(post("/events")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                            .andReturn()
                            .getResponse()
                            .getStatus();
                    if (status == 201) status201.incrementAndGet();
                    else if (status == 200) status200.incrementAndGet();
                    else errors.incrementAndGet();
                    return status;
                } finally {
                    done.countDown();
                }
            }));
        }

        startGate.countDown(); // fire
        done.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Exactly ONE event stored
        long storedCount = repository.findByAccountIdOrderByEventTimestampAsc(accountId).size();
        assertThat(storedCount).isEqualTo(1);

        // All responses were 200 or 201 — no 500s
        assertThat(errors.get()).isZero();

        // At least one thread won the race with 201
        assertThat(status201.get()).isGreaterThanOrEqualTo(1);

        // Total responses add up
        assertThat(status201.get() + status200.get()).isEqualTo(THREAD_COUNT);

        // Balance reflects the amount exactly once
        String balanceBody = mockMvc.perform(get("/accounts/" + accountId + "/balance"))
                .andReturn().getResponse().getContentAsString();
        assertThat(balanceBody).contains("100");

        // Cleanup (no @Transactional on this test class)
        repository.deleteById(eventId);
    }
}
