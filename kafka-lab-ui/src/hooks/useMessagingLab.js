import { useCallback, useEffect, useMemo, useState } from 'react';
import { message } from 'antd';
import {
    createTopic,
    deleteTopic,
    fetchConsumerGroups,
    fetchTopics,
    sendMessage,
    startConsumer,
    stopConsumer
} from '../api/kafkaLabApi';
import useLabRealtime from './useLabRealtime';
import { LAB_REALTIME_EVENT_TYPE } from '../constants/labDomain';

const useMessagingLab = ({ ready, nextName }) => {
    const [topics, setTopics] = useState([]);
    const [groups, setGroups] = useState([]);
    const [loadingTopics, setLoadingTopics] = useState(false);
    const [loadingGroups, setLoadingGroups] = useState(false);
    const [activityLogs, setActivityLogs] = useState([]);

    const topicNameSet = useMemo(() => new Set(topics.map((item) => item.name)), [topics]);
    const groupIdSet = useMemo(() => new Set(groups.map((item) => item.groupId)), [groups]);

    const ensureReady = useCallback(() => {
        if (ready) {
            return true;
        }
        message.warning('请先在 Environments 页面创建并启动环境');
        return false;
    }, [ready]);

    const pushLog = useCallback((level, action, detail, fields = {}) => {
        setActivityLogs((prev) => {
            const next = [{
                id: `${Date.now()}-${Math.random()}`,
                time: new Date().toLocaleTimeString(),
                level,
                action,
                detail,
                fields
            }, ...prev];
            return next.slice(0, 80);
        });
    }, []);

    const getErrorText = (error) => {
        return error?.response?.data?.message || error?.message || 'Unknown error';
    };

    const loadTopics = useCallback(async () => {
        setLoadingTopics(true);
        try {
            const res = await fetchTopics();
            setTopics((res.data || []).map((item) => ({ key: item, name: item })));
        } finally {
            setLoadingTopics(false);
        }
    }, []);

    const loadGroups = useCallback(async () => {
        setLoadingGroups(true);
        try {
            const res = await fetchConsumerGroups();
            setGroups((res.data?.groups || []).map((group) => ({ ...group, key: group.groupId })));
        } finally {
            setLoadingGroups(false);
        }
    }, []);

    useEffect(() => {
        if (ready) {
            loadTopics();
            loadGroups();
            return;
        }
        setTopics([]);
        setGroups([]);
    }, [ready, loadGroups, loadTopics]);

    useLabRealtime((event) => {
        if (!ready) {
            return;
        }
        if (event?.type === LAB_REALTIME_EVENT_TYPE.TOPIC_CHANGED) {
            loadTopics();
            return;
        }
        if (event?.type === LAB_REALTIME_EVENT_TYPE.CONSUMER_CHANGED
            || event?.type === LAB_REALTIME_EVENT_TYPE.ENVIRONMENT_CHANGED) {
            loadGroups();
            return;
        }
        if (event?.type === LAB_REALTIME_EVENT_TYPE.PRODUCER_CHANGED) {
            loadTopics();
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
            pushLog('success', 'TOPIC_CREATE', topicName, {
                topic: topicName,
                partitions: values.partitionCount,
                replicationFactor: values.replicationFactor
            });
            await loadTopics();
        } catch (error) {
            const errorText = getErrorText(error);
            message.error(`Topic create failed: ${errorText}`);
            pushLog('error', 'TOPIC_CREATE', errorText, {
                topic: values.topicName || 'auto',
                partitions: values.partitionCount,
                replicationFactor: values.replicationFactor
            });
        }
    }, [ensureReady, nextName, topicNameSet, loadTopics, pushLog]);

    const deleteTopicByName = useCallback(async (topicName) => {
        if (!ensureReady()) {
            return;
        }
        try {
            await deleteTopic(topicName);
            message.success('Topic deleted');
            pushLog('success', 'TOPIC_DELETE', topicName, { topic: topicName });
            await loadTopics();
        } catch (error) {
            const errorText = getErrorText(error);
            message.error(`Topic delete failed: ${errorText}`);
            pushLog('error', 'TOPIC_DELETE', errorText, { topic: topicName });
        }
    }, [ensureReady, loadTopics, pushLog]);

    const sendProducerMessageByValues = useCallback(async (values, selectedTopic) => {
        if (!ensureReady()) {
            return;
        }
        if (!selectedTopic || !topicNameSet.has(selectedTopic)) {
            message.warning('请选择一个已创建的 Topic');
            return;
        }
        try {
            const requestMessage = values.message?.trim() || 'hello kafka lab';
            const res = await sendMessage({
                topic: selectedTopic,
                key: values.key,
                message: requestMessage,
                partition: values.partition,
                delay: values.delay,
                count: values.count,
                transactional: values.transactional || false
            });
            const sentCount = res?.data?.count ?? values.count ?? 1;
            message.success(`Message sent to: ${selectedTopic}`);
            pushLog('success', 'PRODUCE', 'Message sent', {
                topic: selectedTopic,
                key: values.key || 'auto',
                count: sentCount,
                partition: values.partition ?? 'auto',
                delayMs: values.delay ?? 0,
                transactional: values.transactional ? 'true' : 'false',
                payload: requestMessage.length > 60 ? `${requestMessage.slice(0, 60)}...` : requestMessage
            });
        } catch (error) {
            const errorText = getErrorText(error);
            message.error(`Message send failed: ${errorText}`);
            pushLog('error', 'PRODUCE', 'Message send failed', {
                topic: selectedTopic,
                key: values.key || 'auto',
                count: values.count ?? 1,
                partition: values.partition ?? 'auto',
                error: errorText
            });
        }
    }, [ensureReady, topicNameSet, pushLog]);

    const startConsumerByValues = useCallback(async (values, selectedTopic) => {
        if (!ensureReady()) {
            return;
        }
        if (!selectedTopic || !topicNameSet.has(selectedTopic)) {
            message.warning('请选择一个已创建的 Topic');
            return;
        }
        try {
            const groupId = nextName('consumerGroup', values.groupId, groupIdSet);
            await startConsumer({
                groupId,
                topic: selectedTopic,
                concurrency: values.concurrency,
                autoCommit: values.autoCommit
            });
            message.success(`Consumer started: ${groupId} -> ${selectedTopic}`);
            pushLog('success', 'CONSUME_START', 'Consumer started', {
                topic: selectedTopic,
                groupId,
                concurrency: values.concurrency ?? 1,
                autoCommit: values.autoCommit === false ? 'false' : 'true'
            });
            await loadGroups();
        } catch (error) {
            const errorText = getErrorText(error);
            message.error(`Consumer start failed: ${errorText}`);
            pushLog('error', 'CONSUME_START', 'Consumer start failed', {
                topic: selectedTopic,
                groupId: values.groupId || 'auto',
                concurrency: values.concurrency ?? 1,
                error: errorText
            });
        }
    }, [ensureReady, nextName, groupIdSet, topicNameSet, loadGroups, pushLog]);

    const stopConsumerByGroupId = useCallback(async (groupId) => {
        if (!ensureReady()) {
            return;
        }
        try {
            await stopConsumer({ groupId });
            message.success('Consumer stopped');
            pushLog('success', 'CONSUME_STOP', 'Consumer stopped', { groupId });
            await loadGroups();
        } catch (error) {
            const errorText = getErrorText(error);
            message.error(`Consumer stop failed: ${errorText}`);
            pushLog('error', 'CONSUME_STOP', 'Consumer stop failed', { groupId, error: errorText });
        }
    }, [ensureReady, loadGroups, pushLog]);

    return {
        topics,
        groups,
        activityLogs,
        loadingTopics,
        loadingGroups,
        createTopicByValues,
        deleteTopicByName,
        sendProducerMessageByValues,
        startConsumerByValues,
        stopConsumerByGroupId
    };
};

export default useMessagingLab;