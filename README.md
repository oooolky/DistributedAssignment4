https://gemini.google.com/share/da85714b2e0a

## CS6650 Assignment 4 Report
https://docs.google.com/document/d/1AR6HFxyPiv-obOdjiWJVDkRo794sbfMJ6fu36DqUFK0/edit?usp=sharing

## 2026.03.19更新

### feat: add docker, terraform, and consistency test support
### 更新代码时删除中文readme内容，中文部分仅作为便于沟通理解使用。

#### 新增内容

- **Dockerfile** — 多阶段构建（Maven 编译 → JRE 运行时），生成可通过 `java -jar` 直接启动的可执行 fat JAR 镜像。
- **docker-compose.yml** — 5 节点 Leader-Follower 集群（默认 W=5/R=1），用于在本地进行集成测试，无需依赖 AWS 环境。
- **docker-compose.w1r1.override.yml** — 覆盖配置为 W=1/R=1，用于暴露 Test 3 所需的不一致性窗口。
- **docker-compose.w3r3.override.yml** — 覆盖配置为 W=3/R=3（平衡法定人数）。
- **docker-compose.leaderless.yml** — 5 节点 Leaderless 集群（W=N，R=1）。
- **terraform/** — AWS 基础设施即代码：5 个数据库 EC2 实例、1 个负载测试 EC2、应用负载均衡器（ALB）、ECR 镜像仓库、用于拉取镜像的 IAM 角色，以及安全组配置。
- **scripts/deploy.sh** — 读取 Terraform 输出，通过 SSH 登录各 EC2 节点，按指定配置（lf-w5r1 / lf-w1r1 / lf-w3r3 / leaderless）启动数据库节点容器。
- **scripts/build-and-push.sh** — 构建 Docker 镜像并推送到 ECR。
- **scripts/run-load-test.sh** — 针对已部署集群依次运行四种读写比例（W=1%、10%、50%、90%），并下载生成的 CSV 结果文件。
- **scripts/plot_results.py** — 生成报告所需的延迟直方图、CDF 曲线图和键访问间隔分布图。
- **LeaderFollowerConsistencyTest.java** — 按作业要求实现的 Test 1、Test 2、Test 3 集成测试。
- **LeaderlessConsistencyTest.java** — Leaderless 模式不一致性窗口的集成测试。
- **DEPLOYMENT.md** — 包含本地测试、AWS 基础设施部署和负载测试执行的分步操作指南。

#### 新增原因

原始代码库包含了应用逻辑，但无法构建为可部署的产物，也无法进行隔离测试或在 AWS 上完成部署。具体问题如下：

- 缺少 Dockerfile 和 docker-compose 文件，意味着无法在本地运行 5 节点集群来验证复制行为，部署前无法进行任何本地联调。
- 缺少 Terraform，四种测试配置（三种 Leader-Follower 变体加上 Leaderless）的每次切换都需要在 AWS 控制台手动创建 EC2 实例、安全组和 ALB，工作量巨大且容易出错。
- 缺少单元测试，无法自动化验证一致性保证（W=5 下的强一致性、W=1 下的不一致性窗口）是否真正生效。

#### 修复的问题

| 文件 | 问题描述 | 修复方式 |
|------|---------|---------|
| `database-node/pom.xml` | `spring-web` 被重复声明为 `scope=test`，覆盖了 `spring-boot-starter-web` 以 `compile` scope 引入的传递依赖。这导致 `RestTemplate`、`ResponseEntity`、`@RestController` 等所有 Spring Web 注解对主源代码不可见，产生 43 个编译错误。 | 删除错误的 `spring-web` test scope 声明。 |
| `database-node/pom.xml` | Parent POM 使用 `dependencyManagement` 导入而非继承 `spring-boot-starter-parent`，导致 `spring-boot-maven-plugin` 没有版本信息，且 `repackage` goal 未绑定到 `package` 阶段。`mvn package` 只能生成普通 JAR，不含内嵌 Tomcat 和依赖，`java -jar` 会报 "no main manifest attribute" 错误。 | 在插件声明中显式添加 `<version>` 以及 `<execution><goal>repackage</goal></execution>`。 |
| `LeaderFollowerReplicationCoordinator.java` | 无论 W 配置为何值，协调器始终同步等待所有 4 个 Follower 完成复制才返回，导致 W=1 和 W=3 的行为与 W=5 完全相同——Test 3 所依赖的不一致性窗口实际上从未存在。 | 超出 (W−1) 同步目标的 Follower 改为后台异步更新，W<5 时真实的不一致性窗口得以正确呈现。 |
| `KvController.java` | 完全没有错误处理，服务层抛出的任何异常都会以不受控的 500 响应返回，且无日志输出。作业要求节点不可达时必须返回 503。 | 添加 try-catch，对无效请求返回 400，对写/读法定人数不满足的情况返回 503。 |
| `InternalNodeClient.java` | 所有异常被静默吞掉（`catch (Exception e) { return false; }`），无法通过日志诊断任何连接问题。 | 区分 `ResourceAccessException`（节点不可达）和其他异常，两种情况均输出包含目标 URL 和 key 的结构化日志。 |

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