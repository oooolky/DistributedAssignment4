package com.cs6650.databasenode.controller;

import com.cs6650.common.dto.GetResponse;
import com.cs6650.common.dto.PutRequest;
import com.cs6650.common.dto.PutResponse;
import com.cs6650.databasenode.service.KvService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class KvController {
  private final KvService kvService;

  public KvController(KvService kvService) {
    this.kvService = kvService;
  }

  @PutMapping("/kv")
  public ResponseEntity<PutResponse> put(@RequestBody PutRequest request) {
    PutResponse response = kvService.put(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @GetMapping("/kv")
  public ResponseEntity<GetResponse> get(@RequestParam String key) {
    GetResponse response = kvService.get(key);
    if (response == null) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(response);
  }

  @GetMapping("/local_read")
  public ResponseEntity<GetResponse> localRead(@RequestParam String key) {
    GetResponse response = kvService.localRead(key);
    if (response == null) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(response);
  }
}
