import React, { useCallback, useEffect, useMemo, useState } from "react";
import {
  Alert,
  Badge,
  Card,
  Collapse,
  Empty,
  Divider,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
  Row,
  Col,
} from "antd";
import {
  ApartmentOutlined,
  CloudServerOutlined,
  DatabaseOutlined,
  DownloadOutlined,
  UploadOutlined,
} from "@ant-design/icons";
import { fetchClusterTopology } from "../api/kafkaLabApi";
import { LAB_REALTIME_EVENT_TYPE } from "../constants/labDomain";
import useRunningEnvironment from "../hooks/useRunningEnvironment";
import useLabRealtime from "../hooks/useLabRealtime";
import "../styles/clusterTopology.css";

const ClusterPage = () => {
  const [topology, setTopology] = useState();
  const [loading, setLoading] = useState(false);
  const [producerPulse, setProducerPulse] = useState(false);
  const [consumerPulse, setConsumerPulse] = useState(false);
  const { ready, runningEnvironment } = useRunningEnvironment();

  const loadTopology = useCallback(async () => {
    if (!ready) {
      setTopology(undefined);
      return;
    }
    setLoading(true);
    try {
      const res = await fetchClusterTopology();
      setTopology(res.data);
    } finally {
      setLoading(false);
    }
  }, [ready]);

  useEffect(() => {
    loadTopology();
  }, [loadTopology]);

  useLabRealtime((event) => {
    if (!ready) {
      return;
    }

    if (event?.type === LAB_REALTIME_EVENT_TYPE.PRODUCER_CHANGED) {
      setProducerPulse(true);
      window.setTimeout(() => setProducerPulse(false), 900);
      loadTopology();
      return;
    }

    if (event?.type === LAB_REALTIME_EVENT_TYPE.CONSUMER_CHANGED) {
      setConsumerPulse(true);
      window.setTimeout(() => setConsumerPulse(false), 900);
      loadTopology();
      return;
    }

    if (
      event?.type === LAB_REALTIME_EVENT_TYPE.TOPIC_CHANGED ||
      event?.type === LAB_REALTIME_EVENT_TYPE.ENVIRONMENT_CHANGED
    ) {
      loadTopology();
    }
  });

  const brokers = topology?.brokers || [];
  const topics = topology?.topics || [];
  const activeProducers = topology?.producer?.active || [];
  const activeConsumers = topology?.consumer?.subscriptions || [];

  const producerNodes = useMemo(
    () =>
      activeProducers.slice(0, 8).map((producer, index) => ({
        key: producer.producerId || `producer-${index}`,
        label: producer.producerId || `producer-${index + 1}`,
        topic: producer.topic,
      })),
    [activeProducers],
  );

  const consumerNodes = useMemo(
    () =>
      activeConsumers.slice(0, 8).map((consumer, index) => ({
        key: consumer.groupId || `consumer-${index}`,
        label: consumer.groupId || `consumer-${index + 1}`,
        topic: consumer.topic,
      })),
    [activeConsumers],
  );

  const topicPanels = useMemo(() => {
    return topics.map((topic) => {
      const partitionRows = (topic.partitions || []).map((partition) => ({
        key: `${topic.topic}-${partition.partition}`,
        ...partition,
      }));

      return {
        key: topic.topic,
        label: (
          <Space>
            <Typography.Text strong>{topic.topic}</Typography.Text>
            <Tag>{`partitions: ${topic.partitionCount}`}</Tag>
          </Space>
        ),
        children: (
          <Table
            size="small"
            loading={loading}
            dataSource={partitionRows}
            pagination={false}
            rowKey="key"
            expandable={{
              expandedRowRender: (partitionRow) => {
                const rows = (partitionRow.recentMessages || []).map(
                  (message, index) => ({
                    key: `${partitionRow.key}-${message.offset}-${index}`,
                    ...message,
                  }),
                );

                if (rows.length === 0) {
                  return (
                    <Empty
                      image={Empty.PRESENTED_IMAGE_SIMPLE}
                      description="No messages"
                    />
                  );
                }

                return (
                  <div className="memory-board">
                    {rows.map((row) => (
                      <div className="memory-row" key={row.key}>
                        <div className="memory-cell memory-time">
                          {row.timestamp || "-"}
                        </div>
                        <div className="memory-cell memory-offset">
                          offset {row.offset}
                        </div>
                        <div className="memory-cell memory-key">
                          key {row.key}
                        </div>
                        <div
                          className="memory-cell memory-value"
                          title={String(row.value)}
                        >
                          {String(row.value)}
                        </div>
                        <div className="memory-cell memory-producer">
                          {row.producerId || "-"}
                        </div>
                      </div>
                    ))}
                  </div>
                );
              },
            }}
            columns={[
              { title: "Partition", dataIndex: "partition", width: 100 },
              { title: "Leader", dataIndex: "leader", width: 100 },
              {
                title: "Replicas",
                dataIndex: "replicas",
                render: (replicas) => (replicas || []).join(", "),
              },
              {
                title: "Latest 10",
                render: (_, row) => (
                  <Tag color="blue">{(row.recentMessages || []).length}</Tag>
                ),
                width: 120,
              },
            ]}
          />
        ),
      };
    });
  }, [topics, loading]);

  return (
    <Space direction="vertical" style={{ width: "100%" }}>
      <Typography.Title level={3}>Kafka Runtime Topology</Typography.Title>
      {!ready && (
        <Alert
          type="warning"
          showIcon
          message="请先在 Environments 页面创建并启动一个 Kafka 环境，再查看集群信息"
        />
      )}
      {ready && runningEnvironment && (
        <Alert
          type="success"
          showIcon
          message={`当前环境：${runningEnvironment.name}`}
        />
      )}

      <Card title="Logical Topology Diagram" size="small">
        <Spin spinning={loading}>
          <div className="topology-graph">
            <div className="graph-lane graph-left">
              <Typography.Text strong>
                Producers ({activeProducers.length})
              </Typography.Text>
              {producerNodes.length === 0 ? (
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description="No active producer"
                />
              ) : (
                producerNodes.map((producer) => (
                  <div className="node-link-row" key={producer.key}>
                    <div
                      className={`node-card producer-node ${producerPulse ? "node-pulse" : ""}`}
                    >
                      <UploadOutlined />
                      <div className="node-main">{producer.label}</div>
                      <div className="node-sub">{producer.topic || "-"}</div>
                    </div>
                    <div
                      className={`link-line ${producerPulse ? "link-producer-animate" : ""}`}
                    />
                  </div>
                ))
              )}
            </div>

            <div className="graph-lane graph-center">
              <div className="kafka-core">
                <div className="kafka-title">
                  <DatabaseOutlined />
                  <span>Kafka Cluster ({topology?.kafkaMode || "KRaft"})</span>
                </div>
                <Divider style={{ margin: "10px 0" }} />
                <div className="broker-grid">
                  {brokers.map((broker) => (
                    <div className="broker-chip" key={broker.id}>
                      <CloudServerOutlined />
                      <span>{`broker-${broker.id}`}</span>
                      <Badge
                        status={broker.status === "UP" ? "success" : "error"}
                        text={broker.status}
                      />
                      <Tag>
                        {broker.activeController
                          ? "controller-active"
                          : "follower"}
                      </Tag>
                    </div>
                  ))}
                </div>
                <div className="cluster-meta">
                  <Tag
                    icon={<ApartmentOutlined />}
                  >{`topics: ${topics.length}`}</Tag>
                  <Tag>{`producers: ${activeProducers.length}`}</Tag>
                  <Tag>{`consumers: ${activeConsumers.length}`}</Tag>
                </div>
              </div>
            </div>

            <div className="graph-lane graph-right">
              <Typography.Text strong>
                Consumers ({activeConsumers.length})
              </Typography.Text>
              {consumerNodes.length === 0 ? (
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description="No active consumer"
                />
              ) : (
                consumerNodes.map((consumer) => (
                  <div className="node-link-row" key={consumer.key}>
                    <div
                      className={`link-line ${consumerPulse ? "link-consumer-animate" : ""}`}
                    />
                    <div
                      className={`node-card consumer-node ${consumerPulse ? "node-pulse" : ""}`}
                    >
                      <DownloadOutlined />
                      <div className="node-main">{consumer.label}</div>
                      <div className="node-sub">{consumer.topic || "-"}</div>
                    </div>
                  </div>
                ))
              )}
            </div>
          </div>
        </Spin>
      </Card>

      <Card title="Topic / Partition / Latest 10 Messages" size="small">
        <Collapse items={topicPanels} />
      </Card>
    </Space>
  );
};

export default ClusterPage;
