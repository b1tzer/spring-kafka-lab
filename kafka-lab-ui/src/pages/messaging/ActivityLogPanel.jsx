import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Button, Card, Empty, Input, Segmented, Select, Space, Typography } from 'antd';
import { ArrowDownOutlined, ArrowUpOutlined } from '@ant-design/icons';
import '../../styles/activityLogEditor.css';

const toKafkaLevel = (level) => {
  if (level === 'error') {
    return 'ERROR';
  }
  if (level === 'warn' || level === 'warning') {
    return 'WARN';
  }
  if (level === 'success' || level === 'info') {
    return 'INFO';
  }
  return 'DEBUG';
};

const pad = (value, len = 2) => String(value).padStart(len, '0');

const formatTimestamp = (item) => {
  const raw = item.timestamp || item.time;
  const date = raw ? new Date(raw) : new Date();
  if (Number.isNaN(date.getTime())) {
    return String(raw || '');
  }
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())},${pad(date.getMilliseconds(), 3)}`;
};

const sourceByAction = (action = '') => {
  if (action.includes('PRODUCE')) {
    return 'kafka.producer';
  }
  if (action.includes('CONSUME')) {
    return 'kafka.consumer';
  }
  if (action.includes('/')) {
    return 'kafka.api';
  }
  return 'kafka.lab';
};

const formatLine = (item) => {
  const fields = item.fields || {};
  const attrs = Object.entries(fields)
    .filter(([, value]) => value !== undefined && value !== null && value !== '')
    .map(([key, value]) => `${key}=${String(value)}`)
    .join(' ');

  const timestamp = formatTimestamp(item);
  const level = toKafkaLevel(item.level);
  const logger = sourceByAction(item.action);
  const action = item.action || 'EVENT';
  const detail = item.detail || '';
  const topic = item.topic ? ` topic=${item.topic}` : '';
  const context = attrs ? `${attrs}${topic}` : topic.trim();

  return `${timestamp} ${level.padEnd(5, ' ')} [${logger}] [${action}]${context ? ` [${context}]` : ''} - ${detail}`;
};

const escapeRegExp = (value) => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

const highlightText = (text, query, active) => {
  if (!query) {
    return text;
  }
  const regex = new RegExp(`(${escapeRegExp(query)})`, 'ig');
  const parts = text.split(regex);
  return parts.map((part, index) => {
    if (part.toLowerCase() !== query.toLowerCase()) {
      return <React.Fragment key={`${part}-${index}`}>{part}</React.Fragment>;
    }
    return <mark key={`${part}-${index}`} className={active ? 'log-mark active' : 'log-mark'}>{part}</mark>;
  });
};

const ActivityLogPanel = ({ logs }) => {
  const [topicFilter, setTopicFilter] = useState('ALL');
  const [sortOrder, setSortOrder] = useState('desc');
  const [query, setQuery] = useState('');
  const [activeMatch, setActiveMatch] = useState(0);
  const lineRefs = useRef({});

  const topicOptions = useMemo(() => {
    const topics = Array.from(new Set(logs.map((item) => item.topic).filter(Boolean)));
    return [
      { label: 'All Topics', value: 'ALL' },
      ...topics.map((topic) => ({ label: topic, value: topic }))
    ];
  }, [logs]);

  const displayLogs = useMemo(() => {
    const filtered = topicFilter === 'ALL'
      ? logs
      : logs.filter((item) => item.topic === topicFilter || item.fields?.topic === topicFilter);

    const copy = [...filtered];
    copy.sort((a, b) => {
      const ta = a.timestamp || 0;
      const tb = b.timestamp || 0;
      return sortOrder === 'asc' ? ta - tb : tb - ta;
    });
    return copy;
  }, [logs, topicFilter, sortOrder]);

  const lines = useMemo(() => displayLogs.map((item) => ({
    id: item.id,
    text: formatLine(item)
  })), [displayLogs]);

  const matches = useMemo(() => {
    if (!query.trim()) {
      return [];
    }
    const q = query.toLowerCase();
    return lines
      .map((line, index) => ({ index, line }))
      .filter((item) => item.line.text.toLowerCase().includes(q));
  }, [lines, query]);

  useEffect(() => {
    if (matches.length === 0) {
      setActiveMatch(0);
      return;
    }
    if (activeMatch >= matches.length) {
      setActiveMatch(matches.length - 1);
    }
  }, [matches, activeMatch]);

  useEffect(() => {
    if (matches.length === 0) {
      return;
    }
    const current = matches[activeMatch];
    if (!current) {
      return;
    }
    const ref = lineRefs.current[current.line.id];
    if (ref && typeof ref.scrollIntoView === 'function') {
      ref.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }
  }, [activeMatch, matches]);

  const gotoNext = () => {
    if (matches.length === 0) {
      return;
    }
    setActiveMatch((prev) => (prev + 1) % matches.length);
  };

  const gotoPrev = () => {
    if (matches.length === 0) {
      return;
    }
    setActiveMatch((prev) => (prev - 1 + matches.length) % matches.length);
  };

  return (
    <Card title="Activity Logs" bordered={false} size="small">
      <Space wrap style={{ marginBottom: 8, width: '100%', justifyContent: 'space-between' }}>
        <Space wrap>
          <Select
            value={topicFilter}
            options={topicOptions}
            style={{ width: 220 }}
            onChange={setTopicFilter}
          />
          <Segmented
            value={sortOrder}
            onChange={setSortOrder}
            options={[
              { label: 'Newest First', value: 'desc' },
              { label: 'Oldest First', value: 'asc' }
            ]}
          />
        </Space>

        <Space>
          <Input
            allowClear
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            onPressEnter={gotoNext}
            placeholder="Find in logs"
            style={{ width: 220 }}
          />
          <Button icon={<ArrowUpOutlined />} onClick={gotoPrev} disabled={matches.length === 0} />
          <Button icon={<ArrowDownOutlined />} onClick={gotoNext} disabled={matches.length === 0} />
          <Typography.Text type="secondary">{matches.length === 0 ? '0/0' : `${activeMatch + 1}/${matches.length}`}</Typography.Text>
        </Space>
      </Space>

      {lines.length === 0 ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无操作日志" />
      ) : (
        <div className="activity-log-editor" role="log" aria-label="Kafka activity logs">
          {lines.map((line, index) => {
            const matchedIndex = matches.findIndex((item) => item.line.id === line.id);
            const isActiveMatch = matchedIndex === activeMatch && matchedIndex !== -1;

            return (
              <div
                key={line.id || `${line.text}-${index}`}
                ref={(node) => {
                  if (node) {
                    lineRefs.current[line.id] = node;
                  }
                }}
                className={`log-line ${isActiveMatch ? 'active-match-line' : ''}`}
              >
                <span className="log-line-number">{index + 1}</span>
                <span className="log-line-text">{highlightText(line.text, query.trim(), isActiveMatch)}</span>
              </div>
            );
          })}
        </div>
      )}
    </Card>
  );
};

export default ActivityLogPanel;