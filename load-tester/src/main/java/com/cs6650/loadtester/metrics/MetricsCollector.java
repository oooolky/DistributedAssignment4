package com.cs6650.loadtester.metrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MetricsCollector {
  private final List<Long> readLatencies = Collections.synchronizedList(new ArrayList<>());
  private final List<Long> writeLatencies = Collections.synchronizedList(new ArrayList<>());

  public void recordReadLatency(long latencyMs) {
    readLatencies.add(latencyMs);
  }

  public void recordWriteLatency(long latencyMs) {
    writeLatencies.add(latencyMs);
  }

  public List<Long> getReadLatencies() {
    return readLatencies;
  }

  public List<Long> getWriteLatencies() {
    return writeLatencies;
  }
}
