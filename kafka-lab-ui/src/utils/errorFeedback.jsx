import React from 'react';
import { Button, Space, Typography, message, notification } from 'antd';

export const extractErrorReason = (error, fallback = 'Unknown error') => {
  if (typeof error === 'string' && error.trim()) {
    return error;
  }

  const response = error?.response?.data;
  const output = response?.data?.output;
  if (typeof output === 'string' && output.trim()) {
    return output;
  }

  const apiMessage = response?.message;
  if (typeof apiMessage === 'string' && apiMessage.trim()) {
    return apiMessage;
  }

  const errorMessage = error?.message;
  if (typeof errorMessage === 'string' && errorMessage.trim()) {
    return errorMessage;
  }

  return fallback;
};

export const extractFailureReason = (response, fallback = 'Operation failed') => {
  const output = response?.data?.output;
  if (typeof output === 'string' && output.trim()) {
    return output;
  }

  const responseMessage = response?.message;
  if (typeof responseMessage === 'string' && responseMessage.trim()) {
    return responseMessage;
  }

  return fallback;
};

const copyText = async (text) => {
  if (!text) {
    return;
  }

  try {
    if (navigator?.clipboard?.writeText) {
      await navigator.clipboard.writeText(text);
      message.success('报错原因已复制');
      return;
    }

    const textarea = document.createElement('textarea');
    textarea.value = text;
    textarea.style.position = 'fixed';
    textarea.style.left = '-9999px';
    document.body.appendChild(textarea);
    textarea.focus();
    textarea.select();
    document.execCommand('copy');
    document.body.removeChild(textarea);
    message.success('报错原因已复制');
  } catch (err) {
    message.error('复制失败，请手动复制');
  }
};

export const showErrorWithCopy = (title, reason) => {
  notification.error({
    message: title,
    duration: 8,
    description: (
      <Space direction="vertical" size={8} style={{ width: '100%' }}>
        <Typography.Text style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
          {reason}
        </Typography.Text>
        <Button size="small" onClick={() => copyText(reason)}>
          复制报错原因
        </Button>
      </Space>
    )
  });
};

export const showApiError = (title, error, fallback = 'Unknown error') => {
  const reason = extractErrorReason(error, fallback);
  showErrorWithCopy(title, reason);
};
