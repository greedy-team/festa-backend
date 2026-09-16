import exec from 'k6/execution';
import { buildEndpointSummary, buildSearchTagSummary } from './summary.js';

export const options = { vus: 1, iterations: 1 };

function metric(values) {
  return { values };
}

function endpoint(summary, name) {
  return summary.find((item) => item.endpoint === name);
}

function assert(condition, message) {
  if (!condition) {
    exec.test.abort(message);
  }
}

export default function () {
  const metrics = {
    'http_reqs{endpoint:search}': metric({ count: 9, rate: 3 }),
    'http_reqs{endpoint:search,search_bucket:common_1,search_type:ALL}': metric({ count: 6, rate: 2 }),
    'http_reqs{endpoint:search,search_bucket:rare,search_type:ARTIST}': metric({ count: 3, rate: 1 }),
    'http_req_duration{endpoint:search}': metric({ count: 9, rate: 3, avg: 40, med: 10, 'p(95)': 100, 'p(99)': 110 }),
    'http_req_duration{endpoint:search,search_bucket:common_1,search_type:ALL}': metric({ count: 6, rate: 2, avg: 10, med: 9, 'p(95)': 15, 'p(99)': 16 }),
    'http_req_duration{endpoint:search,search_bucket:rare,search_type:ARTIST}': metric({ count: 3, rate: 1, avg: 100, med: 90, 'p(95)': 150, 'p(99)': 160 }),
    'http_req_failed{endpoint:search}': metric({ rate: 1 / 9 }),
    'response_check_failures{endpoint:search}': metric({ count: 1 }),
    'response_check_failures{endpoint:search,search_bucket:common_1,search_type:ALL}': metric({ count: 0 }),
    'response_check_failures{endpoint:search,search_bucket:rare,search_type:ARTIST}': metric({ count: 1 }),
    'http_reqs{endpoint:festival_upcoming}': metric({ count: 12, rate: 4 }),
    'http_req_duration{endpoint:festival_upcoming}': metric({ count: 12, rate: 4, avg: 7, med: 6, 'p(95)': 8, 'p(99)': 9 }),
    'http_req_failed{endpoint:festival_upcoming}': metric({ rate: 0 }),
    'response_check_failures{endpoint:festival_upcoming}': metric({ count: 0 }),
  };

  const summary = buildEndpointSummary(metrics);
  const search = endpoint(summary, 'search');
  const upcoming = endpoint(summary, 'festival_upcoming');

  assert(search.requests.count === 9, 'search request count must retain the exact endpoint aggregate');
  assert(search.requests.rps === 3, 'search RPS must retain the exact endpoint aggregate');
  assert(Object.keys(search.requests).length === 2, 'request summaries must contain only count and RPS');
  assert(search.httpErrorRate === 1 / 9, 'search error rate must be weighted by request count');
  assert(search.checkFailureCount === 1, 'search check failures must retain the exact endpoint aggregate');
  assert(search.duration.avg === 40, 'search average latency must retain the exact endpoint aggregate');
  assert(search.duration.p50 === 10 && search.duration.p95 === 100 && search.duration.p99 === 110
    && search.duration.latencyPercentilesExact,
  'endpoint aggregate percentiles must retain the exact k6 submetric');
  assert(upcoming.requests.count === 12 && upcoming.duration.p95 === 8
    && upcoming.duration.latencyPercentilesExact,
  'a single-series endpoint must keep its exact latency summary');

  const searchTags = buildSearchTagSummary({
    ...metrics,
    'http_reqs{endpoint:search,search_bucket:common_10,search_type:ALL}': metric({ count: 99, rate: 33 }),
  });
  const commonOne = searchTags.find((item) => item.searchBucket === 'common_1' && item.searchType === 'ALL');
  assert(commonOne.requests.count === 6 && commonOne.requests.rps === 2,
    'exact tag matching must not include similarly named buckets');
}
