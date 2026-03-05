import React from 'react';
import { Alert, Button, Card, Col, Form, Input, InputNumber, Row, Switch } from 'antd';

const ProducerPanel = ({ form, ready, selectedTopic, onSend }) => {
  const canSend = ready && !!selectedTopic;

  return (
    <Card title="Producer" bordered={false}>
      <Form form={form} layout="vertical" initialValues={{ count: 1, delay: 0, transactional: false }}>
        <Alert
          type={selectedTopic ? 'info' : 'warning'}
          showIcon
          message={selectedTopic ? `当前 Topic：${selectedTopic}` : '请先在上方选择一个已创建 Topic'}
          style={{ marginBottom: 12 }}
        />
        <Form.Item name="key" label="Key">
          <Input placeholder="随机 key 留空" />
        </Form.Item>
        <Form.Item name="message" label="Message">
          <Input.TextArea rows={3} placeholder="留空则默认 hello kafka lab" />
        </Form.Item>

        <Row gutter={8}>
          <Col xs={12} sm={12} md={8}>
            <Form.Item name="partition" label="Partition">
              <InputNumber min={0} style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col xs={12} sm={12} md={8}>
            <Form.Item name="delay" label="Delay(ms)">
              <InputNumber min={0} style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col xs={12} sm={12} md={8}>
            <Form.Item name="count" label="Count" rules={[{ required: true }]}> 
              <InputNumber min={1} style={{ width: '100%' }} />
            </Form.Item>
          </Col>
        </Row>

        <Form.Item name="transactional" label="Transactional" valuePropName="checked" style={{ marginBottom: 12 }}>
          <Switch />
        </Form.Item>
        <Button type="primary" onClick={onSend} disabled={!canSend}>Send Message</Button>
      </Form>
    </Card>
  );
};

export default ProducerPanel;