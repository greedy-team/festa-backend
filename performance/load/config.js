import { check, fail } from 'k6';
import http from 'k6/http';
import { Counter } from 'k6/metrics';

export const FIXTURES = {
  performance: {
    description: 'Issue #154 fixture: Host 200 / Festival 20,000 / Artist 30,000 / ArtistAlias 45,000 / Lineup 300,000',
    ids: { festival: 1, artist: 1, host: 1 },
  },
  smoke: {
    description: 'Local Docker smoke seed; the canonical search strings match the Issue #154 fixture patterns.',
    ids: { festival: 900001, artist: 900001, host: 900001 },
  },
};

export const ENDPOINTS = {
  upcoming: 'festival_upcoming',
  recent: 'festival_recent',
  festivals: 'festival_list',
  festivalDetail: 'festival_detail',
  artists: 'artist_list',
  artistDetail: 'artist_detail',
  search: 'search',
  hostDetail: 'host_detail',
};

// Exactly 20 slots: common one-character 30%, common two-character 30%, rare 15%,
// whitespace-normalized 15%, English 10%.  The index is iteration-based, never random.
export const SEARCH_CORPUS = [
  { bucket: 'common_1', query: '김', type: 'ALL' },
  { bucket: 'common_1', query: '김', type: 'ARTIST' },
  { bucket: 'common_1', query: '축', type: 'ALL' },
  { bucket: 'common_1', query: '축', type: 'FESTIVAL' },
  { bucket: 'common_1', query: '성', type: 'ALL' },
  { bucket: 'common_1', query: '성', type: 'HOST' },

  { bucket: 'common_2', query: '김아', type: 'ALL' },
  { bucket: 'common_2', query: '김아', type: 'ARTIST' },
  { bucket: 'common_2', query: '축제', type: 'ALL' },
  { bucket: 'common_2', query: '축제', type: 'FESTIVAL' },
  { bucket: 'common_2', query: '성능', type: 'ALL' },
  { bucket: 'common_2', query: '성능', type: 'HOST' },

  { bucket: 'rare', query: '희귀 아티스트 30000', type: 'ARTIST' },
  { bucket: 'rare', query: '희귀 검색 축제 20000', type: 'FESTIVAL' },
  { bucket: 'rare', query: 'PERF171', type: 'HOST' },

  { bucket: 'space_normalized', query: '서울축제', type: 'ALL' },
  { bucket: 'space_normalized', query: '서울축제', type: 'FESTIVAL' },
  { bucket: 'space_normalized', query: '성능대학교', type: 'HOST' },

  { bucket: 'english', query: 'campus', type: 'ARTIST' },
  { bucket: 'english', query: 'spring', type: 'FESTIVAL' },
];

export const responseCheckFailures = new Counter('response_check_failures');

const forbiddenHosts = new Set(['api.every-festa.com', 'dev-api.every-festa.com']);

export function assertSafeTarget(url) {
  const authority = /^[a-z][a-z0-9+.-]*:\/\/([^/?#]+)/i.exec(url)?.[1];
  if (!authority) {
    fail(`BASE_URL must be an absolute URL: '${url}'`);
  }
  const hostPort = authority.includes('@') ? authority.slice(authority.lastIndexOf('@') + 1) : authority;
  const bracketedHost = /^\[([^\]]+)\]/.exec(hostPort)?.[1];
  const host = (bracketedHost || hostPort.split(':')[0]).toLowerCase().replace(/\.+$/, '');
  if (!host) {
    fail(`BASE_URL must be an absolute URL: '${url}'`);
  }
  if (forbiddenHosts.has(host)) {
    fail('Refusing to load test production or the shared development server. Use a dedicated local or temporary load-test stack.');
  }
}

export function requiredEnv(name) {
  const value = __ENV[name];
  if (!value) {
    fail(`${name} is required. Refusing to choose a target URL implicitly.`);
  }
  const normalized = value.replace(/\/$/, '');
  assertSafeTarget(normalized);
  return normalized;
}

export function selectedFixture() {
  const name = __ENV.FIXTURE || 'performance';
  const fixture = FIXTURES[name];
  if (!fixture) {
    fail(`Unknown FIXTURE '${name}'. Use one of: ${Object.keys(FIXTURES).join(', ')}`);
  }
  return { name, ...fixture };
}

export function searchCase(iteration) {
  const requestedBucket = __ENV.SEARCH_BUCKET;
  const requestedType = __ENV.SEARCH_TYPE;
  const candidates = SEARCH_CORPUS.filter((entry) =>
    (!requestedBucket || entry.bucket === requestedBucket)
    && (!requestedType || entry.type === requestedType));

  if (candidates.length === 0) {
    fail('SEARCH_BUCKET / SEARCH_TYPE selects no corpus entry.');
  }
  return candidates[iteration % candidates.length];
}

export function getJson(url, endpoint, extraTags = {}) {
  const tags = { endpoint, ...extraTags };
  const response = http.get(url, {
    tags,
    responseType: 'none',
  });
  const passed = check(response, {
    'status is 200': (res) => res.status === 200,
    'content type is JSON': (res) => (res.headers['Content-Type'] || '').includes('application/json'),
  }, tags);

  responseCheckFailures.add(passed ? 0 : 1, tags);
  return response;
}

export function apiUrl(baseUrl, path, query = {}) {
  const params = Object.entries(query)
    .filter(([, value]) => value !== undefined && value !== null && value !== '')
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`);
  return `${baseUrl}${path}${params.length === 0 ? '' : `?${params.join('&')}`}`;
}
