https://gemini.google.com/share/da85714b2e0a

## CS6650 Assignment 4 Report
https://docs.google.com/document/d/1AR6HFxyPiv-obOdjiWJVDkRo794sbfMJ6fu36DqUFK0/edit?usp=sharing

## 2026.03.19 Change

### feat: add docker, terraform, and consistency test support

#### What was added

- **Dockerfile** — Multi-stage build (Maven compile → JRE runtime). Produces a minimal
  image that runs `database-node` as an executable fat JAR via `java -jar`.
- **docker-compose.yml** — 5-node Leader-Follower cluster (default W=5/R=1) for local
  integration testing without AWS.
- **docker-compose.w1r1.override.yml** — Override to W=1/R=1, used to expose the
  inconsistency window required by Test 3.
- **docker-compose.w3r3.override.yml** — Override to W=3/R=3 (balanced quorum).
- **docker-compose.leaderless.yml** — 5-node Leaderless cluster (W=N, R=1).
- **terraform/** — Infrastructure as code for AWS: 5 database EC2 instances, 1 load-tester
  EC2, Application Load Balancer, ECR repository, IAM role for ECR pull, and security groups.
- **scripts/deploy.sh** — Reads Terraform outputs and SSHes into each EC2 node to start
  the database-node container with the correct environment variables per configuration
  (lf-w5r1 / lf-w1r1 / lf-w3r3 / leaderless).
- **scripts/build-and-push.sh** — Builds the Docker image and pushes it to ECR.
- **scripts/run-load-test.sh** — Runs all four read/write ratios (W=1%, 10%, 50%, 90%)
  against a deployed cluster and downloads the resulting CSV files.
- **scripts/plot_results.py** — Generates latency histograms, CDFs, and key access interval
  distributions for the report.
- **LeaderFollowerConsistencyTest.java** — Integration tests for Tests 1, 2, and 3 as
  specified in the assignment.
- **LeaderlessConsistencyTest.java** — Integration test for the Leaderless inconsistency
  window.
- **DEPLOYMENT.md** — Step-by-step deployment guide covering local testing, AWS
  provisioning, and load test execution.

#### Why these were needed

The original codebase contained the application logic but could not be built into a
deployable artifact, tested in isolation, or provisioned on AWS. Specifically:

- Without a Dockerfile and docker-compose files, there was no way to run a 5-node cluster
  locally to verify replication behaviour before deploying to AWS.
- Without Terraform, each of the four test configurations (three Leader-Follower variants
  plus Leaderless) would require manually creating EC2 instances, security groups, and an
  ALB through the AWS Console — and repeating that process for every configuration switch.
- Without unit tests, there was no automated way to verify that the consistency guarantees
  (strong consistency under W=5, inconsistency window under W=1) actually hold.

#### Bugs fixed

| File | Problem | Fix |
|------|---------|-----|
| `database-node/pom.xml` | `spring-web` was re-declared with `scope=test`, which overrode the `compile`-scope transitive dependency brought in by `spring-boot-starter-web`. This made `RestTemplate`, `ResponseEntity`, `@RestController`, and all Spring Web annotations invisible to the main source tree, causing 43 compilation errors. | Removed the erroneous `spring-web` test-scope declaration. |
| `database-node/pom.xml` | The parent POM uses `dependencyManagement` import rather than inheriting `spring-boot-starter-parent`, so `spring-boot-maven-plugin` had no version and its `repackage` goal was not bound to the `package` phase. `mvn package` produced a plain JAR with no embedded Tomcat or dependencies; `java -jar` would fail with "no main manifest attribute". | Added explicit `<version>` and `<execution><goal>repackage</goal></execution>` to the plugin declaration. |
| `LeaderFollowerReplicationCoordinator.java` | Regardless of the configured W value, the coordinator always replicated synchronously to all 4 Followers before returning. This made W=1 and W=3 behave identically to W=5 — the inconsistency window that Test 3 relies on never existed. | Followers beyond the (W−1) synchronous target are now updated asynchronously in the background, creating a real inconsistency window for W<5. |
| `KvController.java` | No error handling was present. Any exception thrown by the service layer would propagate as an uncontrolled 500 response with no log output. The assignment requires nodes to return 503 when a peer is unreachable. | Added try-catch blocks that return 400 for bad requests and 503 when the write or read quorum cannot be satisfied. |
| `InternalNodeClient.java` | All exceptions were silently swallowed (`catch (Exception e) { return false; }`), making it impossible to diagnose connectivity failures in logs. | Separated `ResourceAccessException` (node unreachable) from unexpected errors, and added structured log messages including the target URL and key for both cases. |
