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

/**
 * Runs the load test using a fixed thread pool.
 *
 * <p>Each request is classified as a write or read by the WorkloadGenerator.
 * Latency is recorded for every request that completes, including reads that
 * return 404 (key not yet written) — a 404 is a valid response that reflects
 * real read latency and is counted as a stale read for consistency tracking.
 */
public class LoadTestRunner {

    private final KvHttpClient client = new KvHttpClient();
    private static final int NUM_THREADS = 50;

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
            final int requestIndex = i;

            threadPool.submit(() -> {
                try {
                    String key = generator.nextKey();
                    long startTime = System.currentTimeMillis();

                    // Record the interval between successive accesses to this key.
                    metrics.recordKeyAccessInterval(key, startTime);

                    if (generator.isWrite()) {
                        PutResponse response = client.put(writeUrl, key, generator.nextValue());
                        long latency = System.currentTimeMillis() - startTime;
                        metrics.recordWriteLatency(latency);
                        if (response != null) {
                            staleTracker.recordWrite(key, response.getVersion());
                        }
                    } else {
                        // Round-robin across all read URLs.
                        String readUrl = readUrls.get(requestIndex % readUrls.size());
                        GetResponse response = client.get(readUrl, key);
                        long latency = System.currentTimeMillis() - startTime;

                        // Always record read latency — even for 404 (key not yet written).
                        // A 404 is a valid and measurable response; it is also a stale read
                        // if we have previously written that key.
                        metrics.recordReadLatency(latency);
                        if (response != null) {
                            staleTracker.recordRead(key, response.getVersion());
                        }
                        // response == null means 404: treat as version 0 for staleness.
                        // staleTracker.recordRead with version 0 will flag it as stale
                        // if a higher version was previously written.
                        if (response == null) {
                            staleTracker.recordRead(key, 0);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Request failed: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        threadPool.shutdown();
    }
}
