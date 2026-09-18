export const A1_PRODUCTION_LOAD_APPROVAL = 'A1_READ_ONLY_LOAD_TEST';
export const A1_ALLOWED_RATES = [1, 5, 10, 25, 50, 100, 150];
export const A1_STEP_MAX_DURATION_SECONDS = 180;
export const A1_DEPLOYMENT_EXPERIMENT_RATE = 50;
export const A1_DEPLOYMENT_EXPERIMENT_DURATION_SECONDS = 300;
export const A1_MANIFEST_STAGE = 'a1-manifest';
export const A1_HTTP_FAILURE_ABORT_DELAY = '120s';

function normalizedHost(url) {
  const authority = /^[a-z][a-z0-9+.-]*:\/\/([^/?#]+)/i.exec(url)?.[1];
  if (!authority) {
    throw new Error(`BASE_URL must be an absolute URL: '${url}'`);
  }
  const hostPort = authority.includes('@') ? authority.slice(authority.lastIndexOf('@') + 1) : authority;
  const bracketedHost = /^\[([^\]]+)\]/.exec(hostPort)?.[1];
  const host = (bracketedHost || hostPort.split(':')[0]).toLowerCase().replace(/\.+$/, '');
  if (!host) {
    throw new Error(`BASE_URL must be an absolute URL: '${url}'`);
  }
  return host;
}

export function targetEnvironment(url) {
  const host = normalizedHost(url);
  if (host === 'api.every-festa.com') return 'a1-production';
  if (host === 'dev-api.every-festa.com') return 'shared-development';
  return 'non-production';
}

export function isA1ProductionTarget(url) {
  return targetEnvironment(url) === 'a1-production';
}

export function assertSafeTarget(url, approval = '') {
  const environment = targetEnvironment(url);
  if (environment === 'shared-development') {
    throw new Error('Refusing to load test the shared development server.');
  }
  if (environment === 'a1-production' && approval !== A1_PRODUCTION_LOAD_APPROVAL) {
    throw new Error('Refusing to load test production without A1_READ_ONLY_LOAD_TEST approval.');
  }
  return environment;
}

function durationSeconds(value) {
  const match = /^(\d+)([sm])$/.exec(value || '');
  if (!match || Number(match[1]) <= 0) {
    throw new Error('A1 production load duration must use a positive whole-second or whole-minute value.');
  }
  return Number(match[1]) * (match[2] === 'm' ? 60 : 1);
}

export function a1MixedVUs(rate) {
  return { preAllocatedVUs: rate, maxVUs: rate * 10 };
}

export function assertA1ProductionProfile({ scenario, stage, profile, fixture, rate, duration, preAllocatedVUs, maxVUs }) {
  if (scenario === 'fixture-manifest') {
    if (stage !== A1_MANIFEST_STAGE || fixture !== 'a1') {
      throw new Error('A1 manifest validation requires the a1-manifest stage and the A1-only manifest.');
    }
    if (rate || duration || preAllocatedVUs > 1 || maxVUs > 1) {
      throw new Error('A1 manifest validation is fixed at one VU with no configurable rate or duration.');
    }
    return;
  }
  if (scenario !== 'mixed' || fixture !== 'a1' || !['step', 'deployment-experiment'].includes(profile)) {
    throw new Error('A1 production load tests allow only mixed with the separately validated A1 manifest.');
  }
  if (stage !== 'baseline') {
    throw new Error('A1 production load tests must use the baseline stage.');
  }
  const seconds = durationSeconds(duration);
  if (profile === 'step') {
    if (!A1_ALLOWED_RATES.includes(rate)) {
      throw new Error(`A1 production load rate must be one of ${A1_ALLOWED_RATES.join(', ')} RPS.`);
    }
    if (seconds > A1_STEP_MAX_DURATION_SECONDS) {
      throw new Error(`A1 step duration must not exceed ${A1_STEP_MAX_DURATION_SECONDS} seconds.`);
    }
  } else if (rate !== A1_DEPLOYMENT_EXPERIMENT_RATE || seconds !== A1_DEPLOYMENT_EXPERIMENT_DURATION_SECONDS) {
    throw new Error('A1 deployment experiment requires exactly 50 RPS for 5 minutes.');
  }
  const expectedVUs = a1MixedVUs(rate);
  if (preAllocatedVUs !== expectedVUs.preAllocatedVUs || maxVUs !== expectedVUs.maxVUs) {
    throw new Error(`A1 ${profile} VUs must be preAllocated=${expectedVUs.preAllocatedVUs}, max=${expectedVUs.maxVUs}.`);
  }
}

export function a1HttpFailureAbortThreshold() {
  return { threshold: 'rate<0.01', abortOnFail: true, delayAbortEval: A1_HTTP_FAILURE_ABORT_DELAY };
}

export function assertA1ManifestValidationReceipt(value, baseUrl) {
  if (value !== 'validated') {
    throw new Error('A1 mixed load requires a successful matching A1 manifest validation receipt.');
  }
}
