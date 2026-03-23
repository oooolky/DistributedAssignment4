package com.cs6650.loadtester;

import com.cs6650.loadtester.generator.WorkloadGenerator;
import com.cs6650.loadtester.metrics.CsvExporter;
import com.cs6650.loadtester.metrics.MetricsCollector;
import com.cs6650.loadtester.metrics.StaleReadTracker;
import com.cs6650.loadtester.runner.LoadTestRunner;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Entry point for the KV store load tester.
 *
 * <p>Configuration is read from CLI arguments first, then environment variables,
 * then falls back to sensible defaults for local docker-compose testing.
 *
 * <p>Usage:
 * <pre>
 *   java -jar load-tester.jar [writeUrl] [readUrls] [totalRequests] [writePercentage] [keySpace]
 * </pre>
 *
 * <p>Arguments:
 * <ul>
 *   <li>{@code writeUrl}        – URL of the write endpoint (Leader for LF, ALB for Leaderless)</li>
 *   <li>{@code readUrls}        – Comma-separated list of read URLs</li>
 *   <li>{@code totalRequests}   – Total number of requests to generate</li>
 *   <li>{@code writePercentage} – Percentage of requests that are writes (0–100)</li>
 *   <li>{@code keySpace}        – Number of distinct keys to use</li>
 * </ul>
 *
 * <p>Environment variable equivalents (used when no CLI argument is provided):
 * <ul>
 *   <li>{@code LT_WRITE_URL}        (default: http://localhost:8080)</li>
 *   <li>{@code LT_READ_URLS}        (default: all 5 localhost ports)</li>
 *   <li>{@code LT_TOTAL_REQUESTS}   (default: 1000)</li>
 *   <li>{@code LT_WRITE_PERCENTAGE} (default: 10)</li>
 *   <li>{@code LT_KEY_SPACE}        (default: 100)</li>
 * </ul>
 */
public class Main {

    public static void main(String[] args) {
        String writeUrl = resolveString(
                args, 0, "LT_WRITE_URL", "http://localhost:8080");

        String readUrlsCsv = resolveString(
                args, 1, "LT_READ_URLS",
                "http://localhost:8080,http://localhost:8081,http://localhost:8082,"
                        + "http://localhost:8083,http://localhost:8084");

        int totalRequests    = resolveInt(args, 2, "LT_TOTAL_REQUESTS",   1000);
        int writePercentage  = resolveInt(args, 3, "LT_WRITE_PERCENTAGE",   10);
        int keySpace         = resolveInt(args, 4, "LT_KEY_SPACE",          100);

        List<String> readUrls = Arrays.stream(readUrlsCsv.split(","))
                .map(String::trim)
                .filter(url -> !url.isBlank())
                .collect(Collectors.toList());

        System.out.println("=== Load Test Configuration ===");
        System.out.println("Write URL       : " + writeUrl);
        System.out.println("Read URLs       : " + readUrls);
        System.out.println("Total requests  : " + totalRequests);
        System.out.println("Write percentage: " + writePercentage + "%");
        System.out.println("Key space       : " + keySpace);
        System.out.println("================================\n");

        WorkloadGenerator generator = new WorkloadGenerator(writePercentage, keySpace);
        MetricsCollector metrics = new MetricsCollector();
        StaleReadTracker staleTracker = new StaleReadTracker();

        try {
            System.out.println("Starting load test...");
            new LoadTestRunner().run(writeUrl, readUrls, totalRequests, generator, metrics, staleTracker);

            System.out.println("\n--- Load Test Completed ---");
            System.out.println("Read requests   : " + metrics.getReadLatencies().size());
            System.out.println("Write requests  : " + metrics.getWriteLatencies().size());
            System.out.println("Stale reads     : " + staleTracker.getStaleReads());
            System.out.println("Key intervals   : " + metrics.getKeyAccessIntervals().size());

            // Prefix includes write percentage so runs never overwrite each other.
            String filePrefix = "run_" + writePercentage + "pctWrites_" + System.currentTimeMillis();
            System.out.println("\n--- Exporting CSVs (prefix: " + filePrefix + ") ---");
            CsvExporter.exportAll(metrics, filePrefix);

        } catch (InterruptedException e) {
            System.err.println("Load test was interrupted.");
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Returns the CLI arg at {@code index} if present, otherwise the env var {@code envKey},
     * otherwise {@code defaultValue}.
     */
    private static String resolveString(String[] args, int index, String envKey, String defaultValue) {
        if (args.length > index && !args[index].isBlank()) {
            return args[index];
        }
        String envValue = System.getenv(envKey);
        return (envValue != null && !envValue.isBlank()) ? envValue : defaultValue;
    }

    /** Same as {@link #resolveString} but parses the result as an integer. */
    private static int resolveInt(String[] args, int index, String envKey, int defaultValue) {
        String raw = resolveString(args, index, envKey, String.valueOf(defaultValue));
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid integer for arg[" + index + "] / env " + envKey + ": " + raw, e);
        }
    }
}
