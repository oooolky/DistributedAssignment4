package com.cs6650.databasenode.service;

import com.cs6650.common.config.NodeConfig;
import com.cs6650.common.dto.GetResponse;
import com.cs6650.common.dto.PutRequest;
import com.cs6650.common.dto.PutResponse;
import com.cs6650.common.dto.ReplicationPutRequest;
import com.cs6650.common.model.VersionedValue;
import com.cs6650.common.util.SleepUtil;
import com.cs6650.databasenode.client.InternalNodeClient;
import com.cs6650.databasenode.replication.LeaderFollowerReplicationCoordinator;
import com.cs6650.databasenode.replication.QuorumReadCoordinator;
import com.cs6650.databasenode.store.KvStore;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Handles KV operations for nodes running in Leader-Follower mode.
 *
 * <p><b>Write flow (Leader only):</b>
 * <ol>
 *   <li>Assign the next version number for the key.</li>
 *   <li>Replicate synchronously to exactly (W-1) followers; remaining followers
 *       receive the update asynchronously in the background.</li>
 *   <li>Sleep 200 ms to simulate a durable write, then store locally (= W-th node).</li>
 *   <li>Verify quorum is satisfied, then return 201 to the client.</li>
 * </ol>
 *
 * <p><b>Read flow (any node):</b>
 * <ul>
 *   <li>R=1: Read from this node's local store only.</li>
 *   <li>R &gt; 1: Collect responses from this node plus (R-1) followers;
 *       return the highest version seen.</li>
 * </ul>
 */
@Service
public class LeaderFollowerService {

    private static final Logger logger = LoggerFactory.getLogger(LeaderFollowerService.class);

    private final KvStore kvStore;
    private final NodeConfig nodeConfig;
    private final VersionService versionService;
    private final InternalNodeClient internalNodeClient;
    private final LeaderFollowerReplicationCoordinator replicationCoordinator;
    private final QuorumReadCoordinator quorumReadCoordinator;

    public LeaderFollowerService(
            KvStore kvStore,
            NodeConfig nodeConfig,
            VersionService versionService,
            InternalNodeClient internalNodeClient,
            LeaderFollowerReplicationCoordinator replicationCoordinator,
            QuorumReadCoordinator quorumReadCoordinator) {
        this.kvStore = kvStore;
        this.nodeConfig = nodeConfig;
        this.versionService = versionService;
        this.internalNodeClient = internalNodeClient;
        this.replicationCoordinator = replicationCoordinator;
        this.quorumReadCoordinator = quorumReadCoordinator;
    }

    /** Handles a client PUT request. Only the Leader should receive external writes. */
    public PutResponse put(PutRequest request) {
        if (!"leader".equalsIgnoreCase(nodeConfig.getRole())) {
            throw new IllegalStateException(
                    "Only the Leader accepts external writes in leader-follower mode.");
        }
        if (request.getKey() == null || request.getKey().isBlank()) {
            throw new IllegalArgumentException("Key cannot be empty.");
        }

        VersionedValue current = kvStore.get(request.getKey());
        int newVersion = versionService.nextVersion(current);

        ReplicationPutRequest replicationRequest =
                new ReplicationPutRequest(request.getKey(), request.getValue(), newVersion);

        // Step 1: synchronously replicate to (W-1) followers; rest go to background.
        int followerSuccesses =
                replicationCoordinator.replicateToFollowers(nodeConfig, replicationRequest);

        // Step 2: Leader sleeps 200 ms (simulates its own durable write), then stores locally.
        SleepUtil.sleepMillis(200);
        kvStore.put(request.getKey(), new VersionedValue(request.getValue(), newVersion));

        // Step 3: total = synchronous followers + leader itself.
        int totalSuccesses = followerSuccesses + 1;
        if (totalSuccesses < nodeConfig.getWriteQuorumSize()) {
            throw new RuntimeException(
                    "Write quorum not satisfied: required " + nodeConfig.getWriteQuorumSize()
                            + ", achieved " + totalSuccesses);
        }

        return new PutResponse(request.getKey(), newVersion);
    }

    /**
     * Handles a client GET request.
     * Works on both Leader and Follower nodes; quorum logic only engages when R > 1.
     */
    public GetResponse get(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Key cannot be empty.");
        }

        int readQuorum = nodeConfig.getReadQuorumSize();
        if (readQuorum <= 1) {
            return localReadWithDelay(key);
        }

        // R > 1: collect from this node plus (R-1) followers, return the latest version.
        List<GetResponse> responses = new ArrayList<>();
        GetResponse localResponse = localReadWithDelay(key);
        if (localResponse != null) {
            responses.add(localResponse);
        }

        for (String followerUrl : nodeConfig.getFollowerUrls()) {
            if (responses.size() >= readQuorum) {
                break;
            }
            GetResponse followerResponse = internalNodeClient.internalRead(followerUrl, key);
            if (followerResponse != null) {
                responses.add(followerResponse);
            }
        }

        return quorumReadCoordinator.chooseLatest(responses);
    }

    /**
     * Applies an incoming replication PUT from the Leader.
     * Sleeps 200 ms to simulate the cost of a durable write before storing locally.
     */
    public void applyReplication(String key, String value, int version) {
        SleepUtil.sleepMillis(200);
        kvStore.put(key, new VersionedValue(value, version));
    }

    /**
     * Reads this node's local value with the standard 50 ms read delay.
     * Used for R=1 reads and as the local portion of quorum reads.
     */
    public GetResponse localReadWithDelay(String key) {
        SleepUtil.sleepMillis(50);
        VersionedValue stored = kvStore.get(key);
        if (stored == null) {
            return null;
        }
        return new GetResponse(key, stored.getValue(), stored.getVersion());
    }

    /**
     * Returns this node's local value without any quorum coordination.
     * Used by the /local_read endpoint for consistency testing.
     */
    public GetResponse localReadWithoutQuorum(String key) {
        return localReadWithDelay(key);
    }
}
