import { useCallback, useEffect, useState } from 'react';
import { listEnvironments } from '../api/kafkaLabApi';
import useLabRealtime from './useLabRealtime';
import { ENVIRONMENT_STATUS, LAB_REALTIME_EVENT_TYPE } from '../constants/labDomain';

const useRunningEnvironment = () => {
  const [loading, setLoading] = useState(true);
  const [ready, setReady] = useState(false);
  const [runningEnvironment, setRunningEnvironment] = useState(null);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      const res = await listEnvironments();
      const running = (res.data || []).find((item) => item.status === ENVIRONMENT_STATUS.RUNNING);
      setRunningEnvironment(running || null);
      setReady(Boolean(running));
    } catch (error) {
      setRunningEnvironment(null);
      setReady(false);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  useLabRealtime((event) => {
    if (event?.type === LAB_REALTIME_EVENT_TYPE.ENVIRONMENT_CHANGED) {
      refresh();
    }
  });

  return { loading, ready, runningEnvironment, refresh };
};

export default useRunningEnvironment;
