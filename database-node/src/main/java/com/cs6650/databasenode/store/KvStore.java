package com.cs6650.databasenode.store;

import com.cs6650.common.model.VersionedValue;

public interface KvStore {
  VersionedValue get(String key);
  void put(String key, VersionedValue value);
  boolean contains(String key);
}