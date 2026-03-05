import React, { useState } from 'react';
import { CloseOutlined } from '@ant-design/icons';
import { Button, Card, Empty, Form, Input, InputNumber, Space, Spin } from 'antd';

const TopicsPanel = ({ form, ready, loading, topics, onCreate, onDelete }) => {
  const [hoveredTopic, setHoveredTopic] = useState('');

  return (
    <Card title="Topics" bordered={false}>
      <Space direction="vertical" style={{ width: '100%' }} size={8}>
        <Form layout="inline" form={form} initialValues={{ partitionCount: 3, replicationFactor: 1 }}>
          <Form.Item name="topicName">
            <Input placeholder="标识(可空，自动生成并递增后缀)" style={{ width: 280 }} />
          </Form.Item>
          <Form.Item name="partitionCount" rules={[{ required: true }]}>
            <InputNumber min={1} />
          </Form.Item>
          <Form.Item name="replicationFactor" rules={[{ required: true }]}>
            <InputNumber min={1} />
          </Form.Item>
          <Form.Item>
            <Button type="primary" onClick={onCreate} disabled={!ready}>Create Topic</Button>
          </Form.Item>
        </Form>

        <Spin spinning={loading}>
          {topics.length === 0 ? (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无 Topic" />
          ) : (
            <Space wrap size={[8, 8]}>
              {topics.map((topic) => (
                <div
                  key={topic.name}
                  onMouseEnter={() => setHoveredTopic(topic.name)}
                  onMouseLeave={() => setHoveredTopic('')}
                  style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    border: '1px solid #d9d9d9',
                    borderRadius: 6,
                    padding: '4px 6px',
                    background: '#fff'
                  }}
                >
                  <Button type="text" size="small" style={{ paddingInline: 6, height: 24 }}>
                    {topic.name}
                  </Button>
                  {hoveredTopic === topic.name && (
                    <Button
                      type="text"
                      danger
                      size="small"
                      icon={<CloseOutlined />}
                      onClick={() => onDelete(topic.name)}
                      disabled={!ready}
                      style={{ width: 22, minWidth: 22, height: 22, padding: 0 }}
                    />
                  )}
                </div>
              ))}
            </Space>
          )}
        </Spin>
      </Space>
    </Card>
  );
};

export default TopicsPanel;