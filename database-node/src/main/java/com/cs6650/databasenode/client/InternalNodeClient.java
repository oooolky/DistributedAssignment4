package com.cs6650.databasenode.client;


import com.cs6650.common.dto.GetResponse;
import com.cs6650.common.dto.ReplicationPutRequest;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class InternalNodeClient {
  private final RestTemplate restTemplate = new RestTemplate();

  public boolean replicatePut(String baseUrl, ReplicationPutRequest request) {
    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      HttpEntity<ReplicationPutRequest> entity = new HttpEntity<>(request, headers);

      ResponseEntity<Void> response = restTemplate.exchange(
          baseUrl + "/internal/replicate",
          HttpMethod.PUT,
          entity,
          Void.class
      );
      return response.getStatusCode().is2xxSuccessful();
    } catch (Exception e) {
      return false;
    }
  }

  public GetResponse internalRead(String baseUrl, String key) {
    try {
      return restTemplate.getForObject(
          baseUrl + "/internal/read?key={key}",
          GetResponse.class,
          key
      );
    } catch (Exception e) {
      return null;
    }
  }

  public GetResponse localRead(String baseUrl, String key) {
    try {
      return restTemplate.getForObject(
          baseUrl + "/local_read?key={key}",
          GetResponse.class,
          key
      );
    } catch (Exception e) {
      return null;
    }
  }
}
