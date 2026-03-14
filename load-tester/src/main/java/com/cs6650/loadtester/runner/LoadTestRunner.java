package com.cs6650.loadtester.runner;

import com.cs6650.common.dto.GetResponse;
import com.cs6650.common.dto.PutResponse;
import com.cs6650.loadtester.client.KvHttpClient;
import com.cs6650.loadtester.generator.WorkloadGenerator;
import com.cs6650.loadtester.metrics.MetricsCollector;
import com.cs6650.loadtester.metrics.StaleReadTracker;
import java.util.List;

public class LoadTestRunner {
  private final KvHttpClient client = new KvHttpClient();

  public void run(
      String writeUrl,
      List<String> readUrls,
      int totalRequests,
      WorkloadGenerator generator,
      MetricsCollector metrics,
      StaleReadTracker staleTracker) {

    for (int i = 0; i < totalRequests; i++) {
      String key = generator.nextKey();

      if (generator.isWrite()) {
        long start = System.currentTimeMillis();
        PutResponse response = client.put(writeUrl, key, generator.nextValue());
        long end = System.currentTimeMillis();

        metrics.recordWriteLatency(end - start);
        staleTracker.recordWrite(key, response.getVersion());
      } else {
        String readUrl = readUrls.get(i % readUrls.size());
        long start = System.currentTimeMillis();
        GetResponse response = client.get(readUrl, key);
        long end = System.currentTimeMillis();

        metrics.recordReadLatency(end - start);
        if (response != null) {
          staleTracker.recordRead(key, response.getVersion());
        }
      }
    }
  }
}
