import React, { useCallback, useEffect, useMemo, useRef } from "react";
import ReactFlow, {
  Background,
  Controls,
  MiniMap,
  ReactFlowProvider,
  useEdgesState,
  useNodesState,
} from "reactflow";
import { Alert, Space, Tag, Typography } from "antd";
import { fetchClusterTopology } from "../api/kafkaLabApi";
import useLabRealtime from "../hooks/useLabRealtime";
import useRunningEnvironment from "../hooks/useRunningEnvironment";
import useTopologyFlowStore from "../stores/useTopologyFlowStore";
import {
  LAB_REALTIME_EVENT_TYPE,
  TOPOLOGY_EVENT_TYPE,
} from "../constants/labDomain";
import { buildFlowGraph } from "./topology/buildFlowGraph";
import { topologyNodeTypes } from "./topology/nodeTypes";
import "../styles/topologyStudio.css";
import "reactflow/dist/style.css";

const MAX_PARTICLES = 500;

const toSnapshot = (payload) => {
  if (!payload) {
    return null;
  }

  if (
    payload.brokers &&
    payload.topics &&
    (payload.producers || payload.consumers)
  ) {
    return payload;
  }

  return {
    brokers: payload.brokers || [],
    topics: payload.topics || [],
    producers: (payload.producer?.active || []).map((item) => item.producerId),
    consumers: (payload.consumer?.subscriptions || []).map((item) => ({
      groupId: item.groupId,
      clientId: item.clientId || `${item.groupId}-client`,
      host: item.host || "127.0.0.1",
    })),
  };
};

const resolveAbsolute = (node, map) => {
  if (!node) {
    return { x: 0, y: 0 };
  }
  if (!node.parentId) {
    return { x: node.position.x, y: node.position.y };
  }
  const parent = map[node.parentId];
  const p = resolveAbsolute(parent, map);
  return { x: p.x + node.position.x, y: p.y + node.position.y };
};

const anchorFor = (nodes, id, side) => {
  const map = Object.fromEntries(nodes.map((n) => [n.id, n]));
  const node = map[id];
  if (!node) {
    return null;
  }

  const abs = resolveAbsolute(node, map);
  const width = Number(node.style?.width) || node.width || 180;
  const height = Number(node.style?.height) || node.height || 60;
  const y = abs.y + height / 2;
  const x = side === "left" ? abs.x : abs.x + width;
  return { x, y };
};

const pointOnCubic = (p0, p1, p2, p3, t) => {
  const mt = 1 - t;
  const x =
    mt ** 3 * p0.x +
    3 * mt ** 2 * t * p1.x +
    3 * mt * t ** 2 * p2.x +
    t ** 3 * p3.x;
  const y =
    mt ** 3 * p0.y +
    3 * mt ** 2 * t * p1.y +
    3 * mt * t ** 2 * p2.y +
    t ** 3 * p3.y;
  return { x, y };
};

const buildParticle = (from, to, color, speed = 0.03) => {
  const cpOffset = Math.max(80, Math.abs(to.x - from.x) * 0.35);
  return {
    active: true,
    t: 0,
    speed,
    color,
    p0: from,
    p1: { x: from.x + cpOffset, y: from.y },
    p2: { x: to.x - cpOffset, y: to.y },
    p3: to,
    trail: [],
  };
};

const TopologyStudioInner = () => {
  const { ready, runningEnvironment } = useRunningEnvironment();
  const canvasRef = useRef(null);
  const rafRef = useRef();
  const particlesRef = useRef(
    Array.from({ length: MAX_PARTICLES }, () => ({ active: false })),
  );

  const [nodes, setNodes, onNodesChange] = useNodesState([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState([]);

  const {
    topologySnapshot,
    setTopologySnapshot,
    markProducerSend,
    markConsumerRecv,
    consumeMatchedEvent,
    canCreateParticle,
    consumeBudget,
  } = useTopologyFlowStore();

  const refreshSnapshot = useCallback(async () => {
    if (!ready) {
      setTopologySnapshot(null);
      setNodes([]);
      setEdges([]);
      return;
    }
    const res = await fetchClusterTopology();
    setTopologySnapshot(toSnapshot(res.data));
  }, [ready, setTopologySnapshot, setNodes, setEdges]);

  useEffect(() => {
    refreshSnapshot();
  }, [refreshSnapshot]);

  const producerRate = useTopologyFlowStore((s) => s.producerRate);
  const consumerRate = useTopologyFlowStore((s) => s.consumerRate);

  useEffect(() => {
    const graph = buildFlowGraph({
      snapshot: topologySnapshot,
      producerRate,
      consumerRate,
    });
    setNodes(graph.nodes);
    setEdges(graph.edges);
  }, [topologySnapshot, producerRate, consumerRate, setNodes, setEdges]);

  const bumpPartitionCounter = useCallback(
    (partitionId) => {
      setNodes((current) =>
        current.map((node) => {
          if (node.id !== partitionId) {
            return node;
          }
          return {
            ...node,
            data: {
              ...node.data,
              logEndOffset: (node.data.logEndOffset || 0) + 1,
              messageCount: (node.data.messageCount || 0) + 1,
              rippleAt: Date.now(),
            },
          };
        }),
      );
    },
    [setNodes],
  );

  const shakeConsumer = useCallback(
    (consumerId) => {
      setNodes((current) =>
        current.map((node) => {
          if (node.id !== consumerId) {
            return node;
          }
          return {
            ...node,
            data: {
              ...node.data,
              shakeAt: Date.now(),
            },
          };
        }),
      );
    },
    [setNodes],
  );

  useLabRealtime((event) => {
    if (!event) {
      return;
    }

    if (event.type === TOPOLOGY_EVENT_TYPE.TOPOLOGY_SNAPSHOT) {
      setTopologySnapshot(toSnapshot(event.payload));
      return;
    }

    if (event.type === TOPOLOGY_EVENT_TYPE.PRODUCER_SEND) {
      markProducerSend(event.payload || event);
      return;
    }

    if (event.type === TOPOLOGY_EVENT_TYPE.CONSUMER_RECV) {
      markConsumerRecv(event.payload || event);
      return;
    }

    if (
      event.type === LAB_REALTIME_EVENT_TYPE.ENVIRONMENT_CHANGED ||
      event.type === LAB_REALTIME_EVENT_TYPE.TOPIC_CHANGED ||
      event.type === LAB_REALTIME_EVENT_TYPE.PRODUCER_CHANGED ||
      event.type === LAB_REALTIME_EVENT_TYPE.CONSUMER_CHANGED
    ) {
      refreshSnapshot();
    }
  });

  const canvasSize = useMemo(() => ({ width: 1600, height: 860 }), []);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) {
      return undefined;
    }

    const ctx = canvas.getContext("2d");

    const allocParticle = () => {
      const pool = particlesRef.current;
      for (let i = 0; i < pool.length; i += 1) {
        if (!pool[i].active) {
          return i;
        }
      }
      return -1;
    };

    const spawnFromEvent = () => {
      if (!canCreateParticle()) {
        consumeMatchedEvent();
        return;
      }

      const staged = consumeMatchedEvent();
      if (!staged) {
        return;
      }

      const poolIndex = allocParticle();
      if (poolIndex === -1) {
        return;
      }

      if (staged.stage === "SEND_ONLY" || staged.stage === "CHAINED") {
        const from = anchorFor(nodes, staged.fromId, "right");
        const to = anchorFor(nodes, staged.toPartitionGlobalId, "left");
        if (from && to) {
          particlesRef.current[poolIndex] = {
            ...buildParticle(from, to, "#35a1ff", 0.045),
            done: () => bumpPartitionCounter(staged.toPartitionGlobalId),
            next:
              staged.stage === "CHAINED"
                ? {
                    fromId:
                      staged.fromPartitionGlobalId ||
                      staged.toPartitionGlobalId,
                    toId: staged.toClientId,
                  }
                : null,
          };
          consumeBudget();
          return;
        }
      }

      if (staged.stage === "RECV_ONLY" || staged.stage === "CHAINED") {
        const from = anchorFor(
          nodes,
          staged.fromPartitionGlobalId || staged.toPartitionGlobalId,
          "right",
        );
        const to = anchorFor(nodes, staged.toClientId, "left");
        if (from && to) {
          particlesRef.current[poolIndex] = {
            ...buildParticle(from, to, "#ff9b2f", 0.038),
            done: () => shakeConsumer(staged.toClientId),
          };
          consumeBudget();
        }
      }
    };

    const frame = () => {
      ctx.clearRect(0, 0, canvas.width, canvas.height);

      spawnFromEvent();

      particlesRef.current.forEach((particle, index) => {
        if (!particle.active) {
          return;
        }

        particle.t += particle.speed;
        const point = pointOnCubic(
          particle.p0,
          particle.p1,
          particle.p2,
          particle.p3,
          Math.min(1, particle.t),
        );
        particle.trail.push(point);
        if (particle.trail.length > 10) {
          particle.trail.shift();
        }

        particle.trail.forEach((trailPoint, trailIndex) => {
          const alpha = (trailIndex + 1) / particle.trail.length;
          ctx.beginPath();
          ctx.fillStyle = `${particle.color}${Math.floor(alpha * 140)
            .toString(16)
            .padStart(2, "0")}`;
          ctx.arc(trailPoint.x, trailPoint.y, 2 + alpha * 2, 0, Math.PI * 2);
          ctx.fill();
        });

        ctx.beginPath();
        ctx.fillStyle = particle.color;
        ctx.shadowBlur = 16;
        ctx.shadowColor = particle.color;
        ctx.arc(point.x, point.y, 4, 0, Math.PI * 2);
        ctx.fill();
        ctx.shadowBlur = 0;

        if (particle.t >= 1) {
          particle.done?.();
          if (particle.next) {
            const from = anchorFor(nodes, particle.next.fromId, "right");
            const to = anchorFor(nodes, particle.next.toId, "left");
            if (from && to) {
              particlesRef.current[index] = {
                ...buildParticle(from, to, "#ff9b2f", 0.038),
                done: () => shakeConsumer(particle.next.toId),
              };
              return;
            }
          }
          particlesRef.current[index] = { active: false };
        }
      });

      rafRef.current = window.requestAnimationFrame(frame);
    };

    rafRef.current = window.requestAnimationFrame(frame);

    return () => {
      if (rafRef.current) {
        window.cancelAnimationFrame(rafRef.current);
      }
    };
  }, [
    nodes,
    canCreateParticle,
    consumeMatchedEvent,
    consumeBudget,
    bumpPartitionCounter,
    shakeConsumer,
  ]);

  return (
    <div className="topology-studio-page">
      <div className="topology-studio-head">
        <Space>
          <Typography.Title level={4} style={{ margin: 0, color: "#dce8ff" }}>
            Topology Studio
          </Typography.Title>
          {runningEnvironment && (
            <Tag color="blue">{runningEnvironment.name}</Tag>
          )}
        </Space>
        <Tag color="geekblue">Left → Right Data Flow</Tag>
      </div>

      {!ready && (
        <Alert
          type="warning"
          showIcon
          message="请先在 Environments 页面启动环境后再查看拓扑。"
          style={{ marginBottom: 12 }}
        />
      )}

      <div className="topology-canvas-wrap">
        <ReactFlow
          nodes={nodes}
          edges={edges}
          nodeTypes={topologyNodeTypes}
          onNodesChange={onNodesChange}
          onEdgesChange={onEdgesChange}
          fitView
          fitViewOptions={{ padding: 0.12 }}
          minZoom={0.5}
          maxZoom={1.5}
          proOptions={{ hideAttribution: true }}
          defaultViewport={{ x: 0, y: 0, zoom: 0.85 }}
        >
          <Background color="#2b3959" gap={24} size={1} />
          <MiniMap
            zoomable
            pannable
            style={{ background: "#0f172a", border: "1px solid #334155" }}
          />
          <Controls />
        </ReactFlow>
        <canvas
          ref={canvasRef}
          width={canvasSize.width}
          height={canvasSize.height}
          className="topology-particle-layer"
        />
      </div>
    </div>
  );
};

const TopologyStudioPage = () => (
  <ReactFlowProvider>
    <TopologyStudioInner />
  </ReactFlowProvider>
);

export default TopologyStudioPage;
