package com.cs6650.databasenode.replication;

import com.cs6650.common.config.NodeConfig;
import com.cs6650.common.dto.ReplicationPutRequest;
import com.cs6650.databasenode.client.InternalNodeClient;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Coordinates replication from the Leader to Follower nodes.
 *
 * <p>The W (write quorum) value controls how many nodes must acknowledge a write
 * before the Leader responds to the client:
 * <ul>
 *   <li><b>W=5</b>: All 4 followers replicated synchronously, then Leader writes locally.</li>
 *   <li><b>W=3</b>: 2 followers replicated synchronously, Leader writes locally (=3 total),
 *       remaining 2 followers receive the update asynchronously in the background.</li>
 *   <li><b>W=1</b>: Leader writes locally immediately; all 4 followers updated asynchronously.</li>
 * </ul>
 *
 * <p>This design deliberately creates an inconsistency window for W &lt; 5, which is the
 * core behaviour this assignment is designed to demonstrate and measure.
 */
@Component
public class LeaderFollowerReplicationCoordinator {

    private static final Logger logger =
            LoggerFactory.getLogger(LeaderFollowerReplicationCoordinator.class);

    // Cached thread pool for fire-and-forget background replication to non-quorum followers.
    private final ExecutorService backgroundExecutor = Executors.newCachedThreadPool();

    private final InternalNodeClient internalNodeClient;

    public LeaderFollowerReplicationCoordinator(InternalNodeClient internalNodeClient) {
        this.internalNodeClient = internalNodeClient;
    }

    /**
     * Replicates a write to followers, blocking until exactly (W-1) followers acknowledge.
     *
     * <p>The leader itself accounts for 1 slot in the quorum, so only (W-1) followers
     * need to respond synchronously. Any remaining followers beyond the quorum receive
     * the update asynchronously in the background (creating the inconsistency window).
     *
     * @param config             node configuration (follower URLs, quorum size)
     * @param replicationRequest the write to propagate
     * @return count of followers that acknowledged synchronously
     */
    public int replicateToFollowers(NodeConfig config, ReplicationPutRequest replicationRequest) {
        List<String> followerUrls = config.getFollowerUrls();
        // Leader counts as 1, so we need (W - 1) follower acknowledgements synchronously.
        int synchronousTarget = config.getWriteQuorumSize() - 1;

        int successCount = 0;
        List<String> backgroundFollowers = new ArrayList<>();

        for (int i = 0; i < followerUrls.size(); i++) {
            String followerUrl = followerUrls.get(i);

            if (i < synchronousTarget) {
                // Synchronous: block until this follower responds.
                boolean acknowledged = internalNodeClient.replicatePut(followerUrl, replicationRequest);
                if (acknowledged) {
                    successCount++;
                } else {
                    logger.warn("Follower {} did not acknowledge replication for key={}",
                            followerUrl, replicationRequest.getKey());
                }
            } else {
                // Beyond quorum: schedule for background delivery (inconsistency window here).
                backgroundFollowers.add(followerUrl);
            }
        }

        if (!backgroundFollowers.isEmpty()) {
            backgroundExecutor.submit(() -> replicateInBackground(backgroundFollowers, replicationRequest));
        }

        return successCount;
    }

    /** Sends replication requests to non-quorum followers without blocking the caller. */
    private void replicateInBackground(List<String> followerUrls, ReplicationPutRequest request) {
        for (String followerUrl : followerUrls) {
            boolean acknowledged = internalNodeClient.replicatePut(followerUrl, request);
            if (!acknowledged) {
                logger.warn("Background replication to {} failed for key={}",
                        followerUrl, request.getKey());
            }
        }
    }
}
