import exec from 'k6/execution';
import { check, fail, sleep } from 'k6';
import http from 'k6/http';
import {
  ENDPOINTS,
  apiUrl,
  fixtureValue,
  getJson,
  requiredEnv,
  searchCase,
  selectedFixture,
} from './config.js';
import {
  A1_MANIFEST_STAGE,
  a1HttpFailureAbortThreshold,
  assertA1ManifestValidationReceipt,
  assertA1ProductionProfile,
  isA1ProductionTarget,
} from './safety.js';
export { handleSummary } from './handle-summary.js';

const baseUrl = requiredEnv('BASE_URL');
const fixture = selectedFixture();
const searchCorpus = fixture.searchCorpus;
const scenarioName = __ENV.SCENARIO || 'mixed';
const stage = __ENV.STAGE || __ENV.PROFILE || 'smoke';
const a1Profile = __ENV.A1_PROFILE || '';

const stageDefaults = {
  smoke: { executor: 'shared-iterations', vus: 1, iterations: 100, maxDuration: '2m' },
  baseline: { executor: 'constant-arrival-rate', rate: 5, duration: '3m', preAllocatedVUs: 2, maxVUs: 10 },
  normal: { executor: 'constant-arrival-rate', rate: 15, duration: '5m', preAllocatedVUs: 5, maxVUs: 25 },
  stress: { executor: 'constant-arrival-rate', rate: 30, duration: '5m', preAllocatedVUs: 10, maxVUs: 50 },
  saturation: { executor: 'constant-arrival-rate', rate: 50, duration: '5m', preAllocatedVUs: 15, maxVUs: 80 },
};

const taggedMetricThresholds = Object.fromEntries([
  ...Object.values(ENDPOINTS).flatMap((endpoint) => [
    [`http_reqs{endpoint:${endpoint}}`, ['count>=0']],
    [`http_req_duration{endpoint:${endpoint}}`, ['p(99)<10000']],
    [`http_req_failed{endpoint:${endpoint}}`, ['rate<0.01']],
    [`response_check_failures{endpoint:${endpoint}}`, ['count>=0']],
  ]),
  ...searchCorpus.flatMap(({ bucket, type }) => {
    const tags = `endpoint:search,search_bucket:${bucket},search_type:${type}`;
    return [
      [`http_reqs{${tags}}`, ['count>=0']],
      [`http_req_duration{${tags}}`, ['p(99)<10000']],
      [`response_check_failures{${tags}}`, ['count>=0']],
    ];
  }),
]);

if (!stageDefaults[stage] && stage !== A1_MANIFEST_STAGE) {
  fail(`Unknown STAGE '${stage}'. Use smoke, baseline, normal, stress, or saturation.`);
}

const configured = scenarioName === 'fixture-manifest'
  ? { executor: 'shared-iterations', vus: 1, iterations: 1, maxDuration: '2m' }
  : stageDefaults[stage];
const executableScenarioName = {
  'festival-detail': 'festival_detail',
  'artist-detail': 'artist_detail',
  'host-detail': 'host_detail',
  'fixture-manifest': 'fixture_manifest',
}[scenarioName] || scenarioName;
const profile = {
  ...configured,
  exec: executableScenarioName,
  tags: { stage, scenario: scenarioName, fixture: fixture.name, a1_profile: a1Profile },
};

if (configured.executor === 'constant-arrival-rate') {
  profile.timeUnit = '1s';
  profile.rate = Number(__ENV.RATE || configured.rate);
  profile.duration = __ENV.DURATION || configured.duration;
  profile.preAllocatedVUs = Number(__ENV.PRE_ALLOCATED_VUS || configured.preAllocatedVUs);
  profile.maxVUs = Number(__ENV.MAX_VUS || configured.maxVUs);
}

if (isA1ProductionTarget(baseUrl)) {
  assertA1ProductionProfile({
    scenario: scenarioName,
    stage,
    profile: a1Profile,
    fixture: fixture.name,
    rate: Number(__ENV.RATE),
    duration: __ENV.DURATION,
    preAllocatedVUs: Number(__ENV.PRE_ALLOCATED_VUS || configured.preAllocatedVUs),
    maxVUs: Number(__ENV.MAX_VUS || configured.maxVUs),
  });
  if (scenarioName === 'mixed') {
    assertA1ManifestValidationReceipt(__ENV.A1_MANIFEST_VALIDATION_RECEIPT, baseUrl);
  }
}

const thresholds = {
  http_req_failed: ['rate<0.01'],
  checks: ['rate>0.99'],
  http_req_duration: ['p(99)<10000'],
  ...taggedMetricThresholds,
};

if (isA1ProductionTarget(baseUrl) && scenarioName === 'mixed') {
  // At the permitted 1 RPS floor, 120 seconds produces enough observations
  // that one transient failure remains below 1%, while sustained failures abort.
  thresholds.http_req_failed = [a1HttpFailureAbortThreshold()];
}

export const options = {
  scenarios: { [scenarioName]: profile },
  discardResponseBodies: scenarioName !== 'fixture-manifest',
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'count', 'p(90)', 'p(95)', 'p(99)'],
  // These are safety/contract guards, not a final TPS acceptance criterion.
  thresholds,
};

function currentIteration() {
  return exec.scenario.iterationInTest;
}

function upcoming() {
  getJson(apiUrl(baseUrl, '/api/festivals/upcoming', { limit: 10 }), ENDPOINTS.upcoming);
}

function recent() {
  getJson(apiUrl(baseUrl, '/api/festivals/recent', { limit: 10 }), ENDPOINTS.recent);
}

function festivals(iteration = currentIteration()) {
  getJson(apiUrl(baseUrl, '/api/festivals', {
    page: __ENV.FESTIVAL_PAGE || fixtureValue(fixture.manifest.festivalPages, iteration),
    size: 20,
    sort: 'LATEST',
    hostId: __ENV.FESTIVAL_HOST_ID,
    q: __ENV.FESTIVAL_QUERY,
  }), ENDPOINTS.festivals);
}

function festivalDetail(iteration = currentIteration()) {
  const id = __ENV.FESTIVAL_ID || fixtureValue(fixture.manifest.festivalDetailIds, iteration);
  getJson(apiUrl(baseUrl, `/api/festivals/${id}`), ENDPOINTS.festivalDetail);
}

function artists(iteration = currentIteration()) {
  const page = __ENV.ARTIST_PAGE || fixtureValue(fixture.manifest.artistPages, iteration);
  getJson(apiUrl(baseUrl, '/api/artists', { page, size: 20, sort: 'NAME' }), ENDPOINTS.artists);
}

function artistDetail(iteration = currentIteration()) {
  const id = __ENV.ARTIST_ID || fixtureValue(fixture.manifest.artistDetailIds, iteration);
  getJson(apiUrl(baseUrl, `/api/artists/${id}`), ENDPOINTS.artistDetail);
}

function search(searchRequestOrdinal = currentIteration()) {
  const item = searchCase(searchRequestOrdinal, searchCorpus);
  getJson(apiUrl(baseUrl, '/api/search', { q: item.query, type: item.type }), ENDPOINTS.search, {
    search_bucket: item.bucket,
    search_type: item.type,
  });
}

function hostDetail() {
  const id = __ENV.HOST_ID || fixtureValue(fixture.manifest.hostDetailIds, currentIteration());
  getJson(apiUrl(baseUrl, `/api/hosts/${id}`), ENDPOINTS.hostDetail);
}

function fixtureManifest() {
  const requests = [
    ...fixture.manifest.festivalDetailIds.map((id) => ({ url: apiUrl(baseUrl, `/api/festivals/${id}`), label: `festival-detail-${id}` })),
    ...fixture.manifest.artistDetailIds.map((id) => ({ url: apiUrl(baseUrl, `/api/artists/${id}`), label: `artist-detail-${id}` })),
    ...fixture.manifest.hostDetailIds.map((id) => ({ url: apiUrl(baseUrl, `/api/hosts/${id}`), label: `host-detail-${id}` })),
    ...fixture.manifest.festivalPages.map((page) => ({ url: apiUrl(baseUrl, '/api/festivals', { page, size: 20, sort: 'LATEST' }), label: `festival-page-${page}` })),
    ...fixture.manifest.artistPages.map((page) => ({ url: apiUrl(baseUrl, '/api/artists', { page, size: 20, sort: 'NAME' }), label: `artist-page-${page}` })),
  ];
  requests.forEach(({ url, label }) => {
    const response = http.get(url, { tags: { endpoint: 'fixture_manifest', manifest_target: label } });
    const body = response.status === 200 ? JSON.parse(response.body) : null;
    const isPage = label.includes('-page-');
    const pageItems = body?.items ?? body?.content;
    const hasResult = !isPage || (Array.isArray(pageItems) && pageItems.length > 0);
    check(response, { 'fixture manifest target returns a result': (res) => res.status === 200 && hasResult }, { endpoint: 'fixture_manifest', manifest_target: label });
    if (isA1ProductionTarget(baseUrl)) sleep(1);
  });
  searchCorpus.forEach(({ query, type, bucket }) => {
    const response = http.get(apiUrl(baseUrl, '/api/search', { q: query, type }), {
      tags: { endpoint: 'fixture_manifest', manifest_target: `search-${bucket}-${type}` },
    });
    const body = response.status === 200 ? JSON.parse(response.body) : null;
    const count = body?.counts?.[type.toLowerCase()] ?? 0;
    if (count <= 0) {
      console.error(`fixture search corpus empty: ${bucket}/${type}/${query}`);
    }
    check(response, { 'fixture search corpus returns a result': () => count > 0 }, {
      endpoint: 'fixture_manifest', manifest_target: `search-${bucket}-${type}`,
    });
    if (isA1ProductionTarget(baseUrl)) sleep(1);
  });
}

const mixedHandlers = { upcoming, recent, festivals, festivalDetail, artists, artistDetail };
const mixedWeights = {
  upcoming: 12,
  recent: 10,
  festivals: 18,
  festivalDetail: 12,
  artists: 18,
  artistDetail: 10,
  search: 20,
};

// Smooth weighted round-robin keeps the fixed 100-slot ratio while interleaving endpoints.
function smoothWeightedCycle(weights) {
  const current = Object.fromEntries(Object.keys(weights).map((name) => [name, 0]));
  const total = Object.values(weights).reduce((sum, weight) => sum + weight, 0);
  return Array.from({ length: total }, () => {
    Object.keys(weights).forEach((name) => { current[name] += weights[name]; });
    const selected = Object.keys(weights).reduce((best, name) =>
      current[name] > current[best] ? name : best, Object.keys(weights)[0]);
    current[selected] -= total;
    return selected;
  });
}

const mixedCycle = smoothWeightedCycle(mixedWeights);
const mixedRequestCounts = Object.fromEntries(Object.keys(mixedWeights).map((name) => [name, 0]));
const mixedRequestOrdinals = Object.fromEntries(Object.keys(mixedWeights).map((name) => [name, []]));
mixedCycle.forEach((name, slot) => {
  mixedRequestOrdinals[name][slot] = mixedRequestCounts[name];
  mixedRequestCounts[name] += 1;
});

function mixedRequestIteration(name, iteration) {
  const slot = iteration % mixedCycle.length;
  const completedCycles = Math.floor(iteration / mixedCycle.length);
  return (completedCycles * mixedRequestCounts[name]) + mixedRequestOrdinals[name][slot];
}

export function mixed() {
  const iteration = currentIteration();
  const slot = iteration % mixedCycle.length;
  const scenario = mixedCycle[slot];
  const requestIteration = mixedRequestIteration(scenario, iteration);
  if (scenario === 'search') return search(requestIteration);
  mixedHandlers[scenario](requestIteration);
}

// k6 resolves named scenario executors from exported functions, so keep the names explicit.
export {
  upcoming, recent, festivals, festivalDetail as festival_detail, artists, artistDetail as artist_detail,
  search, hostDetail as host_detail, fixtureManifest as fixture_manifest,
};
