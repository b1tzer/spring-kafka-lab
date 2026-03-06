import React from "react";
import { Alert, Button, Card, Space, Typography, message } from "antd";
import { runScenario } from "../api/kafkaLabApi";
import useRunningEnvironment from "../hooks/useRunningEnvironment";

const scenarioButtons = [
  { name: "rebalance", label: "Run Rebalance" },
  { name: "consumer-lag", label: "Run Lag Simulation" },
  { name: "poison-message", label: "Run Retry Simulation" },
  { name: "broker-crash", label: "Run Broker Crash" },
  { name: "exactly-once", label: "Run Exactly Once" },
];

const ScenariosPage = () => {
  const { ready, runningEnvironment } = useRunningEnvironment();

  const onRun = async (scenarioName) => {
    if (!ready) {
      message.warning("请先在 Environments 页面创建并启动环境");
      return;
    }
    const res = await runScenario({ scenarioName, parameters: {} });
    message.info(res.data?.hint || `${scenarioName} executed`);
  };

  return (
    <Space direction="vertical" style={{ width: "100%" }}>
      <Typography.Title level={3}>Scenarios</Typography.Title>
      {!ready && (
        <Alert
          type="warning"
          showIcon
          message="请先在 Environments 页面创建并启动一个 Kafka 环境，再运行实验场景"
        />
      )}
      {ready && runningEnvironment && (
        <Alert
          type="success"
          showIcon
          message={`当前环境：${runningEnvironment.name}`}
        />
      )}
      <Card>
        <Space wrap>
          {scenarioButtons.map((item) => (
            <Button
              key={item.name}
              type="primary"
              disabled={!ready}
              onClick={() => onRun(item.name)}
            >
              {item.label}
            </Button>
          ))}
        </Space>
      </Card>
    </Space>
  );
};

export default ScenariosPage;
