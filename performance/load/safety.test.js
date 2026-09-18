import exec from 'k6/execution';
import { handleSummary } from './handle-summary.js';
import {
  A1_PRODUCTION_LOAD_APPROVAL,
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

  assertA1ProductionProfile({
    scenario: 'mixed', stage: 'baseline', rate: 10, duration: '3m', preAllocatedVUs: 2, maxVUs: 10,
  });
  assert(throws(() => assertA1ProductionProfile({
    scenario: 'mixed', stage: 'baseline', rate: 11, duration: '3m', preAllocatedVUs: 2, maxVUs: 10,
  })), 'A1 production rate above 10 RPS must be blocked');
  assert(throws(() => assertA1ProductionProfile({
    scenario: 'mixed', stage: 'baseline', rate: 10, duration: '4m', preAllocatedVUs: 2, maxVUs: 10,
  })), 'A1 production duration above three minutes must be blocked');

  const outputs = handleSummary({ metrics: {} });
  const summary = JSON.parse(Object.entries(outputs).find(([name]) => name.endsWith('/k6-summary.json'))[1]);
  assert(summary.run.targetEnvironment === 'a1-production' && summary.run.productionOptIn,
    'A1 run summary metadata must retain the target environment and opt-in state');
}
