package com.cs6650.loadtester.runner;

import com.cs6650.common.dto.GetResponse;
import com.cs6650.common.dto.PutResponse;
import com.cs6650.loadtester.client.KvHttpClient;
import com.cs6650.loadtester.generator.WorkloadGenerator;
import com.cs6650.loadtester.metrics.MetricsCollector;
import com.cs6650.loadtester.metrics.StaleReadTracker;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LoadTestRunner {
  private final KvHttpClient client = new KvHttpClient();
  // Using 50 concurrent threads to simulate high load and force overlaps
  private final int NUM_THREADS = 50;

  public void run(
      String writeUrl,
      List<String> readUrls,
      int totalRequests,
      WorkloadGenerator generator,
      MetricsCollector metrics,
      StaleReadTracker staleTracker) throws InterruptedException {

    ExecutorService threadPool = Executors.newFixedThreadPool(NUM_THREADS);
    CountDownLatch latch = new CountDownLatch(totalRequests);

    for (int i = 0; i < totalRequests; i++) {
      final int requestId = i;

      threadPool.submit(() -> {
        try {
          String key = generator.nextKey();
          long requestStartTime = System.currentTimeMillis();
          
          // Record the time interval between accesses to this specific key (Required for PDF graphs)
          metrics.recordKeyAccessInterval(key, requestStartTime);

          if (generator.isWrite()) {
            PutResponse response = client.put(writeUrl, key, generator.nextValue());
            long end = System.currentTimeMillis();

            metrics.recordWriteLatency(end - requestStartTime);
            if (response != null) {
              staleTracker.recordWrite(key, response.getVersion());
            }
          } else {
            // Round-robin load balancing for reads
            String readUrl = readUrls.get(requestId % readUrls.size());
            GetResponse response = client.get(readUrl, key);
            long end = System.currentTimeMillis();

            metrics.recordReadLatency(end - requestStartTime);
            if (response != null) {
              staleTracker.recordRead(key, response.getVersion());
            }
          }
        } catch (Exception e) {
          System.err.println("Request failed: " + e.getMessage());
        } finally {
          // Ensure latch counts down even if the request throws an exception
          latch.countDown();
        }
      });
    }

    // Main thread blocks here until all concurrent requests finish
    latch.await();
    threadPool.shutdown();
  }
}