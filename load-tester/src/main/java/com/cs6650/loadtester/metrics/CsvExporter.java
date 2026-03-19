package com.cs6650.loadtester.metrics;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

public class CsvExporter {

  /**
   * Exports all metrics from the collector into three separate CSV files.
   */
  public static void exportAll(MetricsCollector metrics, String filePrefix) {
    writeListToFile(metrics.getReadLatencies(), filePrefix + "_read_latencies.csv", "ReadLatencyMs");
    writeListToFile(metrics.getWriteLatencies(), filePrefix + "_write_latencies.csv", "WriteLatencyMs");
    writeListToFile(metrics.getKeyAccessIntervals(), filePrefix + "_key_intervals.csv", "IntervalMs");
  }

  private static void writeListToFile(List<Long> data, String filename, String headerColumn) {
    if (data == null || data.isEmpty()) {
      System.out.println("Skipping " + filename + " (No data to write)");
      return;
    }

    try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
      // Write the CSV header
      writer.println(headerColumn);
      
      // Write each data point on a new line
      // Synchronizing on the list ensures thread safety just in case other threads are still lingering
      synchronized (data) {
        for (Long value : data) {
          writer.println(value);
        }
      }
      System.out.println("✅ Exported " + data.size() + " records to " + filename);
    } catch (IOException e) {
      System.err.println("Failed to write CSV file: " + filename);
      e.printStackTrace();
    }
  }
}