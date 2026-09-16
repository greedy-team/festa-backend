import { buildEndpointSummary, buildSearchTagSummary } from './summary.js';

function value(metrics, name, key) {
  return metrics[name]?.values?.[key];
}

function failedThresholds(metrics) {
  return Object.entries(metrics).flatMap(([metric, details]) =>
    Object.entries(details.thresholds || {})
      .filter(([, threshold]) => threshold.ok === false)
      .map(([threshold]) => ({ metric, threshold })));
}

export function handleSummary(data) {
  const artifact = {
    schemaVersion: 1,
    run: {
      runId: __ENV.RUN_ID || 'manual',
      stage: __ENV.STAGE || __ENV.PROFILE || 'smoke',
      scenario: __ENV.SCENARIO || 'mixed',
      fixture: __ENV.FIXTURE || 'performance',
      baseUrl: __ENV.BASE_URL,
      gitSha: __ENV.GIT_SHA || null,
      imageSha: __ENV.IMAGE_SHA || null,
      generatedAt: new Date().toISOString(),
    },
    overall: {
      requestsPerSecond: value(data.metrics, 'http_reqs', 'rate'),
      p50: value(data.metrics, 'http_req_duration', 'med'),
      p95: value(data.metrics, 'http_req_duration', 'p(95)'),
      p99: value(data.metrics, 'http_req_duration', 'p(99)'),
      httpErrorRate: value(data.metrics, 'http_req_failed', 'rate'),
      checksRate: value(data.metrics, 'checks', 'rate'),
      maxVUs: value(data.metrics, 'vus_max', 'max'),
    },
    endpoints: buildEndpointSummary(data.metrics),
    searchTags: buildSearchTagSummary(data.metrics),
    failedThresholds: failedThresholds(data.metrics),
  };

  const destination = __ENV.RESULTS_DIR;
  const lines = [
    `run=${artifact.run.runId} stage=${artifact.run.stage} scenario=${artifact.run.scenario}`,
    `http_reqs=${artifact.overall.requestsPerSecond} p95=${artifact.overall.p95} p99=${artifact.overall.p99} error_rate=${artifact.overall.httpErrorRate}`,
    `failed_thresholds=${artifact.failedThresholds.length}`,
  ];
  const output = { stdout: `${lines.join('\n')}\n` };
  if (destination) {
    output[`${destination.replace(/\/$/, '')}/k6-summary.json`] = JSON.stringify(artifact, null, 2);
  }
  return output;
}
