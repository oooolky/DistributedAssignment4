package com.cs6650.common.dto;

public class PutResponse {
  private String key;
  private int version;

  public PutResponse() {}

  public PutResponse(String key, int version) {
    this.key = key;
    this.version = version;
  }

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public int getVersion() {
    return version;
  }

  public void setVersion(int version) {
    this.version = version;
  }
}