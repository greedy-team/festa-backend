import { check, fail } from 'k6';
import http from 'k6/http';
import { Counter } from 'k6/metrics';
import { assertSafeTarget as assertApprovedTarget, targetEnvironment } from './safety.js';

export const FIXTURES = {
  performance: {
    description: 'Issue #154 fixture: Host 200 / Festival 20,000 / Artist 30,000 / ArtistAlias 45,000 / Lineup 300,000',
    manifest: {
      festivalDetailIds: [1, 5001, 10001, 15001],
      artistDetailIds: [1, 10000, 20000, 30000],
      hostDetailIds: [1, 7, 100, 200],
      festivalPages: [0, 1, 100, 500],
      artistPages: [0, 1, 100, 500],
    },
  },
  smoke: {
    description: 'Local Docker smoke seed; the canonical search strings match the Issue #154 fixture patterns.',
    manifest: {
      festivalDetailIds: [900001], artistDetailIds: [900001], hostDetailIds: [900001],
      festivalPages: [0], artistPages: [0],
    },
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

function requiredNonEmptyArray(manifest, name) {
  if (!Array.isArray(manifest[name]) || manifest[name].length === 0) {
    fail(`A1 manifest '${name}' must be a non-empty array.`);
  }
  return manifest[name];
}

function a1Fixture() {
  if (!__ENV.A1_MANIFEST_FILE) {
    fail('A1_MANIFEST_FILE is required when FIXTURE=a1. Run through run.ps1 with -A1ManifestFile.');
  }
  let manifest;
  try {
    manifest = JSON.parse(open(__ENV.A1_MANIFEST_FILE));
  } catch (_) {
    fail('A1_MANIFEST_FILE must contain valid JSON.');
  }
  const normalized = {
    festivalDetailIds: requiredNonEmptyArray(manifest, 'festivalDetailIds'),
    artistDetailIds: requiredNonEmptyArray(manifest, 'artistDetailIds'),
    hostDetailIds: requiredNonEmptyArray(manifest, 'hostDetailIds'),
    festivalPages: requiredNonEmptyArray(manifest, 'festivalPages'),
    artistPages: requiredNonEmptyArray(manifest, 'artistPages'),
  };
  const searchCorpus = requiredNonEmptyArray(manifest, 'searchCorpus');
  if (!searchCorpus.every((entry) => entry && entry.bucket && entry.query && entry.type)) {
    fail('Every A1 manifest searchCorpus entry requires bucket, query, and type.');
  }
  return {
    name: 'a1',
    description: 'Team-maintained A1 production targets; never use the local performance fixture values.',
    manifest: normalized,
    searchCorpus,
  };
}

export const responseCheckFailures = new Counter('response_check_failures');

export function assertSafeTarget(url) {
  try {
    return assertApprovedTarget(url, __ENV.A1_PRODUCTION_LOAD_APPROVAL);
  } catch (error) {
    fail(error.message);
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

export { targetEnvironment };

export function selectedFixture() {
  const name = __ENV.FIXTURE || 'performance';
  if (name === 'a1') {
    return a1Fixture();
  }
  const fixture = FIXTURES[name];
  if (!fixture) {
    fail(`Unknown FIXTURE '${name}'. Use one of: ${Object.keys(FIXTURES).join(', ')}`);
  }
  return { name, ...fixture, searchCorpus: SEARCH_CORPUS };
}

export function searchCase(iteration, corpus = SEARCH_CORPUS) {
  const requestedBucket = __ENV.SEARCH_BUCKET;
  const requestedType = __ENV.SEARCH_TYPE;
  const candidates = corpus.filter((entry) =>
    (!requestedBucket || entry.bucket === requestedBucket)
    && (!requestedType || entry.type === requestedType));

  if (candidates.length === 0) {
    fail('SEARCH_BUCKET / SEARCH_TYPE selects no corpus entry.');
  }
  return candidates[iteration % candidates.length];
}

export function fixtureValue(values, iteration) {
  return values[iteration % values.length];
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
