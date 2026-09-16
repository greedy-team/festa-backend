import { ENDPOINTS, SEARCH_CORPUS } from './config.js';

function metricTags(name) {
  const start = name.indexOf('{');
  return start === -1 ? '' : name.slice(start + 1, -1);
}

function hasEndpoint(name, endpoint) {
  return metricTags(name).split(',').includes(`endpoint:${endpoint}`);
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
  const series = Object.entries(metrics)
    .filter(([name]) => name.startsWith(`${metricName}{`) && hasEndpoint(name, endpoint))
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

function metricWithTags(metrics, metricName, tags) {
  const key = Object.keys(metrics).find((name) =>
    name.startsWith(metricName) && tags.every((tag) => name.includes(tag)));
  return key ? metrics[key]?.values : undefined;
}

export function buildEndpointSummary(metrics) {
  return Object.values(ENDPOINTS).map((endpoint) => {
    const requests = metricSeries(metrics, 'http_reqs', endpoint);
    const requestCountsByTags = new Map(requests.map(({ tags, values }) => [tags, values.count || 0]));
    const failures = metricSeries(metrics, 'http_req_failed', endpoint);
    const failedRequests = failures.reduce((total, { tags, values }) =>
      total + ((values.rate || 0) * [...requestCountsByTags]
        .filter(([requestTags]) => tagSet(tags).size === 0 || [...tagSet(tags)].every((tag) => tagSet(requestTags).has(tag)))
        .reduce((count, [, requestCount]) => count + requestCount, 0)), 0);
    const requestCount = sum(requests, 'count');

    return {
      endpoint,
      requests: trend(requests),
      duration: trend(metricSeries(metrics, 'http_req_duration', endpoint)),
      httpErrorRate: requestCount === 0 ? 0 : failedRequests / requestCount,
      checkFailureCount: sum(metricSeries(metrics, 'response_check_failures', endpoint), 'count'),
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
      requests: trend(metricWithTags(metrics, 'http_reqs', tags) ? [{ values: metricWithTags(metrics, 'http_reqs', tags) }] : []),
      duration: trend(metricWithTags(metrics, 'http_req_duration', tags) ? [{ values: metricWithTags(metrics, 'http_req_duration', tags) }] : []),
      checkFailureCount: metricWithTags(metrics, 'response_check_failures', tags)?.count,
    };
  });
}
