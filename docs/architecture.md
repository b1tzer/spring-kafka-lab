# Kafka Lab Architecture

## Overview

Kafka Lab 是一个用于 Kafka 学习、功能测试、场景模拟和故障复现的可视化实验平台。

```text
Web UI (React + Ant Design)
    -> REST API
Kafka Lab Server (Spring Boot + Spring Kafka + AdminClient)
    -> Kafka Cluster (kafka1, kafka2, kafka3)
    -> Kafka UI (Provectus)
```

## Backend Modules

- `controller`: 对外 REST 接口
- `service`: Topic / Producer / Consumer / Cluster 管理服务
- `scenario`: 场景编排引擎（当前在 `ScenarioEngineService`）
- `environment`: 动态环境管理（`EnvironmentManagerService`）

## Environment Manager (Control Plane)

`kafka-lab-server` 与 `kafka-lab-ui` 可独立部署为控制平面，通过 REST API 管理实验 Kafka 集群实例。

能力：

- 接收 Web UI 参数创建环境实例
- 生成实例级 `docker-compose.yml`
- 启动/停止/销毁实例
- 查询状态与拉取日志

关键约束：

- 所有 Docker 操作经后端执行，前端不直接操作 Docker Socket
- 每个实例使用唯一 compose project 名，避免容器/网络冲突
- 参数校验：`replicationFactor <= brokerCount`
- 支持命令回退：`docker compose` -> `docker-compose` -> `docker/compose` 容器

## Core APIs

- `POST /topics`
- `GET /topics`
- `DELETE /topics/{name}`
- `POST /producer/send`
- `POST /consumer/start`
- `POST /consumer/stop`
- `GET /consumer/groups`
- `GET /cluster`
- `GET /brokers`
- `POST /scenario/run`
- `POST /env/create`
- `POST /env/{id}/start`
- `POST /env/{id}/stop`
- `DELETE /env/{id}`
- `GET /env/{id}/status`
- `GET /env/{id}/logs`
- `GET /env`

## Environment

- Kafka Broker: 3.6.1 (Confluent 7.5.0 image)
- Spring Boot: 3.2.0
- Spring Kafka: 3.0.2
- Java: 17
- React: 18.2.0
- Ant Design: 5.27.3
