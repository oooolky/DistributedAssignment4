package com.cs6650.common.dto;

public class ReplicationPutRequest {
  private String key;
  private String value;
  private int version;

  public ReplicationPutRequest() {}

  public ReplicationPutRequest(String key, String value, int version) {
    this.key = key;
    this.value = value;
    this.version = version;
  }

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public int getVersion() {
    return version;
  }

  public void setVersion(int version) {
    this.version = version;
  }
}
