package com.cs6650.databasenode.service;

import com.cs6650.common.model.VersionedValue;
import org.springframework.stereotype.Service;

@Service
public class VersionService {
  public int nextVersion(VersionedValue current) {
    return current == null ? 1 : current.getVersion() + 1;
  }
}