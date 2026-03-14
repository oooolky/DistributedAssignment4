package com.cs6650.loadtester.client;

import com.cs6650.common.dto.GetResponse;
import com.cs6650.common.dto.PutRequest;
import com.cs6650.common.dto.PutResponse;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

public class KvHttpClient {
  private final RestTemplate restTemplate = new RestTemplate();

  public PutResponse put(String baseUrl, String key, String value) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    PutRequest request = new PutRequest(key, value);
    HttpEntity<PutRequest> entity = new HttpEntity<>(request, headers);

    ResponseEntity<PutResponse> response = restTemplate.exchange(
        baseUrl + "/kv",
        HttpMethod.PUT,
        entity,
        PutResponse.class
    );
    return response.getBody();
  }

  public GetResponse get(String baseUrl, String key) {
    return restTemplate.getForObject(baseUrl + "/kv?key={key}", GetResponse.class, key);
  }

  public GetResponse localRead(String baseUrl, String key) {
    return restTemplate.getForObject(baseUrl + "/local_read?key={key}", GetResponse.class, key);
  }
}
