import React from 'react';
import { Alert, Space, Typography } from 'antd';

const LogsPage = () => {
  return (
    <Space direction="vertical" style={{ width: '100%' }}>
      <Typography.Title level={3}>Logs</Typography.Title>
      <Alert
        type="info"
        showIcon
        message="日志聚合入口"
        description="建议接入 Spring Boot Actuator、Prometheus、Grafana，并在后续阶段追加实时日志流。"
      />
    </Space>
  );
};

export default LogsPage;
