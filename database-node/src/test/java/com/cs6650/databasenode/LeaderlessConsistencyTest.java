package com.cs6650.databasenode;

import com.cs6650.common.dto.GetResponse;
import com.cs6650.common.dto.PutRequest;
import com.cs6650.common.dto.PutResponse;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for Leaderless replication consistency.
 *
 * <p>Requires a running 5-node Leaderless cluster (W=N, R=1):
 * <pre>
 *   docker compose -f docker-compose.leaderless.yml up -d
 *   mvn test -pl database-node -Dgroups="leaderless"
 * </pre>
 *
 * <p>Override node URLs when running against AWS:
 * <pre>
 *   -Dtest.leaderless.node1.url=http://&lt;ec2-ip&gt;:8080
 * </pre>
 */
class LeaderlessConsistencyTest {

    private static final List<String> NODE_URLS = List.of(
            System.getProperty("test.leaderless.node1.url", "http://localhost:8080"),
            System.getProperty("test.leaderless.node2.url", "http://localhost:8081"),
            System.getProperty("test.leaderless.node3.url", "http://localhost:8082"),
            System.getProperty("test.leaderless.node4.url", "http://localhost:8083"),
            System.getProperty("test.leaderless.node5.url", "http://localhost:8084")
    );

    private final RestTemplate restTemplate = new RestTemplate();
    private final Random random = new Random();

    /**
     * Leaderless consistency test: four assertions in one scenario.
     *
     * <ol>
     *   <li>During the write window, non-coordinator nodes return stale or missing data.</li>
     *   <li>After the write is acknowledged, the coordinator itself returns the new value.</li>
     *   <li>After the write is acknowledged, all other nodes also have the new value
     *       (because W=N means the coordinator waited for all peers before responding).</li>
     * </ol>
     *
     * <p>Requires: Leaderless cluster ({@code docker compose -f docker-compose.leaderless.yml up -d}).
     */
    @Test
    @Tag("leaderless")
    @DisplayName("Leaderless – inconsistency window visible during write, all nodes converge after")
    void leaderless_inconsistencyWindowThenConverge() throws InterruptedException {
        String key = "leaderless-" + System.nanoTime();
        String newValue = "leaderless-test-value";

        // Choose a random Write Coordinator node.
        int coordinatorIndex = random.nextInt(NODE_URLS.size());
        String coordinatorUrl = NODE_URLS.get(coordinatorIndex);

        // Fire the write on a background thread so we can race reads against it.
        final PutResponse[] putResult = new PutResponse[1];
        Thread writeThread = new Thread(() -> putResult[0] = put(coordinatorUrl, key, newValue));
        writeThread.start();

        // The coordinator replicates to (N-1)=4 peers sequentially, each sleeping 200 ms,
        // then writes locally for another 200 ms (total ~1 s). Reading after 50 ms gives
        // a good chance of catching non-coordinator nodes in the stale state.
        Thread.sleep(50);

        int staleOrMissingDuringWrite = 0;
        for (int i = 0; i < NODE_URLS.size(); i++) {
            if (i == coordinatorIndex) {
                continue; // Coordinator may not have written locally yet either — skip.
            }
            GetResponse response = localRead(NODE_URLS.get(i), key);
            if (response == null) {
                staleOrMissingDuringWrite++;
            }
        }

        writeThread.join();
        assertNotNull(putResult[0], "PUT to Leaderless coordinator must succeed");

        System.out.printf("Stale/missing non-coordinator nodes during write window: %d / %d%n",
                staleOrMissingDuringWrite, NODE_URLS.size() - 1);

        assertTrue(staleOrMissingDuringWrite > 0,
                "Expected at least one non-coordinator node to be stale during the write window. "
                        + "If this fails, try re-running — the 50 ms window may have been too short.");

        // After the write is acknowledged, the coordinator must have the value.
        GetResponse coordinatorRead = get(coordinatorUrl, key);
        assertNotNull(coordinatorRead, "Coordinator must return the written value after acknowledging PUT");
        assertEquals(newValue, coordinatorRead.getValue());

        // W=N means ALL peers were updated before the coordinator responded.
        // Every other node must now also have the value.
        for (int i = 0; i < NODE_URLS.size(); i++) {
            if (i == coordinatorIndex) {
                continue;
            }
            GetResponse peerRead = localRead(NODE_URLS.get(i), key);
            assertNotNull(peerRead, "Node " + i + " must have the value after W=N write is acknowledged");
            assertEquals(newValue, peerRead.getValue(), "Node " + i + " must return the correct value");
            assertEquals(putResult[0].getVersion(), peerRead.getVersion(),
                    "Node " + i + " must have the correct version");
        }
    }

    // -----------------------------------------------------------------------
    // Helper methods
    // -----------------------------------------------------------------------

    /** Sends PUT /kv and asserts HTTP 201 Created. */
    private PutResponse put(String baseUrl, String key, String value) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<PutResponse> response = restTemplate.exchange(
                baseUrl + "/kv",
                HttpMethod.PUT,
                new HttpEntity<>(new PutRequest(key, value), headers),
                PutResponse.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode(),
                "PUT /kv must return HTTP 201 Created");
        return response.getBody();
    }

    /** GET /kv — returns null on 404. */
    private GetResponse get(String baseUrl, String key) {
        try {
            return restTemplate.getForObject(baseUrl + "/kv?key={key}", GetResponse.class, key);
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
    }

    /** GET /local_read — reads this node's local store only. Returns null on 404. */
    private GetResponse localRead(String baseUrl, String key) {
        try {
            return restTemplate.getForObject(
                    baseUrl + "/local_read?key={key}", GetResponse.class, key);
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
    }
}
