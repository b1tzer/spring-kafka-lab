import React from "react";
import { Tooltip } from "antd";
import { Handle, Position } from "reactflow";
import {
  CloudServerOutlined,
  DownloadOutlined,
  UploadOutlined,
} from "@ant-design/icons";

export const ProducerNode = ({ data }) => (
  <div className="topo-node producer">
    <Handle type="source" position={Position.Right} id="out" />
    <div className="topo-node-title">
      <UploadOutlined /> Producer
    </div>
    <div className="topo-node-line">Client: {data.clientId}</div>
    <div className="topo-node-rate">{data.rate || 0} Msg/s</div>
  </div>
);

export const ConsumerNode = ({ data }) => (
  <div
    className={`topo-node consumer ${Date.now() - (data.shakeAt || 0) < 220 ? "consumer-shake" : ""}`}
  >
    <Handle type="target" position={Position.Left} id="in" />
    <div className="topo-node-title">
      Consumer <DownloadOutlined />
    </div>
    <div className="topo-node-line">Group: {data.groupId}</div>
    <div className="topo-node-line">Client: {data.clientId}</div>
    <div className="topo-node-line">Host: {data.host}</div>
    <div className="topo-node-rate">{data.rate || 0} Msg/s</div>
  </div>
);

export const BrokerNode = ({ data }) => (
  <div className="topo-broker">
    <div className="topo-broker-head">
      <CloudServerOutlined /> Broker {data.brokerId}
    </div>
    <div className="topo-broker-meta">{data.hostPort}</div>
    <div className="topo-broker-meta">Disk: {data.diskUsage}</div>
  </div>
);

export const PartitionNode = ({ data }) => (
  <Tooltip
    title={`LogStartOffset: ${data.logStartOffset} | LogEndOffset: ${data.logEndOffset} | MessageCount: ${data.messageCount}`}
  >
    <div
      className={`topo-partition ${data.active ? "active" : ""}`}
      data-ripple={Date.now() - (data.rippleAt || 0) < 260}
    >
      <Handle type="target" position={Position.Left} id="in" />
      <Handle type="source" position={Position.Right} id="out" />
      P-{data.pid}
    </div>
  </Tooltip>
);

export const topologyNodeTypes = {
  producerNode: ProducerNode,
  consumerNode: ConsumerNode,
  brokerNode: BrokerNode,
  partitionNode: PartitionNode,
};
