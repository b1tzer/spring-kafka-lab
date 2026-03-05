import { useCallback, useEffect, useMemo, useState } from 'react';
import { message } from 'antd';
import {
  createTopic,
  deleteManagedConsumer,
  deleteManagedProducer,
  deleteTopic,
  fetchManagedConsumers,
  fetchManagedProducers,
  fetchTopics,
  registerConsumer,
  registerProducer,
  sendByProducer,
  startManagedConsumer,
  stopManagedConsumer,
  updateManagedConsumerTopics,
  updateProducerTopics
} from '../api/kafkaLabApi';
import useLabRealtime from './useLabRealtime';
import { LAB_REALTIME_EVENT_TYPE } from '../constants/labDomain';

const MAX_LOGS = 120;

const normalizeLogEntry = (entry = {}) => ({
  id: entry.id || `${Date.now()}-${Math.random()}`,
  timestamp: entry.timestamp || Date.now(),
  time: entry.time || new Date().toLocaleTimeString(),
  level: entry.level || 'info',
  action: entry.action || 'EVENT',
  detail: entry.detail || '',
  topic: entry.topic || '',
  fields: entry.fields || {}
});

const useMessagingLab = ({ ready, nextName }) => {
  const [topics, setTopics] = useState([]);
  const [producers, setProducers] = useState([]);
  const [consumers, setConsumers] = useState([]);
  const [loadingTopics, setLoadingTopics] = useState(false);
  const [loadingProducers, setLoadingProducers] = useState(false);
  const [loadingConsumers, setLoadingConsumers] = useState(false);
  const [activityLogs, setActivityLogs] = useState([]);

  const topicNameSet = useMemo(() => new Set(topics.map((item) => item.name)), [topics]);
  const producerIdSet = useMemo(() => new Set(producers.map((item) => item.producerId)), [producers]);
  const consumerIdSet = useMemo(() => new Set(consumers.map((item) => item.clientId)), [consumers]);
  const consumerGroupSet = useMemo(() => new Set(consumers.map((item) => item.groupId)), [consumers]);

  const ensureReady = useCallback(() => {
    if (ready) {
      return true;
    }
    message.warning('请先在 Environments 页面创建并启动环境');
    return false;
  }, [ready]);

  const getErrorText = (error) => error?.response?.data?.message || error?.message || 'Unknown error';

  const loadTopics = useCallback(async () => {
    setLoadingTopics(true);
    try {
      const res = await fetchTopics();
      setTopics((res.data || []).map((item) => ({ key: item, name: item })));
    } finally {
      setLoadingTopics(false);
    }
  }, []);

  const loadProducers = useCallback(async () => {
    setLoadingProducers(true);
    try {
      const res = await fetchManagedProducers();
      setProducers((res.data || []).map((item) => ({ ...item, key: item.producerId })));
    } finally {
      setLoadingProducers(false);
    }
  }, []);

  const loadConsumers = useCallback(async () => {
    setLoadingConsumers(true);
    try {
      const res = await fetchManagedConsumers();
      setConsumers((res.data || []).map((item) => ({ ...item, key: item.clientId })));
    } finally {
      setLoadingConsumers(false);
    }
  }, []);

  useEffect(() => {
    if (ready) {
      loadTopics();
      loadProducers();
      loadConsumers();
      return;
    }
    setTopics([]);
    setProducers([]);
    setConsumers([]);
    setActivityLogs([]);
  }, [ready, loadTopics, loadProducers, loadConsumers]);

  useLabRealtime((event) => {
    if (!ready || !event) {
      return;
    }

    if (event.type === LAB_REALTIME_EVENT_TYPE.TOPIC_CHANGED) {
      loadTopics();
      return;
    }

    if (event.type === LAB_REALTIME_EVENT_TYPE.PRODUCER_CHANGED) {
      loadProducers();
      return;
    }

    if (
      event.type === LAB_REALTIME_EVENT_TYPE.CONSUMER_CHANGED
      || event.type === LAB_REALTIME_EVENT_TYPE.ENVIRONMENT_CHANGED
    ) {
      loadConsumers();
      return;
    }

    if (event.type === LAB_REALTIME_EVENT_TYPE.ACTIVITY_LOG) {
      const entry = normalizeLogEntry(event.data || {});
      setActivityLogs((prev) => {
        const exists = prev.some((item) => item.id === entry.id);
        if (exists) {
          return prev;
        }
        return [entry, ...prev].slice(0, MAX_LOGS);
      });
      return;
    }

    if (event.type === LAB_REALTIME_EVENT_TYPE.ACTIVITY_LOG_SNAPSHOT) {
      const snapshot = (event.data?.logs || []).map(normalizeLogEntry);
      setActivityLogs(snapshot.slice(0, MAX_LOGS));
    }
  });

  const createTopicByValues = useCallback(async (values) => {
    if (!ensureReady()) {
      return;
    }
    try {
      const topicName = nextName('topic', values.topicName, topicNameSet);
      await createTopic({
        topicName,
        partitionCount: values.partitionCount,
        replicationFactor: values.replicationFactor,
        configs: {}
      });
      message.success(`Topic created: ${topicName}`);
      await loadTopics();
    } catch (error) {
      message.error(`Topic create failed: ${getErrorText(error)}`);
    }
  }, [ensureReady, nextName, topicNameSet, loadTopics]);

  const deleteTopicByName = useCallback(async (topicName) => {
    if (!ensureReady()) {
      return;
    }
    try {
      await deleteTopic(topicName);
      message.success('Topic deleted');
      await loadTopics();
    } catch (error) {
      message.error(`Topic delete failed: ${getErrorText(error)}`);
    }
  }, [ensureReady, loadTopics]);

  const createProducerByValues = useCallback(async (values) => {
    if (!ensureReady()) {
      return;
    }
    try {
      const producerId = nextName('producer', values.producerId, producerIdSet);
      await registerProducer({ producerId, topics: values.topics });
      message.success(`Producer added: ${producerId}`);
      await loadProducers();
    } catch (error) {
      message.error(`Producer create failed: ${getErrorText(error)}`);
    }
  }, [ensureReady, nextName, producerIdSet, loadProducers]);

  const sendByProducerValues = useCallback(async (values, selectedTopic) => {
    if (!ensureReady()) {
      return;
    }
    if (!selectedTopic || !topicNameSet.has(selectedTopic)) {
      message.warning('请先在 Topics 中点击选择一个 Topic');
      return;
    }
    try {
      const payload = values.message?.trim() || 'hello kafka lab';
      await sendByProducer(values.producerId, {
        topic: selectedTopic,
        key: values.key,
        message: payload,
        partition: values.partition,
        delay: values.delay,
        count: values.count,
        transactional: values.transactional || false
      });
      message.success(`Message sent by ${values.producerId}`);
      await loadProducers();
    } catch (error) {
      message.error(`Message send failed: ${getErrorText(error)}`);
    }
  }, [ensureReady, topicNameSet, loadProducers]);

  const createConsumerByValues = useCallback(async (values) => {
    if (!ensureReady()) {
      return;
    }
    try {
      const groupId = nextName('consumerGroup', values.groupId, consumerGroupSet);
      const clientId = nextName('consumer', values.clientId, consumerIdSet);
      await registerConsumer({
        groupId,
        clientId,
        hostIp: values.hostIp || '127.0.0.1',
        topics: values.topics,
        autoCommit: true
      });
      message.success(`Consumer added: ${clientId}`);
      await loadConsumers();
    } catch (error) {
      message.error(`Consumer create failed: ${getErrorText(error)}`);
    }
  }, [ensureReady, nextName, consumerGroupSet, consumerIdSet, loadConsumers]);

  const startConsumerByClientId = useCallback(async (clientId, selectedTopic) => {
    if (!ensureReady()) {
      return;
    }
    const consumer = consumers.find((item) => item.clientId === clientId);
    if (!selectedTopic || !consumer || !(consumer.topics || []).includes(selectedTopic)) {
      message.warning('请选择该 Consumer 已订阅的 Topic 后再启动');
      return;
    }
    try {
      await startManagedConsumer(clientId);
      message.success(`Consumer started: ${clientId}`);
      await loadConsumers();
    } catch (error) {
      message.error(`Consumer start failed: ${getErrorText(error)}`);
    }
  }, [ensureReady, consumers, loadConsumers]);

  const stopConsumerByClientId = useCallback(async (clientId) => {
    if (!ensureReady()) {
      return;
    }
    try {
      await stopManagedConsumer(clientId);
      message.success(`Consumer stopped: ${clientId}`);
      await loadConsumers();
    } catch (error) {
      message.error(`Consumer stop failed: ${getErrorText(error)}`);
    }
  }, [ensureReady, loadConsumers]);

  const updateProducerTopicsById = useCallback(async (producerId, topics) => {
    if (!ensureReady()) {
      return;
    }
    try {
      await updateProducerTopics(producerId, topics);
      message.success(`Producer updated: ${producerId}`);
      await loadProducers();
    } catch (error) {
      message.error(`Producer update failed: ${getErrorText(error)}`);
    }
  }, [ensureReady, loadProducers]);

  const deleteProducerById = useCallback(async (producerId) => {
    if (!ensureReady()) {
      return;
    }
    try {
      await deleteManagedProducer(producerId);
      message.success(`Producer deleted: ${producerId}`);
      await loadProducers();
    } catch (error) {
      message.error(`Producer delete failed: ${getErrorText(error)}`);
    }
  }, [ensureReady, loadProducers]);

  const updateConsumerTopicsById = useCallback(async (clientId, topics) => {
    if (!ensureReady()) {
      return;
    }
    try {
      await updateManagedConsumerTopics(clientId, topics);
      message.success(`Consumer updated: ${clientId}`);
      await loadConsumers();
    } catch (error) {
      message.error(`Consumer update failed: ${getErrorText(error)}`);
    }
  }, [ensureReady, loadConsumers]);

  const deleteConsumerById = useCallback(async (clientId) => {
    if (!ensureReady()) {
      return;
    }
    try {
      await deleteManagedConsumer(clientId);
      message.success(`Consumer deleted: ${clientId}`);
      await loadConsumers();
    } catch (error) {
      message.error(`Consumer delete failed: ${getErrorText(error)}`);
    }
  }, [ensureReady, loadConsumers]);

  return {
    topics,
    producers,
    consumers,
    activityLogs,
    loadingTopics,
    loadingProducers,
    loadingConsumers,
    createTopicByValues,
    deleteTopicByName,
    createProducerByValues,
    sendByProducerValues,
    createConsumerByValues,
    startConsumerByClientId,
    stopConsumerByClientId,
    updateProducerTopicsById,
    deleteProducerById,
    updateConsumerTopicsById,
    deleteConsumerById
  };
};

export default useMessagingLab;
