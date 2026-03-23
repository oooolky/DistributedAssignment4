package com.cs6650.loadtester.client;

import com.cs6650.common.dto.GetResponse;
import com.cs6650.common.dto.PutRequest;
import com.cs6650.common.dto.PutResponse;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * HTTP client for the load tester to communicate with KV nodes.
 *
 * <p>GET returning 404 is treated as a valid response (key not yet written),
 * not an exception — the caller records latency and counts it as a stale read.
 */
public class KvHttpClient {

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Sends PUT /kv. Returns the server's PutResponse, or null if the request failed.
     */
    public PutResponse put(String baseUrl, String key, String value) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        PutRequest body = new PutRequest(key, value);
        ResponseEntity<PutResponse> response = restTemplate.exchange(
                baseUrl + "/kv",
                HttpMethod.PUT,
                new HttpEntity<>(body, headers),
                PutResponse.class);
        return response.getBody();
    }

    /**
     * Sends GET /kv. Returns the value if found, or null if the server returned 404.
     * Any other error is re-thrown so the caller can log it.
     */
    public GetResponse get(String baseUrl, String key) {
        try {
            return restTemplate.getForObject(
                    baseUrl + "/kv?key={key}", GetResponse.class, key);
        } catch (HttpClientErrorException.NotFound e) {
            // 404 is a normal response: the key has not been written to this node yet.
            // Return null so the caller records the latency and counts this as stale.
            return null;
        }
    }

    /**
     * Sends GET /local_read. Returns null on 404.
     * Used by integration tests to bypass quorum coordination.
     */
    public GetResponse localRead(String baseUrl, String key) {
        try {
            return restTemplate.getForObject(
                    baseUrl + "/local_read?key={key}", GetResponse.class, key);
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
    }
}
