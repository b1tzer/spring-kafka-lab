import { useRef } from "react";

const defaultPrefixByKind = {
  topic: "topic",
  producerTopic: "producer-topic",
  consumerGroup: "consumer-group",
  consumerTopic: "consumer-topic",
};

const useIncrementalNameGenerator = () => {
  const lastPrefixRef = useRef({});
  const suffixCounterRef = useRef({});

  const nextName = (kind, manualPrefix, existingSet = new Set()) => {
    const typedPrefix = manualPrefix?.trim();
    const rememberedPrefix = lastPrefixRef.current[kind];
    const basePrefix =
      typedPrefix || rememberedPrefix || defaultPrefixByKind[kind] || kind;
    lastPrefixRef.current[kind] = basePrefix;

    const scopedKey = `${kind}:${basePrefix}`;
    let suffix = suffixCounterRef.current[scopedKey] ?? 0;
    let candidate = suffix === 0 ? basePrefix : `${basePrefix}-${suffix}`;

    while (existingSet.has(candidate)) {
      suffix += 1;
      candidate = `${basePrefix}-${suffix}`;
    }

    suffixCounterRef.current[scopedKey] = suffix + 1;
    return candidate;
  };

  return { nextName };
};

export default useIncrementalNameGenerator;
