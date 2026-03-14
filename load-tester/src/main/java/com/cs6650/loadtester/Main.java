package com.cs6650.loadtester;

import com.cs6650.loadtester.generator.WorkloadGenerator;
import com.cs6650.loadtester.metrics.MetricsCollector;
import com.cs6650.loadtester.metrics.StaleReadTracker;
import com.cs6650.loadtester.runner.LoadTestRunner;
import java.util.List;

public class Main {
  public static void main(String[] args) {
    String writeUrl = "http://localhost:8080";
    List<String> readUrls = List.of(
        "http://localhost:8080",
        "http://localhost:8081",
        "http://localhost:8082"
    );

    WorkloadGenerator generator = new WorkloadGenerator(10, 100);
    MetricsCollector metrics = new MetricsCollector();
    StaleReadTracker staleTracker = new StaleReadTracker();

    new LoadTestRunner().run(writeUrl, readUrls, 1000, generator, metrics, staleTracker);

    System.out.println("Read requests: " + metrics.getReadLatencies().size());
    System.out.println("Write requests: " + metrics.getWriteLatencies().size());
    System.out.println("Stale reads: " + staleTracker.getStaleReads());
  }
}
