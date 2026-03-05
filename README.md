# Kafka Lab

Kafka Lab 是一个用于 Kafka 学习、功能测试、场景模拟、故障复现的可视化实验平台。

## 目录结构

- `docker/`：Docker Compose 编排
- `kafka-lab-server/`：Spring Boot 后端
- `kafka-lab-ui/`：React + Ant Design 前端
- `docs/`：架构与场景文档

## GroupId 约定

后端 Maven `groupId` 使用 `xpro.wang` 作为前缀：

- `xpro.wang.kafkalab`

## 快速启动

### 1) 启动基础设施 + 服务

```bash
cd docker
docker compose up -d --build
```

### 2) 访问地址

- Kafka Lab UI: http://localhost:5173
- Kafka Lab Server: http://localhost:8080
- Kafka UI: http://localhost:8085

### 3) 本地开发（可选）

后端：

```bash
cd kafka-lab-server
mvn spring-boot:run
```

前端：

```bash
cd kafka-lab-ui
npm install
npm run dev
```

## 已实现 API 骨架

- Topic 管理：`/topics`
- Producer：`/producer/send`
- Consumer：`/consumer/start` `/consumer/stop` `/consumer/groups`
- Cluster：`/cluster` `/brokers` `/dashboard`
- Scenarios：`/scenario/run`
- Environment Manager：`/env/create` `/env/{id}/start` `/env/{id}/stop` `/env/{id}` `/env/{id}/status` `/env/{id}/logs` `/env`

## 控制平面部署模式

支持将 `kafka-lab-server` + `kafka-lab-ui` 作为控制平面单独部署，通过 Web UI 动态创建实验 Kafka Compose 实例。

后端关键配置：

- `KAFKA_LAB_ENV_ROOT`：实例 compose 与元数据存放目录
- `KAFKA_LAB_WORKSPACE_ROOT`：用于 compose 容器模式的主机路径映射根目录
- `KAFKA_LAB_DOCKER_CONFIG`：可选，覆盖 Docker 凭据配置目录

注意：

- 建议为控制平面加鉴权，避免未授权 Docker 操作。
- 多实例场景请确保 `externalPortBase`、`kafkaUiPort` 不冲突。

## 路线建议

- Phase1：Topic/Producer/Consumer 完整能力
- Phase2：Scenario Engine 深化（真实 listener + DLT + Retry）
- Phase3：UI 交互增强 + Cluster 管理
- Phase4：故障模拟自动化 + Metrics
