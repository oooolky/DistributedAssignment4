package com.cs6650.loadtester.generator;

import java.util.Random;

public class WorkloadGenerator {
  private final int writePercentage;
  private final int keySpace;
  private final Random random = new Random();
  
  // Maintain a hot pool of keys to create temporal locality for testing stale reads
  private final int[] recentKeys;
  private final int HOT_PROBABILITY = 80; // 80% chance to reuse a recent key

  public WorkloadGenerator(int writePercentage, int keySpace) {
    this.writePercentage = writePercentage;
    this.keySpace = keySpace;
    
    // Initialize the hot pool with a small subset of the key space
    int poolSize = Math.min(10, keySpace > 0 ? keySpace : 10);
    this.recentKeys = new int[poolSize];
    for (int i = 0; i < recentKeys.length; i++) {
        recentKeys[i] = random.nextInt(keySpace);
    }
  }

  public boolean isWrite() {
    return random.nextInt(100) < writePercentage;
  }

  public String nextKey() {
    if (random.nextInt(100) < HOT_PROBABILITY) {
        // High probability to hit a recently used key, increasing the chance of a collision
        return "key-" + recentKeys[random.nextInt(recentKeys.length)];
    } else {
        // Generate a brand new key and replace an existing one in the hot pool
        int newKeyId = random.nextInt(keySpace);
        recentKeys[random.nextInt(recentKeys.length)] = newKeyId;
        return "key-" + newKeyId;
    }
  }

  public String nextValue() {
    return "value-" + System.nanoTime();
  }
}