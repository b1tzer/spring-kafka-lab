import { create } from "zustand";

const PARTICLE_BUDGET_PER_SECOND = 60;
const MAX_PENDING = 2000;

const trimQueue = (queue) => {
  if (queue.length <= MAX_PENDING) {
    return queue;
  }
  return queue.slice(queue.length - MAX_PENDING);
};

const nowSecond = () => Math.floor(Date.now() / 1000);

const useTopologyFlowStore = create((set, get) => ({
  topologySnapshot: null,
  pendingSends: [],
  pendingRecvs: [],
  sendByMsgId: {},
  activeRipples: {},
  consumerShake: {},
  producerRate: {},
  consumerRate: {},
  frameBudget: {
    second: nowSecond(),
    created: 0,
  },

  setTopologySnapshot: (snapshot) => set({ topologySnapshot: snapshot }),

  markProducerSend: (event) =>
    set((state) => {
      const second = nowSecond();
      const bucket = state.producerRate[event.fromId] || { second, count: 0 };
      const nextBucket =
        bucket.second === second
          ? { second, count: bucket.count + 1 }
          : { second, count: 1 };

      const nextQueue = trimQueue([
        ...state.pendingSends,
        {
          id: event.msgId,
          fromId: event.fromId,
          toPartitionGlobalId: event.toPartitionGlobalId,
          topic: event.topic,
          ts: Date.now(),
        },
      ]);

      return {
        pendingSends: nextQueue,
        sendByMsgId: {
          ...state.sendByMsgId,
          [event.msgId]: event,
        },
        producerRate: {
          ...state.producerRate,
          [event.fromId]: nextBucket,
        },
      };
    }),

  markConsumerRecv: (event) =>
    set((state) => {
      const second = nowSecond();
      const bucket = state.consumerRate[event.toClientId] || {
        second,
        count: 0,
      };
      const nextBucket =
        bucket.second === second
          ? { second, count: bucket.count + 1 }
          : { second, count: 1 };

      const nextQueue = trimQueue([
        ...state.pendingRecvs,
        {
          id: event.msgId,
          toGroupId: event.toGroupId,
          toClientId: event.toClientId,
          fromPartitionGlobalId: event.fromPartitionGlobalId,
          ts: Date.now(),
        },
      ]);

      return {
        pendingRecvs: nextQueue,
        consumerRate: {
          ...state.consumerRate,
          [event.toClientId]: nextBucket,
        },
      };
    }),

  consumeMatchedEvent: () => {
    const state = get();
    if (state.pendingSends.length === 0 && state.pendingRecvs.length === 0) {
      return null;
    }

    const recv = state.pendingRecvs[0];
    if (recv) {
      const send = state.sendByMsgId[recv.id];
      set((current) => ({
        pendingRecvs: current.pendingRecvs.slice(1),
        sendByMsgId: send
          ? Object.fromEntries(
              Object.entries(current.sendByMsgId).filter(
                ([msgId]) => msgId !== recv.id,
              ),
            )
          : current.sendByMsgId,
      }));
      if (send) {
        return {
          msgId: recv.id,
          stage: "CHAINED",
          fromId: send.fromId,
          toPartitionGlobalId: send.toPartitionGlobalId,
          toClientId: recv.toClientId,
          toGroupId: recv.toGroupId,
          fromPartitionGlobalId: recv.fromPartitionGlobalId,
        };
      }

      return {
        msgId: recv.id,
        stage: "RECV_ONLY",
        fromPartitionGlobalId: recv.fromPartitionGlobalId,
        toClientId: recv.toClientId,
        toGroupId: recv.toGroupId,
      };
    }

    const send = state.pendingSends[0];
    if (!send) {
      return null;
    }

    set((current) => ({
      pendingSends: current.pendingSends.slice(1),
    }));

    return {
      msgId: send.id,
      stage: "SEND_ONLY",
      fromId: send.fromId,
      toPartitionGlobalId: send.toPartitionGlobalId,
    };
  },

  canCreateParticle: () => {
    const budget = get().frameBudget;
    const second = nowSecond();
    if (budget.second !== second) {
      set({
        frameBudget: {
          second,
          created: 0,
        },
      });
      return true;
    }
    return budget.created < PARTICLE_BUDGET_PER_SECOND;
  },

  consumeBudget: () =>
    set((state) => ({
      frameBudget: {
        ...state.frameBudget,
        created: state.frameBudget.created + 1,
      },
    })),

  triggerRipple: (partitionId) =>
    set((state) => ({
      activeRipples: {
        ...state.activeRipples,
        [partitionId]: Date.now(),
      },
    })),

  triggerConsumerShake: (consumerId) =>
    set((state) => ({
      consumerShake: {
        ...state.consumerShake,
        [consumerId]: Date.now(),
      },
    })),
}));

export default useTopologyFlowStore;
