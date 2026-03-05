# Kafka Lab Scenarios

## Rebalance

流程：
1. 启动 consumer（groupId 固定）
2. 发送消息
3. 启动更多 consumer
4. 观察 partition 分配变化

API:

- `POST /scenario/run`
```json
{
  "scenarioName": "rebalance",
  "parameters": {
    "topic": "lab-rebalance-topic",
    "groupId": "lab-rebalance-group"
  }
}
```

## Consumer Lag

流程：
1. 批量发送消息（默认 10000）
2. 启动低吞吐 consumer
3. 观察 lag 指标

## Poison Message / DLQ

流程：
1. Consumer 抛异常
2. 触发重试
3. 进入 DLT

当前状态：骨架中保留手动步骤提示。

## Broker Crash

流程：
1. `docker compose stop kafka2`
2. 观察 leader 选举
3. 恢复 broker 并验证集群状态

## Exactly Once

流程：
1. 开启事务发送
2. 模拟重试
3. 验证消息不重复
