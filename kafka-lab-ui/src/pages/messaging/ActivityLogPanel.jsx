import React from 'react';
import { Card, Empty, List, Space, Tag, Typography } from 'antd';

const levelColorMap = {
  success: 'green',
  error: 'red',
  info: 'blue'
};

const ActivityLogPanel = ({ logs }) => {
  const renderFields = (fields = {}) => {
    const entries = Object.entries(fields).filter(([, value]) => value !== undefined && value !== null && value !== '');
    if (entries.length === 0) {
      return null;
    }

    return (
      <Space wrap size={[6, 4]} style={{ marginTop: 4 }}>
        {entries.map(([key, value]) => (
          <Tag key={key}>{`${key}: ${String(value)}`}</Tag>
        ))}
      </Space>
    );
  };

  return (
    <Card title="Activity Logs" bordered={false} size="small">
      {logs.length === 0 ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无操作日志" />
      ) : (
        <List
          size="small"
          dataSource={logs}
          style={{ maxHeight: 260, overflowY: 'auto' }}
          renderItem={(item) => (
            <List.Item style={{ paddingBlock: 6, display: 'block' }}>
              <Space wrap size={[8, 4]}>
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  {item.time}
                </Typography.Text>
                <Tag color={levelColorMap[item.level] || 'default'}>
                  {item.action}
                </Tag>
                <Typography.Text style={{ fontSize: 13 }}>{item.detail}</Typography.Text>
              </Space>
              {renderFields(item.fields)}
            </List.Item>
          )}
        />
      )}
    </Card>
  );
};

export default ActivityLogPanel;