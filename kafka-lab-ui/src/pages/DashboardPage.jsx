import React, { useEffect, useState } from "react";
import { Alert, Card, Col, Row, Typography } from "antd";
import { fetchDashboard } from "../api/kafkaLabApi";
import useRunningEnvironment from "../hooks/useRunningEnvironment";

const DashboardPage = () => {
  const [data, setData] = useState({
    brokerCount: 0,
    topicCount: 0,
    consumerGroupCount: 0,
  });
  const { ready, runningEnvironment } = useRunningEnvironment();

  useEffect(() => {
    if (!ready) {
      setData({ brokerCount: 0, topicCount: 0, consumerGroupCount: 0 });
      return;
    }

    fetchDashboard()
      .then((res) => {
        if (res.success && res.data) {
          setData(res.data);
        }
      })
      .catch(() => {});
  }, [ready]);

  return (
    <>
      <Typography.Title level={3}>Dashboard</Typography.Title>
      {!ready && (
        <Alert
          type="warning"
          showIcon
          message="请先在 Environments 页面创建并启动一个 Kafka 环境，Dashboard 才会显示实时数据"
          style={{ marginBottom: 16 }}
        />
      )}
      {ready && runningEnvironment && (
        <Alert
          type="success"
          showIcon
          message={`当前环境：${runningEnvironment.name}`}
          style={{ marginBottom: 16 }}
        />
      )}
      <Row gutter={16}>
        <Col span={8}>
          <Card title="Broker 状态">{data.brokerCount} brokers</Card>
        </Col>
        <Col span={8}>
          <Card title="Topic 数量">{data.topicCount}</Card>
        </Col>
        <Col span={8}>
          <Card title="Consumer Group">{data.consumerGroupCount}</Card>
        </Col>
      </Row>
    </>
  );
};

export default DashboardPage;
