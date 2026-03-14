package com.cs6650.databasenode.service;

import com.cs6650.common.config.NodeConfig;
import com.cs6650.common.dto.GetResponse;
import com.cs6650.common.dto.PutRequest;
import com.cs6650.common.dto.PutResponse;
import org.springframework.stereotype.Service;

@Service
public class KvService {
  private final NodeConfig nodeConfig;
  private final LeaderFollowerService leaderFollowerService;
  private final LeaderlessService leaderlessService;

  public KvService(
      NodeConfig nodeConfig,
      LeaderFollowerService leaderFollowerService,
      LeaderlessService leaderlessService) {
    this.nodeConfig = nodeConfig;
    this.leaderFollowerService = leaderFollowerService;
    this.leaderlessService = leaderlessService;
  }

  public PutResponse put(PutRequest request) {
    if ("leaderless".equalsIgnoreCase(nodeConfig.getMode())) {
      return leaderlessService.put(request);
    }
    return leaderFollowerService.put(request);
  }

  public GetResponse get(String key) {
    if ("leaderless".equalsIgnoreCase(nodeConfig.getMode())) {
      return leaderlessService.get(key);
    }
    return leaderFollowerService.get(key);
  }

  public void applyReplication(String key, String value, int version) {
    if ("leaderless".equalsIgnoreCase(nodeConfig.getMode())) {
      leaderlessService.applyReplication(key, value, version);
    } else {
      leaderFollowerService.applyReplication(key, value, version);
    }
  }

  public GetResponse localRead(String key) {
    if ("leaderless".equalsIgnoreCase(nodeConfig.getMode())) {
      return leaderlessService.get(key);
    }
    return leaderFollowerService.localReadWithoutQuorum(key);
  }
}