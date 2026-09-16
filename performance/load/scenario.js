import exec from 'k6/execution';
import { fail } from 'k6';
import {
  ENDPOINTS,
  SEARCH_CORPUS,
  apiUrl,
  getJson,
  requiredEnv,
  searchCase,
  selectedFixture,
} from './config.js';
export { handleSummary } from './handle-summary.js';

const baseUrl = requiredEnv('BASE_URL');
const fixture = selectedFixture();
const scenarioName = __ENV.SCENARIO || 'mixed';
const stage = __ENV.STAGE || __ENV.PROFILE || 'smoke';

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
  ...SEARCH_CORPUS.flatMap(({ bucket, type }) => {
    const tags = `endpoint:search,search_bucket:${bucket},search_type:${type}`;
    return [
      [`http_reqs{${tags}}`, ['count>=0']],
      [`http_req_duration{${tags}}`, ['p(99)<10000']],
      [`response_check_failures{${tags}}`, ['count>=0']],
    ];
  }),
]);

if (!stageDefaults[stage]) {
  fail(`Unknown STAGE '${stage}'. Use smoke, baseline, normal, stress, or saturation.`);
}

const configured = stageDefaults[stage];
const executableScenarioName = {
  'festival-detail': 'festival_detail',
  'artist-detail': 'artist_detail',
  'host-detail': 'host_detail',
}[scenarioName] || scenarioName;
const profile = {
  ...configured,
  exec: executableScenarioName,
  tags: { stage, scenario: scenarioName, fixture: fixture.name },
};

if (configured.executor === 'constant-arrival-rate') {
  profile.timeUnit = '1s';
  profile.rate = Number(__ENV.RATE || configured.rate);
  profile.duration = __ENV.DURATION || configured.duration;
  profile.preAllocatedVUs = Number(__ENV.PRE_ALLOCATED_VUS || configured.preAllocatedVUs);
  profile.maxVUs = Number(__ENV.MAX_VUS || configured.maxVUs);
}

export const options = {
  scenarios: { [scenarioName]: profile },
  discardResponseBodies: true,
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'count', 'p(90)', 'p(95)', 'p(99)'],
  // These are safety/contract guards, not a final TPS acceptance criterion.
  thresholds: {
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
    http_req_duration: ['p(99)<10000'],
    ...taggedMetricThresholds,
  },
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

function festivals() {
  getJson(apiUrl(baseUrl, '/api/festivals', { page: 0, size: 20, sort: 'LATEST' }), ENDPOINTS.festivals);
}

function festivalDetail() {
  const id = __ENV.FESTIVAL_ID || fixture.ids.festival;
  getJson(apiUrl(baseUrl, `/api/festivals/${id}`), ENDPOINTS.festivalDetail);
}

function artists() {
  getJson(apiUrl(baseUrl, '/api/artists', { page: 0, size: 20, sort: 'NAME' }), ENDPOINTS.artists);
}

function artistDetail() {
  const id = __ENV.ARTIST_ID || fixture.ids.artist;
  getJson(apiUrl(baseUrl, `/api/artists/${id}`), ENDPOINTS.artistDetail);
}

function search() {
  const item = searchCase(searchIteration(currentIteration()));
  getJson(apiUrl(baseUrl, '/api/search', { q: item.query, type: item.type }), ENDPOINTS.search, {
    search_bucket: item.bucket,
    search_type: item.type,
  });
}

function hostDetail() {
  const id = __ENV.HOST_ID || fixture.ids.host;
  getJson(apiUrl(baseUrl, `/api/hosts/${id}`), ENDPOINTS.hostDetail);
}

const mixedHandlers = { upcoming, recent, festivals, festivalDetail, artists, artistDetail, search };
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
const searchesPerCycle = mixedWeights.search;

function searchIteration(iteration) {
  const slot = iteration % mixedCycle.length;
  const completedCycles = Math.floor(iteration / mixedCycle.length);
  const searchesBeforeSlot = mixedCycle.slice(0, slot).filter((name) => name === 'search').length;
  return (completedCycles * searchesPerCycle) + searchesBeforeSlot;
}

export function mixed() {
  mixedHandlers[mixedCycle[currentIteration() % mixedCycle.length]]();
}

// k6 resolves named scenario executors from exported functions, so keep the names explicit.
export { upcoming, recent, festivals, festivalDetail as festival_detail, artists, artistDetail as artist_detail, search, hostDetail as host_detail };
