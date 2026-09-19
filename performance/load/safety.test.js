import exec from 'k6/execution';
import { handleSummary } from './handle-summary.js';
import {
  A1_PRODUCTION_LOAD_APPROVAL,
  A1_HTTP_FAILURE_ABORT_DELAY,
  a1MixedVUs,
  a1HttpFailureAbortThreshold,
  assertA1ManifestValidationReceipt,
  assertA1ProductionProfile,
  assertSafeTarget,
  targetEnvironment,
} from './safety.js';

export const options = { vus: 1, iterations: 1 };

function assert(condition, message) {
  if (!condition) {
    exec.test.abort(message);
  }
}

function throws(callback) {
  try {
    callback();
    return false;
  } catch (_) {
    return true;
  }
}

export default function () {
  const productionUrl = 'https://api.every-festa.com';
  assert(throws(() => assertSafeTarget(productionUrl)), 'production must require explicit opt-in');
  assert(targetEnvironment(productionUrl) === 'a1-production', 'production target metadata must identify A1');
  assertSafeTarget(productionUrl, A1_PRODUCTION_LOAD_APPROVAL);
  assert(throws(() => assertSafeTarget('https://dev-api.every-festa.com', A1_PRODUCTION_LOAD_APPROVAL)),
    'shared development must remain blocked even with production opt-in');

  [1, 5, 10, 25, 50, 100, 150, 200, 250, 300].forEach((rate) => {
    const vus = a1MixedVUs(rate);
    assertA1ProductionProfile({
      scenario: 'mixed', stage: 'baseline', profile: 'step', fixture: 'a1', rate, duration: '3m', ...vus,
    });
  });
  assert(throws(() => assertA1ProductionProfile({
    scenario: 'mixed', stage: 'baseline', profile: 'step', fixture: 'a1', rate: 3, duration: '3m', ...a1MixedVUs(3),
  })), 'A1 step rate 3 RPS must be blocked');
  assert(throws(() => assertA1ProductionProfile({
    scenario: 'mixed', stage: 'baseline', profile: 'step', fixture: 'a1', rate: 151, duration: '3m', ...a1MixedVUs(151),
  })), 'A1 step rate between two allowed values must be blocked');
  assert(throws(() => assertA1ProductionProfile({
    scenario: 'mixed', stage: 'baseline', profile: 'step', fixture: 'a1', rate: 301, duration: '3m', ...a1MixedVUs(301),
  })), 'A1 step rate above 300 RPS must be blocked');
  assert(throws(() => assertA1ProductionProfile({
    scenario: 'mixed', stage: 'baseline', profile: 'step', fixture: 'a1', rate: 50, duration: '5m', ...a1MixedVUs(50),
  })), 'A1 step duration above three minutes must be blocked');
  assertA1ProductionProfile({
    scenario: 'mixed', stage: 'baseline', profile: 'deployment-experiment', fixture: 'a1', rate: 50, duration: '5m', ...a1MixedVUs(50),
  });
  assert(throws(() => assertA1ProductionProfile({
    scenario: 'mixed', stage: 'baseline', profile: 'deployment-experiment', fixture: 'a1', rate: 25, duration: '5m', ...a1MixedVUs(25),
  })), 'A1 deployment experiment must reject a rate other than 50 RPS');
  assert(throws(() => assertA1ProductionProfile({
    scenario: 'mixed', stage: 'baseline', profile: 'deployment-experiment', fixture: 'a1', rate: 50, duration: '3m', ...a1MixedVUs(50),
  })), 'A1 deployment experiment must require five minutes');
  const maximumVus = a1MixedVUs(300);
  assert(maximumVus.preAllocatedVUs === 300 && maximumVus.maxVUs === 3000,
    'A1 maximum step VUs must cover 300 RPS at the 10-second p99 guard');
  assertA1ProductionProfile({
    scenario: 'fixture-manifest', stage: 'a1-manifest', fixture: 'a1', rate: 0, duration: '', preAllocatedVUs: 1, maxVUs: 1,
  });
  assert(throws(() => assertA1ProductionProfile({
    scenario: 'fixture-manifest', stage: 'a1-manifest', fixture: 'a1', rate: 1, duration: '', preAllocatedVUs: 1, maxVUs: 1,
  })), 'A1 manifest validation must reject configurable rate');
  const abortThreshold = a1HttpFailureAbortThreshold();
  assert(abortThreshold.abortOnFail && abortThreshold.delayAbortEval === A1_HTTP_FAILURE_ABORT_DELAY,
    'A1 HTTP failure threshold must abort after its configured delay');
  assertA1ManifestValidationReceipt('validated', productionUrl);
  assert(throws(() => assertA1ManifestValidationReceipt('', productionUrl)),
    'A1 mixed load must require a manifest validation receipt');

  const outputs = handleSummary({ metrics: {} });
  const summary = JSON.parse(Object.entries(outputs).find(([name]) => name.endsWith('/k6-summary.json'))[1]);
  assert(summary.run.targetEnvironment === 'a1-production' && summary.run.productionOptIn,
    'A1 run summary metadata must retain the target environment and opt-in state');
}
