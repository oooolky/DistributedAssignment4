package com.cs6650.databasenode.replication;

import com.cs6650.common.config.NodeConfig;
import com.cs6650.common.dto.ReplicationPutRequest;
import com.cs6650.databasenode.client.InternalNodeClient;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class LeaderFollowerReplicationCoordinator {
  private final InternalNodeClient internalNodeClient;

  public LeaderFollowerReplicationCoordinator(InternalNodeClient internalNodeClient) {
    this.internalNodeClient = internalNodeClient;
  }

  public int replicateToFollowers(NodeConfig config, ReplicationPutRequest request, int currentSuccessCount) {
    int success = currentSuccessCount;
    List<String> followers = config.getFollowerUrls();

    // Iterate through ALL followers to prevent permanent data loss on slower nodes.
    // Do not break early even if quorum is reached.
    for (String followerUrl : followers) {
      boolean ok = internalNodeClient.replicatePut(followerUrl, request);
      if (ok) {
        success++;
      }
    }
    
    // Return total success count to the service layer for quorum validation
    return success;
  }
}