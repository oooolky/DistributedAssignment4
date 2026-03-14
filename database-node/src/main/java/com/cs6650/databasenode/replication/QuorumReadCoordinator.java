package com.cs6650.databasenode.replication;

import com.cs6650.common.dto.GetResponse;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class QuorumReadCoordinator {
  public GetResponse chooseLatest(List<GetResponse> responses) {
    return responses.stream()
        .filter(r -> r != null)
        .max(Comparator.comparingInt(GetResponse::getVersion))
        .orElse(null);
  }
}