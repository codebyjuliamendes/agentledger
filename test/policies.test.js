const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');
const { spawn } = require('node:child_process');
const { once } = require('node:events');
const fs = require('node:fs/promises');
const os = require('node:os');
const path = require('node:path');
let child, base, temp;
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
async function api(route, options) { return fetch(base + route, { headers: { 'content-type': 'application/json' }, ...options }); }
before(async () => {
  temp = await fs.mkdtemp(path.join(os.tmpdir(), 'agentledger-test-'));
  const port = 20000 + Math.floor(Math.random() * 40000); base = `http://127.0.0.1:${port}`;
  child = spawn(process.execPath, ['server.js'], { cwd: path.resolve(__dirname, '..'), env: { ...process.env, PORT: String(port), DATABASE_PATH: path.join(temp, 'test.sqlite') }, stdio: 'ignore' });
  for (let i = 0; i < 100; i++) { if (child.exitCode !== null) throw new Error('server exited early'); try { if ((await api('/api/policies')).ok) return; } catch {} await delay(50); }
  throw new Error('server did not start');
});
after(async () => { if (child && child.exitCode === null) { child.kill(); await Promise.race([once(child, 'exit'), delay(2000)]); } if (temp) await fs.rm(temp, { recursive: true, force: true }); });
test('configured terms route to human review and critical actions remain blocked', async () => {
  let response = await api('/api/policies', { method: 'PUT', body: JSON.stringify({ medium: ['custom'], high: ['sensitive'] }) });
  assert.equal(response.status, 200); assert.deepEqual((await response.json()).medium, ['custom']);
  response = await api('/api/actions', { method: 'POST', body: JSON.stringify({ agent: 'test', action: 'custom operation', target: 'resource:1', reason: 'test policy' }) });
  const medium = await response.json(); assert.equal(medium.risk, 'medium'); assert.equal(medium.status, 'pending');
  response = await api('/api/actions', { method: 'POST', body: JSON.stringify({ agent: 'test', action: 'delete production database', target: 'db:prod', reason: 'test mandatory rule' }) });
  const critical = await response.json(); assert.equal(critical.status, 'blocked');
  response = await api(`/api/actions/${critical.id}/review`, { method: 'POST', body: JSON.stringify({ decision: 'approve' }) });
  assert.equal(response.status, 409);
});
