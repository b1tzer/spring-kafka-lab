import React from "react";
import { Button, Card, Select, Space, Tag, Typography } from "antd";

const QUICK_TOPIC_LIMIT = 5;

const TopicPickerBar = ({ topicNames, selectedTopic, onChange, disabled }) => {
  const quickTopics = topicNames.slice(0, QUICK_TOPIC_LIMIT);

  return (
    <Card size="small" variant="borderless">
      <Space direction="vertical" style={{ width: "100%" }} size={8}>
        <Space wrap style={{ width: "100%", justifyContent: "space-between" }}>
          <Typography.Text strong>Active Topic</Typography.Text>
          {selectedTopic ? (
            <Tag color="blue">{selectedTopic}</Tag>
          ) : (
            <Tag>未选择</Tag>
          )}
        </Space>

        <Space wrap>
          <Select
            allowClear
            showSearch
            placeholder="从已有 Topic 中选择"
            style={{ width: 220 }}
            value={selectedTopic || undefined}
            options={topicNames.map((name) => ({ value: name, label: name }))}
            onChange={(value) => onChange(value || "")}
            disabled={disabled || topicNames.length === 0}
            optionFilterProp="label"
          />

          {quickTopics.map((topicName) => (
            <Button
              key={topicName}
              size="small"
              type={selectedTopic === topicName ? "primary" : "default"}
              onClick={() => onChange(topicName)}
              disabled={disabled}
            >
              {topicName}
            </Button>
          ))}
        </Space>
      </Space>
    </Card>
  );
};

export default TopicPickerBar;
