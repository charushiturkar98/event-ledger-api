package com.ledger.controller;

import com.ledger.dto.BalanceResponse;
import com.ledger.dto.EventRequest;
import com.ledger.dto.EventResponse;
import com.ledger.dto.PagedEventResponse;
import com.ledger.service.EventLedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "Event Ledger", description = "Financial transaction event ingestion and querying")
public class EventLedgerController {

    private final EventLedgerService service;

    public EventLedgerController(EventLedgerService service) {
        this.service = service;
    }

    // -------------------------------------------------------------------------
    // POST /events — submit a transaction event
    //
    // HTTP semantics:
    //   201 Created    — new event accepted
    //   200 OK         — duplicate eventId; returns the original stored event
    //   400 Bad Request — validation failure
    // -------------------------------------------------------------------------

    @Operation(
        summary = "Submit a transaction event",
        description = "Idempotent — submitting the same eventId multiple times returns the original event with 200."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Event accepted and stored"),
        @ApiResponse(responseCode = "200", description = "Duplicate event — original returned"),
        @ApiResponse(responseCode = "400", description = "Validation error")
    })
    @PostMapping("/events")
    public ResponseEntity<EventResponse> submitEvent(@Valid @RequestBody EventRequest request) {
        EventLedgerService.IngestResult result = service.submitEvent(request);

        HttpStatus status = result.duplicate() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(result.event());
    }

    // -------------------------------------------------------------------------
    // GET /events/{id} — retrieve a single event
    // -------------------------------------------------------------------------

    @Operation(summary = "Retrieve a single event by its ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Event found"),
        @ApiResponse(responseCode = "404", description = "Event not found")
    })
    @GetMapping("/events/{id}")
    public ResponseEntity<EventResponse> getEvent(@PathVariable String id) {
        return ResponseEntity.ok(service.getEvent(id));
    }

    // -------------------------------------------------------------------------
    // GET /events?account={accountId} — list events for an account
    //
    // Supports optional pagination via ?page=0&size=20
    // Without pagination params, returns the full list.
    // -------------------------------------------------------------------------

    @Operation(
        summary = "List events for an account",
        description = "Always returns events ordered by eventTimestamp (business time) ascending. " +
                      "Supports pagination via ?page=0&size=20."
    )
    @GetMapping("/events")
    public ResponseEntity<?> getEvents(
            @Parameter(description = "Account ID to filter by", required = true)
            @RequestParam String account,

            @Parameter(description = "Zero-based page number (enables pagination)")
            @RequestParam(required = false) Integer page,

            @Parameter(description = "Page size (used with page parameter)")
            @RequestParam(required = false) Integer size) {

        if (page != null && size != null) {
            PagedEventResponse paged = service.getEventsForAccountPaged(account, page, size);
            return ResponseEntity.ok(paged);
        }

        List<EventResponse> events = service.getEventsForAccount(account);
        return ResponseEntity.ok(events);
    }

    // -------------------------------------------------------------------------
    // GET /accounts/{accountId}/balance — compute the net balance
    // -------------------------------------------------------------------------

    @Operation(
        summary = "Get current balance for an account",
        description = "Returns net balance: SUM(CREDIT) - SUM(DEBIT). Correct regardless of event arrival order."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Balance computed successfully")
    })
    @GetMapping("/accounts/{accountId}/balance")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable String accountId) {
        return ResponseEntity.ok(service.getBalance(accountId));
    }
}
