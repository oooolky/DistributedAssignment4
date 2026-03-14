package com.cs6650.databasenode.replication;

import com.cs6650.common.config.NodeConfig;
import com.cs6650.common.dto.ReplicationPutRequest;
import com.cs6650.databasenode.client.InternalNodeClient;
import org.springframework.stereotype.Component;

@Component
public class LeaderlessReplicationCoordinator {
  private final InternalNodeClient internalNodeClient;

  public LeaderlessReplicationCoordinator(InternalNodeClient internalNodeClient) {
    this.internalNodeClient = internalNodeClient;
  }

  public boolean replicateToAllPeers(NodeConfig config, ReplicationPutRequest request) {
    for (String peerUrl : config.getPeerUrls()) {
      boolean ok = internalNodeClient.replicatePut(peerUrl, request);
      if (!ok) {
        return false;
      }
    }
    return true;
  }
}
