import React, { useState } from 'react';
import { Alert, Button, Card, Form, Input, Select, Space, Table, Tag, Popconfirm } from 'antd';

const ConsumerPanel = ({
  form,
  ready,
  loading,
  topicNames,
  consumers,
  selectedTopic,
  onCreate,
  onStart,
  onStop,
  onUpdateTopics,
  onDelete
}) => {
  const [editingClientId, setEditingClientId] = useState('');
  const [editingTopics, setEditingTopics] = useState([]);

  return (
    <Card title="Consumer Management" variant="borderless">
      <Space direction="vertical" style={{ width: '100%' }}>
        <Alert
          type={selectedTopic ? 'info' : 'warning'}
          showIcon
          message={selectedTopic
            ? `Selected Topic: ${selectedTopic}（Consumer 必须订阅该 Topic 才能消费）`
            : '请在 Topics 中点击选择一个 Topic'}
        />

        <Form layout="inline" form={form} initialValues={{ autoCommit: true }}>
          <Form.Item name="groupId">
            <Input placeholder="Group ID (可空自动生成)" style={{ width: 170 }} />
          </Form.Item>
          <Form.Item name="clientId">
            <Input placeholder="Client ID (可空自动生成)" style={{ width: 190 }} />
          </Form.Item>
          <Form.Item name="hostIp">
            <Input placeholder="Host IP" style={{ width: 150 }} />
          </Form.Item>
          <Form.Item name="topics" rules={[{ required: true, message: '至少选择一个 Topic' }]}>
            <Select
              mode="multiple"
              placeholder="订阅 Topics"
              style={{ minWidth: 280 }}
              options={topicNames.map((name) => ({ label: name, value: name }))}
              disabled={!ready || topicNames.length === 0}
            />
          </Form.Item>
          <Form.Item>
            <Button type="primary" onClick={onCreate} disabled={!ready}>Add Consumer</Button>
          </Form.Item>
        </Form>

        <Table
          loading={loading}
          dataSource={consumers}
          rowKey="clientId"
          pagination={false}
          size="small"
          columns={[
            { title: 'Client', dataIndex: 'clientId' },
            { title: 'Group', dataIndex: 'groupId' },
            { title: 'Host', dataIndex: 'hostIp' },
            {
              title: 'Topics',
              dataIndex: 'topics',
              render: (topics = [], row) => {
                if (editingClientId === row.clientId) {
                  return (
                    <Select
                      mode="multiple"
                      style={{ minWidth: 220 }}
                      value={editingTopics}
                      onChange={setEditingTopics}
                      options={topicNames.map((name) => ({ label: name, value: name }))}
                    />
                  );
                }
                return <Space wrap>{topics.map((topic) => <Tag key={topic}>{topic}</Tag>)}</Space>;
              }
            },
            {
              title: 'Status',
              dataIndex: 'status',
              render: (status) => <Tag color={status === 'RUNNING' ? 'green' : 'default'}>{status}</Tag>
            },
            {
              title: 'Action',
              render: (_, row) => {
                if (editingClientId === row.clientId) {
                  return (
                    <Space>
                      <Button
                        type="primary"
                        size="small"
                        onClick={async () => {
                          await onUpdateTopics(row.clientId, editingTopics);
                          setEditingClientId('');
                          setEditingTopics([]);
                        }}
                      >
                        Save
                      </Button>
                      <Button
                        size="small"
                        onClick={() => {
                          setEditingClientId('');
                          setEditingTopics([]);
                        }}
                      >
                        Cancel
                      </Button>
                    </Space>
                  );
                }

                const canOperateTopic = selectedTopic && (row.topics || []).includes(selectedTopic);
                const runButton = row.status === 'RUNNING'
                  ? <Button disabled={!ready} onClick={() => onStop(row.clientId)}>Stop</Button>
                  : (
                    <Button
                      type="primary"
                      disabled={!ready || !canOperateTopic}
                      onClick={() => onStart(row.clientId)}
                    >
                      Start
                    </Button>
                  );

                if (row.status === 'RUNNING') {
                  return (
                    <Space>
                      {runButton}
                      <Button
                        size="small"
                        onClick={() => {
                          setEditingClientId(row.clientId);
                          setEditingTopics([...(row.topics || [])]);
                        }}
                      >
                        Edit Topics
                      </Button>
                      <Popconfirm
                        title={`Delete consumer ${row.clientId}?`}
                        onConfirm={() => onDelete(row.clientId)}
                      >
                        <Button size="small" danger>Delete</Button>
                      </Popconfirm>
                    </Space>
                  );
                }
                return (
                  <Space>
                    {runButton}
                    <Button
                      size="small"
                      onClick={() => {
                        setEditingClientId(row.clientId);
                        setEditingTopics([...(row.topics || [])]);
                      }}
                    >
                      Edit Topics
                    </Button>
                    <Popconfirm
                      title={`Delete consumer ${row.clientId}?`}
                      onConfirm={() => onDelete(row.clientId)}
                    >
                      <Button size="small" danger>Delete</Button>
                    </Popconfirm>
                  </Space>
                );
              }
            }
          ]}
        />
      </Space>
    </Card>
  );
};

export default ConsumerPanel;
