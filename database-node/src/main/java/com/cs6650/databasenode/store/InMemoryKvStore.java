package com.cs6650.databasenode.store;
import com.cs6650.common.model.VersionedValue;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class InMemoryKvStore implements KvStore {
  private final ConcurrentHashMap<String, VersionedValue> map = new ConcurrentHashMap<>();

  @Override
  public VersionedValue get(String key) {
    return map.get(key);
  }

  @Override
  public void put(String key, VersionedValue value) {
    map.put(key, value);
  }

  @Override
  public boolean contains(String key) {
    return map.containsKey(key);
  }
}
