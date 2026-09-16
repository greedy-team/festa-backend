import { ENDPOINTS, SEARCH_CORPUS } from './config.js';

function metricTags(name) {
  const start = name.indexOf('{');
  return start === -1 ? '' : name.slice(start + 1, -1);
}

function tagSet(tags) {
  return new Set(tags ? tags.split(',') : []);
}

function isStrictTagSubset(candidate, other) {
  const candidateTags = tagSet(candidate);
  const otherTags = tagSet(other);
  return candidateTags.size < otherTags.size
    && [...candidateTags].every((tag) => otherTags.has(tag));
}

function metricSeries(metrics, metricName, endpoint) {
  return metricSeriesWithTags(metrics, metricName, [`endpoint:${endpoint}`]);
}

function exactMetricSeries(metrics, metricName, requiredTags) {
  const required = new Set(requiredTags);
  return Object.entries(metrics)
    .filter(([name]) => name.startsWith(`${metricName}{`))
    .map(([name, metric]) => ({ tags: metricTags(name), values: metric.values || {} }))
    .filter(({ tags }) => {
      const actual = tagSet(tags);
      return actual.size === required.size && [...required].every((tag) => actual.has(tag));
    });
}

function metricSeriesWithTags(metrics, metricName, requiredTags) {
  const series = Object.entries(metrics)
    .filter(([name]) => name.startsWith(`${metricName}{`)
      && requiredTags.every((tag) => tagSet(metricTags(name)).has(tag)))
    .map(([name, metric]) => ({ tags: metricTags(name), values: metric.values || {} }));
  // Threshold submetrics can include both {endpoint:x} and more-specific
  // {endpoint:x,other-tag:y} series. Keep only leaf series to avoid counting
  // one request in both its aggregate and its tagged child.
  return series.filter(({ tags }) => !series.some(({ tags: otherTags }) =>
    isStrictTagSubset(tags, otherTags)));
}

function sum(series, key) {
  return series.reduce((total, { values }) => total + (values[key] || 0), 0);
}

function weightedAverage(series) {
  const count = sum(series, 'count');
  if (count === 0) {
    return 0;
  }
  return series.reduce((total, { values }) => total + ((values.avg || 0) * (values.count || 0)), 0) / count;
}

function trend(series) {
  const singleSeries = series.length === 1 ? series[0].values : null;
  return {
    rps: sum(series, 'rate'),
    avg: weightedAverage(series),
    // k6 does not retain a histogram in handleSummary. A percentile across
    // multiple tag series cannot be reconstructed from each series percentile.
    p50: singleSeries ? (singleSeries['p(50)'] ?? singleSeries.med) : null,
    p95: singleSeries ? singleSeries['p(95)'] : null,
    p99: singleSeries ? singleSeries['p(99)'] : null,
    count: sum(series, 'count'),
    latencyPercentilesExact: singleSeries !== null,
  };
}

function requestSummary(series) {
  return { count: sum(series, 'count'), rps: sum(series, 'rate') };
}

export function buildEndpointSummary(metrics) {
  return Object.values(ENDPOINTS).map((endpoint) => {
    const endpointTags = [`endpoint:${endpoint}`];
    // k6 maintains an exact submetric for {endpoint:x} in addition to the
    // more-specific search tag series. Prefer it so endpoint percentiles are
    // preserved; leaf series remain necessary for search bucket/type summaries.
    const requestSeries = exactMetricSeries(metrics, 'http_reqs', endpointTags);
    const effectiveRequestSeries = requestSeries.length > 0
      ? requestSeries : metricSeries(metrics, 'http_reqs', endpoint);
    const durationSeries = exactMetricSeries(metrics, 'http_req_duration', endpointTags);
    const effectiveDurationSeries = durationSeries.length > 0
      ? durationSeries : metricSeries(metrics, 'http_req_duration', endpoint);
    const failureSeries = exactMetricSeries(metrics, 'http_req_failed', endpointTags);
    const checkFailureSeries = exactMetricSeries(metrics, 'response_check_failures', endpointTags);
    const requestCountsByTags = new Map(effectiveRequestSeries.map(({ tags, values }) => [tags, values.count || 0]));
    const failures = failureSeries.length > 0 ? failureSeries : metricSeries(metrics, 'http_req_failed', endpoint);
    const failedRequests = failures.reduce((total, { tags, values }) =>
      total + ((values.rate || 0) * [...requestCountsByTags]
        .filter(([requestTags]) => tagSet(tags).size === 0 || [...tagSet(tags)].every((tag) => tagSet(requestTags).has(tag)))
        .reduce((count, [, requestCount]) => count + requestCount, 0)), 0);
    const requestCount = sum(effectiveRequestSeries, 'count');

    return {
      endpoint,
      requests: requestSummary(effectiveRequestSeries),
      duration: trend(effectiveDurationSeries),
      httpErrorRate: requestCount === 0 ? 0 : failedRequests / requestCount,
      checkFailureCount: sum(checkFailureSeries.length > 0
        ? checkFailureSeries : metricSeries(metrics, 'response_check_failures', endpoint), 'count'),
    };
  });
}

export function buildSearchTagSummary(metrics) {
  const tagGroups = [...new Map(
    SEARCH_CORPUS.map((entry) => [`${entry.bucket}:${entry.type}`, entry]),
  ).values()];
  return tagGroups.map(({ bucket, type }) => {
    const tags = ['endpoint:search', `search_bucket:${bucket}`, `search_type:${type}`];
    return {
      searchBucket: bucket,
      searchType: type,
      requests: requestSummary(metricSeriesWithTags(metrics, 'http_reqs', tags)),
      duration: trend(metricSeriesWithTags(metrics, 'http_req_duration', tags)),
      checkFailureCount: sum(metricSeriesWithTags(metrics, 'response_check_failures', tags), 'count'),
    };
  });
}
