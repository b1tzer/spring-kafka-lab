import { api, unwrap } from './client';

export const fetchDashboard = () => unwrap(api.get('/dashboard'));
export const fetchTopics = () => unwrap(api.get('/topics'));
export const createTopic = (payload) => unwrap(api.post('/topics', payload));
export const deleteTopic = (name) => unwrap(api.delete(`/topics/${name}`));

export const sendMessage = (payload) => unwrap(api.post('/producer/send', payload));

export const startConsumer = (payload) => unwrap(api.post('/consumer/start', payload));
export const stopConsumer = (payload) => unwrap(api.post('/consumer/stop', payload));
export const fetchConsumerGroups = () => unwrap(api.get('/consumer/groups'));

export const runScenario = (payload) => unwrap(api.post('/scenario/run', payload));

export const fetchCluster = () => unwrap(api.get('/cluster'));
export const fetchBrokers = () => unwrap(api.get('/brokers'));
export const fetchClusterTopology = () => unwrap(api.get('/cluster/topology'));

export const createEnvironment = (payload) => unwrap(api.post('/env/create', payload));
export const listEnvironments = () => unwrap(api.get('/env'));
export const getEnvironment = (id) => unwrap(api.get(`/env/${id}`));
export const startEnvironment = (id) => unwrap(api.post(`/env/${id}/start`));
export const stopEnvironment = (id) => unwrap(api.post(`/env/${id}/stop`));
export const deleteEnvironment = (id) => unwrap(api.delete(`/env/${id}`));
export const environmentStatus = (id) => unwrap(api.get(`/env/${id}/status`));
export const environmentLogs = (id, tail = 200) => unwrap(api.get(`/env/${id}/logs?tail=${tail}`));
