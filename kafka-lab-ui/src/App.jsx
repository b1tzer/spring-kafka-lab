import React, { useMemo, useState } from 'react';
import { Layout, Menu, Typography, Button } from 'antd';
import { LinkOutlined } from '@ant-design/icons';
import DashboardPage from './pages/DashboardPage';
import MessagingPage from './pages/MessagingPage';
import ScenariosPage from './pages/ScenariosPage';
import ClusterPage from './pages/ClusterPage';
import LogsPage from './pages/LogsPage';
import EnvironmentPage from './pages/EnvironmentPage';
import useRunningEnvironment from './hooks/useRunningEnvironment';

const { Header, Sider, Content } = Layout;
const { Title } = Typography;

const menus = [
  { key: 'dashboard', label: 'Dashboard' },
  { key: 'messaging', label: 'Topics / Producer / Consumer' },
  { key: 'scenarios', label: 'Scenarios' },
  { key: 'environments', label: 'Environments' },
  { key: 'cluster', label: 'Cluster' },
  { key: 'logs', label: 'Logs' }
];

const App = () => {
  const [active, setActive] = useState('dashboard');
  const { runningEnvironment } = useRunningEnvironment();
  const kafkaUiPort = runningEnvironment?.kafkaUiPort || 18085;
  const kafkaUiUrl = `http://localhost:${kafkaUiPort}`;
  const kafkaUiReady = Boolean(runningEnvironment?.kafkaUiEnabled);

  const body = useMemo(() => {
    switch (active) {
      case 'messaging':
        return <MessagingPage />;
      case 'scenarios':
        return <ScenariosPage />;
      case 'environments':
        return <EnvironmentPage />;
      case 'cluster':
        return <ClusterPage />;
      case 'logs':
        return <LogsPage />;
      case 'dashboard':
      default:
        return <DashboardPage />;
    }
  }, [active]);

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider>
        <div style={{ color: '#fff', padding: 16, fontWeight: 600 }}>Kafka Lab</div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[active]}
          items={menus}
          onClick={({ key }) => setActive(key)}
        />
      </Sider>
      <Layout>
        <Header style={{ background: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <Title level={4} style={{ margin: 0 }}>Kafka Playground for Backend Engineers</Title>
          <Button
            type="primary"
            icon={<LinkOutlined />}
            href={kafkaUiUrl}
            target="_blank"
            rel="noreferrer"
            disabled={!kafkaUiReady}
          >
            Kafka UI
          </Button>
        </Header>
        <Content style={{ margin: 16 }}>{body}</Content>
      </Layout>
    </Layout>
  );
};

export default App;
