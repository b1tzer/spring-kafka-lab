import React, { useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  Form,
  Input,
  InputNumber,
  Row,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Popconfirm
} from 'antd';

const ProducerPanel = ({
  createForm,
  sendForm,
  ready,
  selectedTopic,
  topicNames,
  producers,
  loading,
  onCreate,
  onSend,
  onUpdateTopics,
  onDelete
}) => {
  const [editingProducerId, setEditingProducerId] = useState('');
  const [editingTopics, setEditingTopics] = useState([]);

  const selectedProducerId = Form.useWatch('producerId', sendForm);
  const selectedProducer = producers.find((item) => item.producerId === selectedProducerId);
  const topicAllowed = selectedProducer && selectedTopic
    ? (selectedProducer.topics || []).includes(selectedTopic)
    : false;

  return (
    <Card title="Producer Management" bordered={false}>
      <Space direction="vertical" style={{ width: '100%' }}>
        <Form form={createForm} layout="inline">
          <Form.Item name="producerId">
            <Input placeholder="Producer ID (可空自动生成)" style={{ width: 220 }} />
          </Form.Item>
          <Form.Item
            name="topics"
            rules={[{ required: true, message: '至少选择一个 Topic' }]}
          >
            <Select
              mode="multiple"
              placeholder="订阅 Topics"
              style={{ minWidth: 280 }}
              options={topicNames.map((name) => ({ label: name, value: name }))}
              disabled={!ready || topicNames.length === 0}
            />
          </Form.Item>
          <Form.Item>
            <Button type="primary" onClick={onCreate} disabled={!ready}>Add Producer</Button>
          </Form.Item>
        </Form>

        <Table
          size="small"
          loading={loading}
          pagination={false}
          rowKey="producerId"
          dataSource={producers}
          columns={[
            { title: 'Producer ID', dataIndex: 'producerId' },
            {
              title: 'Topics',
              dataIndex: 'topics',
              render: (topics = [], row) => {
                if (editingProducerId === row.producerId) {
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
            { title: 'Created At', dataIndex: 'createdAt' },
            { title: 'Last Sent', dataIndex: 'lastSentAt' },
            {
              title: 'Action',
              render: (_, row) => {
                if (editingProducerId === row.producerId) {
                  return (
                    <Space>
                      <Button
                        type="primary"
                        size="small"
                        onClick={async () => {
                          await onUpdateTopics(row.producerId, editingTopics);
                          setEditingProducerId('');
                          setEditingTopics([]);
                        }}
                      >
                        Save
                      </Button>
                      <Button
                        size="small"
                        onClick={() => {
                          setEditingProducerId('');
                          setEditingTopics([]);
                        }}
                      >
                        Cancel
                      </Button>
                    </Space>
                  );
                }
                return (
                  <Space>
                    <Button
                      size="small"
                      onClick={() => {
                        setEditingProducerId(row.producerId);
                        setEditingTopics([...(row.topics || [])]);
                      }}
                    >
                      Edit Topics
                    </Button>
                    <Popconfirm
                      title={`Delete producer ${row.producerId}?`}
                      onConfirm={() => onDelete(row.producerId)}
                    >
                      <Button size="small" danger>Delete</Button>
                    </Popconfirm>
                  </Space>
                );
              }
            }
          ]}
        />

        <Form form={sendForm} layout="vertical" initialValues={{ count: 1, delay: 0, transactional: false }}>
          <Alert
            type={selectedTopic ? (topicAllowed ? 'success' : 'warning') : 'warning'}
            showIcon
            message={selectedTopic
              ? (topicAllowed
                ? `Selected Topic: ${selectedTopic}（已在 Producer 订阅列表中）`
                : `Selected Topic: ${selectedTopic}（请先选择订阅该 Topic 的 Producer）`)
              : '请在 Topics 中点击选择一个 Topic'}
          />

          <Row gutter={8} style={{ marginTop: 10 }}>
            <Col xs={24} md={12}>
              <Form.Item name="producerId" label="Producer" rules={[{ required: true }]}>
                <Select
                  placeholder="选择 Producer"
                  options={producers.map((item) => ({ label: item.producerId, value: item.producerId }))}
                  disabled={!ready || producers.length === 0}
                />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item name="key" label="Key">
                <Input placeholder="随机 key 留空" />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item name="message" label="Message">
            <Input.TextArea rows={3} placeholder="留空默认 hello kafka lab" />
          </Form.Item>

          <Row gutter={8}>
            <Col xs={12} sm={8}>
              <Form.Item name="partition" label="Partition">
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col xs={12} sm={8}>
              <Form.Item name="delay" label="Delay(ms)">
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col xs={12} sm={8}>
              <Form.Item name="count" label="Count" rules={[{ required: true }]}> 
                <InputNumber min={1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item name="transactional" label="Transactional" valuePropName="checked" style={{ marginBottom: 12 }}>
            <Switch />
          </Form.Item>

          <Button
            type="primary"
            onClick={onSend}
            disabled={!ready || !selectedTopic || !selectedProducerId || !topicAllowed}
          >
            Send Message
          </Button>
        </Form>
      </Space>
    </Card>
  );
};

export default ProducerPanel;
