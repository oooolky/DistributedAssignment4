package com.cs6650.databasenode.controller;

import com.cs6650.common.dto.GetResponse;
import com.cs6650.common.dto.ReplicationPutRequest;
import com.cs6650.databasenode.service.KvService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal")
public class InternalController {
  private final KvService kvService;

  public InternalController(KvService kvService) {
    this.kvService = kvService;
  }

  @PutMapping("/replicate")
  public ResponseEntity<Void> replicate(@RequestBody ReplicationPutRequest request) {
    kvService.applyReplication(request.getKey(), request.getValue(), request.getVersion());
    return ResponseEntity.ok().build();
  }

  @GetMapping("/read")
  public ResponseEntity<GetResponse> internalRead(@RequestParam String key) {
    GetResponse response = kvService.localRead(key);
    if (response == null) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(response);
  }
}
