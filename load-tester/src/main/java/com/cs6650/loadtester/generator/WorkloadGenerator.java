package com.cs6650.loadtester.generator;

import java.util.Random;

public class WorkloadGenerator {
  private final int writePercentage;
  private final int keySpace;
  private final Random random = new Random();

  public WorkloadGenerator(int writePercentage, int keySpace) {
    this.writePercentage = writePercentage;
    this.keySpace = keySpace;
  }

  public boolean isWrite() {
    return random.nextInt(100) < writePercentage;
  }

  public String nextKey() {
    return "key-" + random.nextInt(keySpace);
  }

  public String nextValue() {
    return "value-" + System.nanoTime();
  }
}
