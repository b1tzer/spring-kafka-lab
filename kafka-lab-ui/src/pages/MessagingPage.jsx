import React, { useEffect, useMemo, useState } from 'react';
import { Alert, Col, Form, Row, Space, Typography } from 'antd';
import useRunningEnvironment from '../hooks/useRunningEnvironment';
import useIncrementalNameGenerator from '../hooks/useIncrementalNameGenerator';
import useMessagingLab from '../hooks/useMessagingLab';
import TopicsPanel from './messaging/TopicsPanel';
import ProducerPanel from './messaging/ProducerPanel';
import ConsumerPanel from './messaging/ConsumerPanel';
import ActivityLogPanel from './messaging/ActivityLogPanel';

const MessagingPage = () => {
    const [selectedTopic, setSelectedTopic] = useState('');

    const [topicForm] = Form.useForm();
    const [producerCreateForm] = Form.useForm();
    const [producerSendForm] = Form.useForm();
    const [consumerForm] = Form.useForm();

    const { ready, runningEnvironment } = useRunningEnvironment();
    const { nextName } = useIncrementalNameGenerator();

    const {
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
    } = useMessagingLab({ ready, nextName });

    const topicNames = useMemo(() => topics.map((item) => item.name), [topics]);

    useEffect(() => {
        if (!selectedTopic) {
            return;
        }
        if (!topicNames.includes(selectedTopic)) {
            setSelectedTopic('');
        }
    }, [topicNames, selectedTopic]);

    const onCreateTopic = async () => {
        const values = await topicForm.validateFields();
        await createTopicByValues(values);
    };

    const onCreateProducer = async () => {
        const values = await producerCreateForm.validateFields();
        await createProducerByValues(values);
    };

    const onSendMessage = async () => {
        const values = await producerSendForm.validateFields();
        await sendByProducerValues(values, selectedTopic);
    };

    const onCreateConsumer = async () => {
        const values = await consumerForm.validateFields();
        await createConsumerByValues(values);
    };

    const onStartConsumer = async (clientId) => startConsumerByClientId(clientId, selectedTopic);

    const onStopConsumer = async (clientId) => stopConsumerByClientId(clientId);

    return (
        <Space direction="vertical" style={{ width: '100%' }} size={12}>
            <Typography.Title level={3}>Messaging Console</Typography.Title>
            {!ready && <Alert type="warning" showIcon message="请先在 Environments 页面创建并启动一个 Kafka 环境，再进行操作" />}
            {ready && runningEnvironment && <Alert type="success" showIcon message={`当前环境：${runningEnvironment.name}`} />}

            <TopicsPanel
                form={topicForm}
                ready={ready}
                loading={loadingTopics}
                topics={topics}
                selectedTopic={selectedTopic}
                onCreate={onCreateTopic}
                onDelete={deleteTopicByName}
                onSelectTopic={setSelectedTopic}
            />

            <Row gutter={16}>
                <Col xs={24} lg={12}>
                    <ProducerPanel
                        createForm={producerCreateForm}
                        sendForm={producerSendForm}
                        ready={ready}
                        selectedTopic={selectedTopic}
                        topicNames={topicNames}
                        producers={producers}
                        loading={loadingProducers}
                        onCreate={onCreateProducer}
                        onSend={onSendMessage}
                        onUpdateTopics={updateProducerTopicsById}
                        onDelete={deleteProducerById}
                    />
                </Col>
                <Col xs={24} lg={12}>
                    <ConsumerPanel
                        form={consumerForm}
                        ready={ready}
                        loading={loadingConsumers}
                        topicNames={topicNames}
                        consumers={consumers}
                        selectedTopic={selectedTopic}
                        onCreate={onCreateConsumer}
                        onStart={onStartConsumer}
                        onStop={onStopConsumer}
                        onUpdateTopics={updateConsumerTopicsById}
                        onDelete={deleteConsumerById}
                    />
                </Col>
            </Row>

            <ActivityLogPanel logs={activityLogs} />
        </Space>
    );
};

export default MessagingPage;