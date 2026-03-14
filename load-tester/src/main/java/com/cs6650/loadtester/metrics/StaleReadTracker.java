package com.cs6650.loadtester.metrics;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class StaleReadTracker {
  private final ConcurrentHashMap<String, Integer> lastWrittenVersion = new ConcurrentHashMap<>();
  private final AtomicInteger staleReads = new AtomicInteger(0);

  public void recordWrite(String key, int version) {
    lastWrittenVersion.put(key, version);
  }

  public void recordRead(String key, int version) {
    int latest = lastWrittenVersion.getOrDefault(key, 0);
    if (version < latest) {
      staleReads.incrementAndGet();
    }
  }

  public int getStaleReads() {
    return staleReads.get();
  }
}
