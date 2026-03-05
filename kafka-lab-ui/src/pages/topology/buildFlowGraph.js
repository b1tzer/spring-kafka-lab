const BROKER_W = 250;
const BROKER_H = 220;
const PARTITION_SIZE = 26;

const producerNode = (producerId, index, rate) => ({
  id: producerId,
  type: 'producerNode',
  position: { x: 40, y: 60 + index * 110 },
  data: {
    clientId: producerId,
    rate: rate || 0
  },
  style: { width: 220 }
});

const consumerNode = (consumer, index, rate) => ({
  id: consumer.clientId,
  type: 'consumerNode',
  position: { x: 1220, y: 60 + index * 130 },
  data: {
    groupId: consumer.groupId,
    clientId: consumer.clientId,
    host: consumer.host || 'n/a',
    rate: rate || 0
  },
  style: { width: 250 }
});

const brokerNode = (broker, index) => ({
  id: `broker-${broker.id}`,
  type: 'brokerNode',
  position: {
    x: 420 + (index % 2) * 320,
    y: 80 + Math.floor(index / 2) * 280
  },
  data: {
    brokerId: broker.id,
    hostPort: broker.endpoint || `broker-${broker.id}:9092`,
    diskUsage: broker.diskUsage ?? '--'
  },
  style: { width: BROKER_W, height: BROKER_H }
});

const partitionNode = (partition, brokerId, index) => ({
  id: `b${brokerId}_t${partition.topic}_p${partition.partition}`,
  type: 'partitionNode',
  parentId: `broker-${brokerId}`,
  extent: 'parent',
  position: {
    x: 20 + (index % 6) * 36,
    y: 64 + Math.floor(index / 6) * 44
  },
  data: {
    pid: partition.partition,
    active: partition.leader === brokerId,
    logStartOffset: partition.logStartOffset ?? 0,
    logEndOffset: partition.logEndOffset ?? 0,
    messageCount: partition.messageCount ?? 0,
    topic: partition.topic
  },
  style: { width: PARTITION_SIZE, height: PARTITION_SIZE }
});

export const buildFlowGraph = ({ snapshot, producerRate = {}, consumerRate = {} }) => {
  if (!snapshot) {
    return { nodes: [], edges: [], partitionByTopic: {} };
  }

  const brokerItems = snapshot.brokers || [];
  const topics = snapshot.topics || [];
  const producerIds = snapshot.producers || [];
  const consumers = snapshot.consumers || [];

  const brokerNodes = brokerItems.map((broker, index) => brokerNode(broker, index));

  const partitionByTopic = {};
  const partitionNodes = [];
  brokerItems.forEach((broker) => {
    const partitions = topics.flatMap((topic) =>
      (topic.partitions || [])
        .filter((p) => p.leader === broker.id)
        .map((p) => ({ ...p, topic: topic.topic }))
    );

    partitions.forEach((partition, index) => {
      partitionNodes.push(partitionNode(partition, broker.id, index));
      if (!partitionByTopic[partition.topic]) {
        partitionByTopic[partition.topic] = [];
      }
      partitionByTopic[partition.topic].push(`b${broker.id}_t${partition.topic}_p${partition.partition}`);
    });
  });

  const pNodes = producerIds.map((id, index) => producerNode(id, index, producerRate[id]?.count));
  const cNodes = consumers.map((consumer, index) => consumerNode(consumer, index, consumerRate[consumer.clientId]?.count));

  const producerEdges = pNodes.flatMap((node) => {
    const targets = Object.values(partitionByTopic).flat().slice(0, 2);
    return targets.map((pid) => ({
      id: `e-${node.id}-${pid}`,
      source: node.id,
      target: pid,
      type: 'smoothstep',
      animated: false,
      style: { stroke: '#2d7bff33' }
    }));
  });

  const consumerEdges = cNodes.flatMap((node) => {
    const targets = Object.values(partitionByTopic).flat().slice(0, 2);
    return targets.map((pid) => ({
      id: `e-${pid}-${node.id}`,
      source: pid,
      target: node.id,
      type: 'smoothstep',
      animated: false,
      style: { stroke: '#ff9b2f33' }
    }));
  });

  return {
    nodes: [...pNodes, ...brokerNodes, ...partitionNodes, ...cNodes],
    edges: [...producerEdges, ...consumerEdges],
    partitionByTopic
  };
};
