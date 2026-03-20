package com.cs6650.databasenode.client;

import com.cs6650.common.dto.GetResponse;
import com.cs6650.common.dto.ReplicationPutRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/**
 * HTTP client used by the Leader (or Write Coordinator) to communicate with peer nodes.
 *
 * <p>Per assignment requirements, if a peer node is unreachable the error is logged
 * and the caller receives a failure signal (false / null). The controller layer is
 * responsible for translating an insufficient quorum into a 503 response to the client.
 */
@Component
public class InternalNodeClient {

    private static final Logger logger = LoggerFactory.getLogger(InternalNodeClient.class);

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Sends a replication PUT to a peer node.
     *
     * @return true if the peer acknowledged with a 2xx response, false if unreachable or error
     */
    public boolean replicatePut(String baseUrl, ReplicationPutRequest request) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<ReplicationPutRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<Void> response = restTemplate.exchange(
                    baseUrl + "/internal/replicate",
                    HttpMethod.PUT,
                    entity,
                    Void.class);

            return response.getStatusCode().is2xxSuccessful();

        } catch (ResourceAccessException e) {
            // Node is unreachable (connection refused, timeout, etc.).
            logger.error("Peer node unreachable during replication: url={}, key={}, reason={}",
                    baseUrl, request.getKey(), e.getMessage());
            return false;
        } catch (Exception e) {
            logger.error("Unexpected error during replication to {}: {}", baseUrl, e.getMessage());
            return false;
        }
    }

    /**
     * Reads the local value from a peer node via the internal /internal/read endpoint.
     * Used for quorum reads (R > 1) where the coordinator collects values from multiple nodes.
     *
     * @return the peer's local value, or null if the node is unreachable or the key is absent
     */
    public GetResponse internalRead(String baseUrl, String key) {
        try {
            return restTemplate.getForObject(
                    baseUrl + "/internal/read?key={key}",
                    GetResponse.class,
                    key);
        } catch (ResourceAccessException e) {
            logger.error("Peer node unreachable during quorum read: url={}, key={}, reason={}",
                    baseUrl, key, e.getMessage());
            return null;
        } catch (Exception e) {
            logger.error("Unexpected error reading from {}: {}", baseUrl, e.getMessage());
            return null;
        }
    }

    /**
     * Reads the local value from a specific node via the /local_read endpoint.
     * Used by integration tests and by the leaderless coordinator.
     *
     * @return the node's local value, or null if unreachable or key is absent
     */
    public GetResponse localRead(String baseUrl, String key) {
        try {
            return restTemplate.getForObject(
                    baseUrl + "/local_read?key={key}",
                    GetResponse.class,
                    key);
        } catch (ResourceAccessException e) {
            logger.error("Peer node unreachable during local_read: url={}, key={}, reason={}",
                    baseUrl, key, e.getMessage());
            return null;
        } catch (Exception e) {
            logger.error("Unexpected error during local_read from {}: {}", baseUrl, e.getMessage());
            return null;
        }
    }
}
