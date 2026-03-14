package com.cs6650.common.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NodeConfig {
  private String mode; // leader-follower or leaderless
  private String role; // leader, follower, leaderless
  private String nodeId;
  private List<String> followerUrls = new ArrayList<>();
  private List<String> peerUrls = new ArrayList<>();
  private int writeQuorumSize;
  private int readQuorumSize;

  public static NodeConfig fromEnv() {
    NodeConfig cfg = new NodeConfig();
    cfg.mode = System.getenv().getOrDefault("MODE", "leader-follower");
    cfg.role = System.getenv().getOrDefault("ROLE", "follower");
    cfg.nodeId = System.getenv().getOrDefault("NODE_ID", "node-1");
    cfg.writeQuorumSize = Integer.parseInt(System.getenv().getOrDefault("WRITE_QUORUM_SIZE", "5"));
    cfg.readQuorumSize = Integer.parseInt(System.getenv().getOrDefault("READ_QUORUM_SIZE", "1"));

    String followerEnv = System.getenv().getOrDefault("FOLLOWER_URLS", "");
    if (!followerEnv.isBlank()) {
      cfg.followerUrls = Arrays.stream(followerEnv.split(","))
          .map(String::trim)
          .filter(s -> !s.isBlank())
          .toList();
    }

    String peerEnv = System.getenv().getOrDefault("PEER_URLS", "");
    if (!peerEnv.isBlank()) {
      cfg.peerUrls = Arrays.stream(peerEnv.split(","))
          .map(String::trim)
          .filter(s -> !s.isBlank())
          .toList();
    }
    return cfg;
  }

  public String getMode() {
    return mode;
  }

  public String getRole() {
    return role;
  }

  public String getNodeId() {
    return nodeId;
  }

  public List<String> getFollowerUrls() {
    return followerUrls;
  }

  public List<String> getPeerUrls() {
    return peerUrls;
  }

  public int getWriteQuorumSize() {
    return writeQuorumSize;
  }

  public int getReadQuorumSize() {
    return readQuorumSize;
  }
}
