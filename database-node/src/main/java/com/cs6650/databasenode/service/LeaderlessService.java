package com.cs6650.databasenode.service;

import com.cs6650.common.config.NodeConfig;
import com.cs6650.common.dto.GetResponse;
import com.cs6650.common.dto.PutRequest;
import com.cs6650.common.dto.PutResponse;
import com.cs6650.common.dto.ReplicationPutRequest;
import com.cs6650.common.model.VersionedValue;
import com.cs6650.common.util.SleepUtil;
import com.cs6650.databasenode.replication.LeaderlessReplicationCoordinator;
import com.cs6650.databasenode.store.KvStore;
import org.springframework.stereotype.Service;

@Service
public class LeaderlessService {
  private final KvStore kvStore;
  private final NodeConfig nodeConfig;
  private final VersionService versionService;
  private final LeaderlessReplicationCoordinator replicationCoordinator;

  public LeaderlessService(
      KvStore kvStore,
      NodeConfig nodeConfig,
      VersionService versionService,
      LeaderlessReplicationCoordinator replicationCoordinator) {
    this.kvStore = kvStore;
    this.nodeConfig = nodeConfig;
    this.versionService = versionService;
    this.replicationCoordinator = replicationCoordinator;
  }

  public PutResponse put(PutRequest request) {
    if (request.getKey() == null || request.getKey().isBlank()) {
      throw new IllegalArgumentException("key cannot be empty");
    }

    VersionedValue current = kvStore.get(request.getKey());
    int newVersion = versionService.nextVersion(current);

    SleepUtil.sleepMillis(200);
    kvStore.put(request.getKey(), new VersionedValue(request.getValue(), newVersion));

    ReplicationPutRequest replicationRequest =
        new ReplicationPutRequest(request.getKey(), request.getValue(), newVersion);

    boolean ok = replicationCoordinator.replicateToAllPeers(nodeConfig, replicationRequest);
    if (!ok) {
      throw new RuntimeException("Failed to replicate to all peers");
    }

    return new PutResponse(request.getKey(), newVersion);
  }

  public GetResponse get(String key) {
    SleepUtil.sleepMillis(50);
    VersionedValue value = kvStore.get(key);
    if (value == null) {
      return null;
    }
    return new GetResponse(key, value.getValue(), value.getVersion());
  }

  public void applyReplication(String key, String value, int version) {
    SleepUtil.sleepMillis(200);
    kvStore.put(key, new VersionedValue(value, version));
  }
}