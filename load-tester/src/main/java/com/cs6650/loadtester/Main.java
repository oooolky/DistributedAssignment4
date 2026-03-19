package com.cs6650.loadtester;

import com.cs6650.loadtester.generator.WorkloadGenerator;
import com.cs6650.loadtester.metrics.CsvExporter;
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

    // 10% writes, 90% reads. Keyspace of 100.
    WorkloadGenerator generator = new WorkloadGenerator(10, 100);
    MetricsCollector metrics = new MetricsCollector();
    StaleReadTracker staleTracker = new StaleReadTracker();

    try {
      System.out.println("Starting load test with multithreading...");
      new LoadTestRunner().run(writeUrl, readUrls, 1000, generator, metrics, staleTracker);
      
      System.out.println("\n--- Load Test Completed ---");
      System.out.println("Total Read requests: " + metrics.getReadLatencies().size());
      System.out.println("Total Write requests: " + metrics.getWriteLatencies().size());
      System.out.println("Stale reads caught: " + staleTracker.getStaleReads());
      System.out.println("Interval metrics collected: " + metrics.getKeyAccessIntervals().size());
      
      System.out.println("\n--- Exporting Data ---");
      // Give the files a unique prefix based on the current timestamp so you don't overwrite previous runs
      String filePrefix = "test_run_" + System.currentTimeMillis();
      CsvExporter.exportAll(metrics, filePrefix);

    } catch (InterruptedException e) {
      System.err.println("Load test was interrupted!");
      e.printStackTrace();
    }
  }
}