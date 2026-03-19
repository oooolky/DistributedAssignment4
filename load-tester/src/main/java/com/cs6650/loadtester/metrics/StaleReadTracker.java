package com.cs6650.loadtester.metrics;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class StaleReadTracker {
  private final ConcurrentHashMap<String, Integer> highestWrittenVersion = new ConcurrentHashMap<>();
  private final AtomicInteger staleReads = new AtomicInteger(0);

  public void recordWrite(String key, int version) {
    // Thread-safe way to ensure we only store the highest version known for a key
    highestWrittenVersion.compute(key, (k, currentMax) -> 
        (currentMax == null || version > currentMax) ? version : currentMax
    );
  }

  public void recordRead(String key, int version) {
    int latestKnownVersion = highestWrittenVersion.getOrDefault(key, 0);
    // If the read version is lower than the highest successfully written version, it's a stale read
    if (version < latestKnownVersion) {
      staleReads.incrementAndGet();
    }
  }

  public int getStaleReads() {
    return staleReads.get();
  }
}