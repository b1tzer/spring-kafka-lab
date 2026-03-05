import React from 'react';
import { Alert, Button, Card, Form, Input, InputNumber, Space, Switch, Table } from 'antd';

const ConsumerPanel = ({ form, ready, loading, groups, selectedTopic, onStart, onStop }) => {
  const canStart = ready && !!selectedTopic;

  return (
    <Card title="Consumer" bordered={false}>
      <Space direction="vertical" style={{ width: '100%' }}>
        <Alert
          type={selectedTopic ? 'info' : 'warning'}
          showIcon
          message={selectedTopic ? `当前 Topic：${selectedTopic}` : '请先在上方选择一个已创建 Topic'}
        />
        <Form layout="inline" form={form} initialValues={{ concurrency: 1, autoCommit: true }}>
          <Form.Item name="groupId">
            <Input placeholder="Group 标识(可空，自动生成)" />
          </Form.Item>
          <Form.Item name="concurrency">
            <InputNumber min={1} />
          </Form.Item>
          <Form.Item name="autoCommit" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item>
            <Button type="primary" onClick={onStart} disabled={!canStart}>Start Consumer</Button>
          </Form.Item>
        </Form>
        <Table
          loading={loading}
          dataSource={groups}
          columns={[
            { title: 'Group', dataIndex: 'groupId' },
            { title: 'State', dataIndex: 'state' },
            { title: 'Members', dataIndex: 'members' },
            { title: 'Managed By Lab', dataIndex: 'managedByLab', render: (v) => String(v) },
            {
              title: 'Action',
              render: (_, row) => <Button disabled={!ready} onClick={() => onStop(row.groupId)}>Stop</Button>
            }
          ]}
          pagination={false}
        />
      </Space>
    </Card>
  );
};

export default ConsumerPanel;