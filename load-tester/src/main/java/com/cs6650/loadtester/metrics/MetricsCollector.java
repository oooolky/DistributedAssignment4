package com.cs6650.loadtester.metrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class MetricsCollector {
  private final List<Long> readLatencies = Collections.synchronizedList(new ArrayList<>());
  private final List<Long> writeLatencies = Collections.synchronizedList(new ArrayList<>());
  
  // Stores the last timestamp a specific key was accessed (read or write)
  private final ConcurrentHashMap<String, Long> lastKeyAccessTime = new ConcurrentHashMap<>();
  // Stores the calculated intervals between accesses
  private final List<Long> keyAccessIntervals = Collections.synchronizedList(new ArrayList<>());

  public void recordReadLatency(long latencyMs) {
    readLatencies.add(latencyMs);
  }

  public void recordWriteLatency(long latencyMs) {
    writeLatencies.add(latencyMs);
  }

  public void recordKeyAccessInterval(String key, long currentTimestampMs) {
    Long lastAccess = lastKeyAccessTime.put(key, currentTimestampMs);
    if (lastAccess != null) {
      long interval = currentTimestampMs - lastAccess;
      keyAccessIntervals.add(interval);
    }
  }

  public List<Long> getReadLatencies() {
    return readLatencies;
  }

  public List<Long> getWriteLatencies() {
    return writeLatencies;
  }

  public List<Long> getKeyAccessIntervals() {
    return keyAccessIntervals;
  }
}