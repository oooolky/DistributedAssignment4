package com.cs6650.databasenode.controller;

import com.cs6650.common.dto.GetResponse;
import com.cs6650.common.dto.PutRequest;
import com.cs6650.common.dto.PutResponse;
import com.cs6650.databasenode.service.KvService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public-facing API for the KV store.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>PUT  /kv           — store a key-value pair (returns 201 Created)</li>
 *   <li>GET  /kv?key=...   — retrieve a value (returns 200 OK or 404 Not Found)</li>
 *   <li>GET  /local_read   — read this node's local store only, bypassing quorum (for testing)</li>
 * </ul>
 *
 * <p>Per assignment requirements, if a peer node is unreachable and the write quorum
 * cannot be satisfied, this controller returns 503 Service Unavailable.
 */
@RestController
public class KvController {

    private static final Logger logger = LoggerFactory.getLogger(KvController.class);

    private final KvService kvService;

    public KvController(KvService kvService) {
        this.kvService = kvService;
    }

    @PutMapping("/kv")
    public ResponseEntity<PutResponse> put(@RequestBody PutRequest request) {
        try {
            PutResponse response = kvService.put(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            // Bad request (e.g. empty key).
            logger.warn("Bad PUT request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (IllegalStateException e) {
            // A follower received a write — only the leader accepts external writes.
            logger.warn("Write rejected on non-leader node: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        } catch (RuntimeException e) {
            // Write quorum not satisfied — one or more nodes were unreachable.
            logger.error("Write quorum not satisfied, returning 503: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

    @GetMapping("/kv")
    public ResponseEntity<GetResponse> get(@RequestParam String key) {
        try {
            GetResponse response = kvService.get(key);
            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.warn("Bad GET request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (RuntimeException e) {
            // Read quorum could not be satisfied.
            logger.error("Read quorum not satisfied, returning 503: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

    /**
     * Returns this node's local value without any quorum coordination.
     * Used by unit tests to detect the inconsistency window (Test 3 / Leaderless test).
     */
    @GetMapping("/local_read")
    public ResponseEntity<GetResponse> localRead(@RequestParam String key) {
        GetResponse response = kvService.localRead(key);
        if (response == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(response);
    }
}
