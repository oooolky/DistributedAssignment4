package com.cs6650.databasenode;

import com.cs6650.common.dto.GetResponse;
import com.cs6650.common.dto.PutRequest;
import com.cs6650.common.dto.PutResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
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
 * Integration tests for Leader-Follower replication consistency.
 *
 * <p>Tests 1 and 2 require the default W=5/R=1 cluster:
 * <pre>
 *   docker compose up -d
 *   mvn test -pl database-node -Dgroups="w5-setup"
 * </pre>
 *
 * <p>Test 3 requires the W=1/R=1 cluster to expose the inconsistency window:
 * <pre>
 *   docker compose -f docker-compose.yml -f docker-compose.w1r1.override.yml up -d
 *   mvn test -pl database-node -Dgroups="w1-setup"
 * </pre>
 *
 * <p>Override node URLs when running against AWS (defaults: localhost):
 * <pre>
 *   mvn test -pl database-node -Dgroups="w5-setup" \
 *     -Dtest.leader.url=http://&lt;ec2-ip&gt;:8080
 * </pre>
 */
@TestMethodOrder(MethodOrderer.DisplayName.class)
class LeaderFollowerConsistencyTest {

    private static final String LEADER_URL =
            System.getProperty("test.leader.url", "http://localhost:8080");

    private static final List<String> FOLLOWER_URLS = List.of(
            System.getProperty("test.follower1.url", "http://localhost:8081"),
            System.getProperty("test.follower2.url", "http://localhost:8082"),
            System.getProperty("test.follower3.url", "http://localhost:8083"),
            System.getProperty("test.follower4.url", "http://localhost:8084")
    );

    private final RestTemplate restTemplate = new RestTemplate();

    // -----------------------------------------------------------------------
    // Test 1 — Smoke test: write to Leader, read back from same Leader (W=5)
    // -----------------------------------------------------------------------

    /**
     * Test 1: Basic smoke test with W=5.
     * After a PUT to the Leader is acknowledged, a GET to the same Leader
     * must return the new value with the correct version number.
     */
    @Test
    @Tag("w5-setup")
    @DisplayName("Test 1 – W=5: write to Leader, read back from same Leader")
    void test1_writeToLeader_readFromLeader_returnsNewValue() {
        String key = "test1-" + System.nanoTime();
        String expectedValue = "hello-from-test1";

        PutResponse putResponse = put(LEADER_URL, key, expectedValue);
        assertNotNull(putResponse, "PUT response body should not be null");
        assertEquals(key, putResponse.getKey());
        assertEquals(1, putResponse.getVersion(),
                "First write to a new key should produce version 1");

        GetResponse getResponse = get(LEADER_URL, key);
        assertNotNull(getResponse, "GET from Leader should return the written value");
        assertEquals(expectedValue, getResponse.getValue());
        assertEquals(putResponse.getVersion(), getResponse.getVersion());
    }

    // -----------------------------------------------------------------------
    // Test 2 — W=5 strong consistency: all Followers have the value
    // -----------------------------------------------------------------------

    /**
     * Test 2: Strong consistency with W=5, R=1.
     * Because W=5 means the Leader waited for all 4 Followers to acknowledge
     * before returning 201, a local_read on any Follower immediately after
     * must return the new value.
     */
    @Test
    @Tag("w5-setup")
    @DisplayName("Test 2 – W=5: every Follower already has the value after PUT is acknowledged")
    void test2_writeWithW5_allFollowersHaveNewValue() {
        String key = "test2-" + System.nanoTime();
        String expectedValue = "strong-consistency-value";

        PutResponse putResponse = put(LEADER_URL, key, expectedValue);
        assertNotNull(putResponse);

        // W=5: all 4 followers updated synchronously before leader responded.
        // local_read reads each follower's own store without quorum coordination.
        for (int i = 0; i < FOLLOWER_URLS.size(); i++) {
            GetResponse followerRead = localRead(FOLLOWER_URLS.get(i), key);
            int followerNumber = i + 1;
            assertNotNull(followerRead,
                    "Follower " + followerNumber + " must have the value after W=5 write");
            assertEquals(expectedValue, followerRead.getValue(),
                    "Follower " + followerNumber + " must return the correct value");
            assertEquals(putResponse.getVersion(), followerRead.getVersion(),
                    "Follower " + followerNumber + " must have the correct version");
        }
    }

    // -----------------------------------------------------------------------
    // Test 3 — W=1, R=1: Followers show stale data immediately after write
    // -----------------------------------------------------------------------

    /**
     * Test 3: Exposing the inconsistency window with W=1, R=1.
     *
     * <p>Per the assignment specification:
     * <ol>
     *   <li>Configure W=1, R=1.</li>
     *   <li>Send PUT to the Leader and wait for acknowledgement.</li>
     *   <li>Immediately (within 5 × 200 ms = 1 s) send {@code local_read} to Followers.</li>
     *   <li>Some Followers should return stale data (or 404 for the first write to that key).</li>
     * </ol>
     *
     * <p>With W=1, the Leader writes locally (~200 ms) and responds without waiting
     * for any Follower. All 4 Followers receive the update asynchronously in the
     * background, each taking another ~200 ms. Reading a Follower immediately after
     * the Leader responds will therefore frequently return stale or missing data.
     *
     * <p>Requires: W=1/R=1 cluster:
     * <pre>
     *   docker compose -f docker-compose.yml -f docker-compose.w1r1.override.yml up -d
     * </pre>
     */
    @Test
    @Tag("w1-setup")
    @DisplayName("Test 3 – W=1/R=1: Follower local_read returns stale or missing data immediately after PUT")
    void test3_writeWithW1R1_followerLocalReadIsStale() {
        String key = "test3-" + System.nanoTime();
        String newValue = "w1-inconsistent-value";

        // Step 1: send the PUT and block until the Leader acknowledges.
        // W=1 means the Leader only writes locally (~200 ms) before responding —
        // no Follower is contacted synchronously, so this returns quickly.
        PutResponse putResponse = put(LEADER_URL, key, newValue);
        assertNotNull(putResponse, "PUT to Leader must succeed");

        // Step 2: immediately query each Follower's local store.
        // Background replication to each Follower takes ~200 ms per node.
        // Because we read right after the Leader responded, at least some Followers
        // will not have received the update yet and will return 404 (null here).
        int staleOrMissingCount = 0;
        for (String followerUrl : FOLLOWER_URLS) {
            GetResponse followerRead = localRead(followerUrl, key);
            // null means 404 — the key is not yet present on this Follower.
            boolean isStale = (followerRead == null)
                    || (followerRead.getVersion() < putResponse.getVersion());
            if (isStale) {
                staleOrMissingCount++;
            }
        }

        System.out.printf("Stale/missing followers immediately after W=1/R=1 write: %d / %d%n",
                staleOrMissingCount, FOLLOWER_URLS.size());

        assertTrue(staleOrMissingCount > 0,
                "Expected at least one Follower to return stale or missing data immediately "
                + "after a W=1/R=1 write. Verify the cluster is running with W=1/R=1. "
                + "If this still fails, the machine completed background replication before "
                + "the check ran — re-run the test.");
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

    /**
     * GET /local_read — reads this node's local store without any quorum coordination.
     * Returns null when the node responds with 404 (key not yet replicated).
     */
    private GetResponse localRead(String baseUrl, String key) {
        try {
            return restTemplate.getForObject(
                    baseUrl + "/local_read?key={key}", GetResponse.class, key);
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
    }
}
