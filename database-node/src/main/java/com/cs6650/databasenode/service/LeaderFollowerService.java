package com.cs6650.databasenode.service;

import com.cs6650.common.config.NodeConfig;
import com.cs6650.common.dto.GetResponse;
import com.cs6650.common.dto.PutRequest;
import com.cs6650.common.dto.PutResponse;
import com.cs6650.common.dto.ReplicationPutRequest;
import com.cs6650.common.model.VersionedValue;
import com.cs6650.common.util.SleepUtil;
import com.cs6650.databasenode.client.InternalNodeClient;
import com.cs6650.databasenode.replication.LeaderFollowerReplicationCoordinator;
import com.cs6650.databasenode.replication.QuorumReadCoordinator;
import com.cs6650.databasenode.store.KvStore;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class LeaderFollowerService {
  private final KvStore kvStore;
  private final NodeConfig nodeConfig;
  private final VersionService versionService;
  private final InternalNodeClient internalNodeClient;
  private final LeaderFollowerReplicationCoordinator replicationCoordinator;
  private final QuorumReadCoordinator quorumReadCoordinator;

  public LeaderFollowerService(
      KvStore kvStore,
      NodeConfig nodeConfig,
      VersionService versionService,
      InternalNodeClient internalNodeClient,
      LeaderFollowerReplicationCoordinator replicationCoordinator,
      QuorumReadCoordinator quorumReadCoordinator) {
    this.kvStore = kvStore;
    this.nodeConfig = nodeConfig;
    this.versionService = versionService;
    this.internalNodeClient = internalNodeClient;
    this.replicationCoordinator = replicationCoordinator;
    this.quorumReadCoordinator = quorumReadCoordinator;
  }

  public PutResponse put(PutRequest request) {
    if (!"leader".equalsIgnoreCase(nodeConfig.getRole())) {
      throw new IllegalStateException("Only leader accepts external writes in leader-follower mode.");
    }
    if (request.getKey() == null || request.getKey().isBlank()) {
      throw new IllegalArgumentException("key cannot be empty");
    }

    VersionedValue current = kvStore.get(request.getKey());
    int newVersion = versionService.nextVersion(current);

    ReplicationPutRequest replicationRequest =
        new ReplicationPutRequest(request.getKey(), request.getValue(), newVersion);

    int success = 0;

    // 1. Replicate to followers FIRST (sequentially as required by the assignment)
    success = replicationCoordinator.replicateToFollowers(nodeConfig, replicationRequest, success);

    // 2. Leader local write and delay AFTER sending to followers
    SleepUtil.sleepMillis(200);
    kvStore.put(request.getKey(), new VersionedValue(request.getValue(), newVersion));
    success++; // Add the leader's own successful local write

    // 3. Verify if the write quorum is satisfied
    if (success < nodeConfig.getWriteQuorumSize()) {
      throw new RuntimeException("Write quorum not reached");
    }

    return new PutResponse(request.getKey(), newVersion);
  }

  public GetResponse get(String key) {
    if (key == null || key.isBlank()) {
      throw new IllegalArgumentException("key cannot be empty");
    }

    int r = nodeConfig.getReadQuorumSize();
    if (r <= 1) {
      return localReadWithDelay(key);
    }

    List<GetResponse> responses = new ArrayList<>();
    responses.add(localReadWithDelay(key));

    for (String followerUrl : nodeConfig.getFollowerUrls()) {
      if (responses.size() >= r) {
        break;
      }
      GetResponse followerResponse = internalNodeClient.internalRead(followerUrl, key);
      if (followerResponse != null) {
        responses.add(followerResponse);
      }
    }

    GetResponse latest = quorumReadCoordinator.chooseLatest(responses);
    if (latest == null) {
      throw new RuntimeException("Key not found");
    }
    return latest;
  }

  public void applyReplication(String key, String value, int version) {
    SleepUtil.sleepMillis(200);
    kvStore.put(key, new VersionedValue(value, version));
  }

  public GetResponse localReadWithDelay(String key) {
    SleepUtil.sleepMillis(50);
    VersionedValue value = kvStore.get(key);
    if (value == null) {
      return null;
    }
    return new GetResponse(key, value.getValue(), value.getVersion());
  }

  public GetResponse localReadWithoutQuorum(String key) {
    return localReadWithDelay(key);
  }
}
