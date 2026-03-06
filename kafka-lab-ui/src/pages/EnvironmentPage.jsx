import React, { useEffect, useState } from "react";
import {
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Modal,
  Space,
  Switch,
  Table,
  Typography,
  message,
} from "antd";
import {
  createEnvironment,
  deleteEnvironment,
  environmentLogs,
  environmentStatus,
  listEnvironments,
  startEnvironment,
  stopEnvironment,
} from "../api/kafkaLabApi";
import useLabRealtime from "../hooks/useLabRealtime";
import { LAB_REALTIME_EVENT_TYPE } from "../constants/labDomain";
import {
  extractFailureReason,
  showApiError,
  showErrorWithCopy,
} from "../utils/errorFeedback";

const EnvironmentPage = () => {
  const [form] = Form.useForm();
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(false);
  const [logsVisible, setLogsVisible] = useState(false);
  const [logsContent, setLogsContent] = useState("");

  const load = async () => {
    setLoading(true);
    try {
      const res = await listEnvironments();
      setRows((res.data || []).map((item) => ({ ...item, key: item.id })));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  useLabRealtime((event) => {
    if (event?.type === LAB_REALTIME_EVENT_TYPE.ENVIRONMENT_CHANGED) {
      load();
    }
  });

  const onCreate = async () => {
    try {
      const values = await form.validateFields();
      await createEnvironment(values);
      message.success("Environment created");
      form.resetFields();
      load();
    } catch (error) {
      showApiError("Environment create failed", error);
    }
  };

  const onStart = async (id) => {
    try {
      const res = await startEnvironment(id);
      if (res.data?.success) {
        message.success("Start success");
      } else {
        showErrorWithCopy(
          "Start failed",
          extractFailureReason(res, "Start failed"),
        );
      }
      load();
    } catch (error) {
      showApiError("Start failed", error);
    }
  };

  const onStop = async (id) => {
    try {
      const res = await stopEnvironment(id);
      if (res.data?.success) {
        message.success("Stop success");
      } else {
        showErrorWithCopy(
          "Stop failed",
          extractFailureReason(res, "Stop failed"),
        );
      }
      load();
    } catch (error) {
      showApiError("Stop failed", error);
    }
  };

  const onDelete = async (id) => {
    try {
      const res = await deleteEnvironment(id);
      if (res.data?.success) {
        message.success("Delete success");
      } else {
        showErrorWithCopy(
          "Delete failed",
          extractFailureReason(res, "Delete failed"),
        );
      }
      load();
    } catch (error) {
      showApiError("Delete failed", error);
    }
  };

  const onStatus = async (id) => {
    try {
      const res = await environmentStatus(id);
      if (res.data?.success && res.data?.output) {
        message.success("Status fetched");
      } else if (!res.data?.success) {
        showErrorWithCopy(
          "Status failed",
          extractFailureReason(res, "Status failed"),
        );
      }
      load();
    } catch (error) {
      showApiError("Status failed", error);
    }
  };

  const onLogs = async (id) => {
    try {
      const res = await environmentLogs(id, 120);
      if (res.data?.success) {
        setLogsContent(res.data?.output || "No logs");
        setLogsVisible(true);
      } else {
        showErrorWithCopy(
          "Fetch logs failed",
          extractFailureReason(res, "Fetch logs failed"),
        );
      }
    } catch (error) {
      showApiError("Fetch logs failed", error);
    }
  };

  return (
    <Space direction="vertical" style={{ width: "100%" }}>
      <Typography.Title level={3}>Environments</Typography.Title>
      <Card title="Create Kafka Environment">
        <Form
          form={form}
          layout="inline"
          initialValues={{
            name: "lab-env",
            brokerCount: 3,
            replicationFactor: 1,
            externalPortBase: 19092,
            kafkaUiEnabled: true,
            kafkaUiPort: 18085,
            kafkaImage: "confluentinc/cp-kafka:7.5.0",
            kafkaUiImage: "provectuslabs/kafka-ui:latest",
          }}
        >
          <Form.Item name="name" rules={[{ required: true }]}>
            <Input placeholder="name" />
          </Form.Item>
          <Form.Item name="brokerCount" rules={[{ required: true }]}>
            <InputNumber min={1} max={6} />
          </Form.Item>
          <Form.Item name="replicationFactor" rules={[{ required: true }]}>
            <InputNumber min={1} max={6} />
          </Form.Item>
          <Form.Item name="externalPortBase" rules={[{ required: true }]}>
            <InputNumber min={10000} max={60000} />
          </Form.Item>
          <Form.Item name="kafkaUiEnabled" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item name="kafkaUiPort">
            <InputNumber min={10000} max={65000} />
          </Form.Item>
          <Form.Item name="kafkaImage">
            <Input style={{ width: 240 }} />
          </Form.Item>
          <Form.Item name="kafkaUiImage">
            <Input style={{ width: 240 }} />
          </Form.Item>
          <Form.Item>
            <Button type="primary" onClick={onCreate}>
              Create
            </Button>
          </Form.Item>
        </Form>
      </Card>

      <Table
        loading={loading}
        dataSource={rows}
        pagination={false}
        columns={[
          { title: "ID", dataIndex: "id", width: 120 },
          { title: "Name", dataIndex: "name" },
          { title: "Project", dataIndex: "projectName" },
          { title: "Brokers", dataIndex: "brokerCount", width: 90 },
          { title: "Bootstrap", dataIndex: "bootstrapServers" },
          { title: "Status", dataIndex: "status", width: 130 },
          {
            title: "Action",
            render: (_, row) => (
              <Space wrap>
                <Button onClick={() => onStart(row.id)}>Start</Button>
                <Button onClick={() => onStop(row.id)}>Stop</Button>
                <Button onClick={() => onStatus(row.id)}>Status</Button>
                <Button onClick={() => onLogs(row.id)}>Logs</Button>
                <Button danger onClick={() => onDelete(row.id)}>
                  Delete
                </Button>
              </Space>
            ),
          },
        ]}
      />

      <Modal
        title="Environment Logs"
        width={1000}
        open={logsVisible}
        onCancel={() => setLogsVisible(false)}
        footer={null}
      >
        <pre
          style={{ maxHeight: 500, overflow: "auto", whiteSpace: "pre-wrap" }}
        >
          {logsContent}
        </pre>
      </Modal>
    </Space>
  );
};

export default EnvironmentPage;
