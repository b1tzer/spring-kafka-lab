import { useEffect, useRef } from 'react';

const RECONNECT_DELAY_MS = 1500;

const normalizeWsUrl = (rawUrl) => {
  if (!rawUrl) {
    return '';
  }

  const url = String(rawUrl).trim();
  if (!url) {
    return '';
  }

  if (/^https?:\/\//i.test(url)) {
    const converted = url.replace(/^http/i, 'ws');
    try {
      new URL(converted);
      return converted;
    } catch (_error) {
      return '';
    }
  }

  if (/^ws:\/\//i.test(url) || /^wss:\/\//i.test(url)) {
    try {
      new URL(url);
      return url;
    } catch (_error) {
      return '';
    }
  }

  if (/^wss?:/i.test(url)) {
    const secure = /^wss:/i.test(url);
    const converted = `${secure ? 'wss' : 'ws'}://${url.replace(/^wss?:\/*/i, '')}`;
    try {
      new URL(converted);
      return converted;
    } catch (_error) {
      return '';
    }
  }

  if (url.startsWith('/')) {
    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
    const converted = `${protocol}://${window.location.host}${url}`;
    try {
      new URL(converted);
      return converted;
    } catch (_error) {
      return '';
    }
  }

  try {
    new URL(url);
    return url;
  } catch (_error) {
    return '';
  }
};

const buildWsCandidates = () => {
  const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
  const envWsUrl = import.meta.env.VITE_LAB_WS_URL;
  const envApiBase = import.meta.env.VITE_API_BASE_URL;
  const candidates = [];

  if (envWsUrl) {
    const normalizedEnvWsUrl = normalizeWsUrl(envWsUrl);
    if (normalizedEnvWsUrl) {
      candidates.push(normalizedEnvWsUrl);
    }
  }

  if (envApiBase && /^https?:\/\//i.test(envApiBase)) {
    const wsBase = envApiBase.replace(/^http/i, 'ws').replace(/\/$/, '');
    candidates.push(`${wsBase}/ws/events`);
  }

  candidates.push(`${protocol}://${window.location.host}/api/ws/events`);
  candidates.push(`${protocol}://${window.location.host}/ws/events`);

  if (window.location.port && window.location.port !== '8080') {
    candidates.push(`${protocol}://${window.location.hostname}:8080/ws/events`);
  }

  return Array.from(new Set(candidates));
};

const useLabRealtime = (onEvent) => {
  const onEventRef = useRef(onEvent);

  useEffect(() => {
    onEventRef.current = onEvent;
  }, [onEvent]);

  useEffect(() => {
    let ws;
    let reconnectTimer;
    let closedByUser = false;
    const wsCandidates = buildWsCandidates();
    let candidateIndex = 0;

    const connect = () => {
      const url = wsCandidates[candidateIndex % wsCandidates.length];
      candidateIndex += 1;
      ws = new WebSocket(url);
      let opened = false;

      ws.onopen = () => {
        opened = true;
      };

      ws.onmessage = (event) => {
        try {
          const payload = JSON.parse(event.data);
          onEventRef.current?.(payload);
        } catch (_error) {
        }
      };

      ws.onclose = () => {
        if (closedByUser) {
          return;
        }

        if (!opened && candidateIndex < wsCandidates.length) {
          connect();
          return;
        }

        reconnectTimer = window.setTimeout(connect, RECONNECT_DELAY_MS);
      };

      ws.onerror = () => {
      };
    };

    connect();

    return () => {
      closedByUser = true;
      if (reconnectTimer) {
        window.clearTimeout(reconnectTimer);
      }
      if (ws) {
        if (ws.readyState === WebSocket.OPEN) {
          ws.close();
        } else if (ws.readyState === WebSocket.CONNECTING) {
          ws.onopen = () => ws.close();
        }
      };
    };
  }, []);
};

export default useLabRealtime;
