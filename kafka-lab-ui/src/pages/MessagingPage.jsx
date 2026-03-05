import React, { useEffect, useMemo, useState } from 'react';
import { Alert, Col, Form, Row, Space, Typography } from 'antd';
import useRunningEnvironment from '../hooks/useRunningEnvironment';
import useIncrementalNameGenerator from '../hooks/useIncrementalNameGenerator';
import useMessagingLab from '../hooks/useMessagingLab';
import TopicsPanel from './messaging/TopicsPanel';
import ProducerPanel from './messaging/ProducerPanel';
import ConsumerPanel from './messaging/ConsumerPanel';
import TopicPickerBar from './messaging/TopicPickerBar';
import ActivityLogPanel from './messaging/ActivityLogPanel';

const MessagingPage = () => {
    const [selectedTopic, setSelectedTopic] = useState('');

    const [topicForm] = Form.useForm();
    const [producerForm] = Form.useForm();
    const [consumerForm] = Form.useForm();

    const { ready, runningEnvironment } = useRunningEnvironment();
    const { nextName } = useIncrementalNameGenerator();

    const {
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

    const onSendMessage = async () => {
        const values = await producerForm.validateFields();
        await sendProducerMessageByValues(values, selectedTopic);
    };

    const onStartConsumer = async () => {
        const values = await consumerForm.validateFields();
        await startConsumerByValues(values, selectedTopic);
    };

    const onStopConsumer = async (groupId) => stopConsumerByGroupId(groupId);

    return (
        <Space direction="vertical" style={{ width: '100%' }} size={12}>
            <Typography.Title level={3}>Topics / Producer / Consumer</Typography.Title>
            {!ready && <Alert type="warning" showIcon message="请先在 Environments 页面创建并启动一个 Kafka 环境，再进行操作" />}
            {ready && runningEnvironment && <Alert type="success" showIcon message={`当前环境：${runningEnvironment.name}`} />}

            <TopicsPanel
                form={topicForm}
                ready={ready}
                loading={loadingTopics}
                topics={topics}
                onCreate={onCreateTopic}
                onDelete={deleteTopicByName}
            />

            <TopicPickerBar
                topicNames={topicNames}
                selectedTopic={selectedTopic}
                onChange={setSelectedTopic}
                disabled={!ready}
            />

            <Row gutter={16}>
                <Col xs={24} lg={12}>
                    <ProducerPanel
                        form={producerForm}
                        ready={ready}
                        selectedTopic={selectedTopic}
                        onSend={onSendMessage}
                    />
                </Col>
                <Col xs={24} lg={12}>
                    <ConsumerPanel
                        form={consumerForm}
                        ready={ready}
                        loading={loadingGroups}
                        groups={groups}
                        selectedTopic={selectedTopic}
                        onStart={onStartConsumer}
                        onStop={onStopConsumer}
                    />
                </Col>
            </Row>

            <ActivityLogPanel logs={activityLogs} />
        </Space>
    );
};

export default MessagingPage;