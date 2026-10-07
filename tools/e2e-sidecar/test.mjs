import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import http from 'node:http';
import { after, before, test } from 'node:test';
import { createSidecarServer } from './server.mjs';

const HOME_USER_ID = '22222222-2222-4222-8222-222222222222';
let harness, sidecar, baseUrl, harnessUrl;
let harnessBulkRequests = [];

function listen(server) {
  return new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(0, '127.0.0.1', () => {
      server.removeListener('error', reject);
      resolve(server.address().port);
    });
  });
}

function close(server) {
  return new Promise((resolve, reject) => server.close(error => error ? reject(error) : resolve()));
}

async function readRequestBody(req) {
  const chunks = [];
  for await (const chunk of req) chunks.push(chunk);
  return Buffer.concat(chunks).toString('utf8');
}

function respond(res, status, body) {
  res.writeHead(status, { 'content-type': 'application/json' });
  res.end(JSON.stringify(body));
}

before(async () => {
  harnessBulkRequests = [];
  harness = http.createServer(async (req, res) => {
    if (req.url === '/categories' && req.method === 'GET') {
      assert.equal(req.headers.authorization, `Bearer e2e-${HOME_USER_ID}`);
      return respond(res, 200, { data: [
        { id: 'harness-groceries', userId: HOME_USER_ID, name: 'Groceries', color: '#4CAF50', isIncome: false },
        { id: 'harness-salary', userId: HOME_USER_ID, name: 'Salary', color: '#2196F3', isIncome: true },
      ] });
    }
    if (req.url === '/user/settings' && req.method === 'GET') {
      assert.equal(req.headers.authorization, `Bearer e2e-${HOME_USER_ID}`);
      return respond(res, 200, { data: { userId: HOME_USER_ID, currency: 'CAD', timezone: 'America/Toronto', setupCompleted: true } });
    }
    if (req.url === '/transactions/bulk' && req.method === 'POST') {
      assert.equal(req.headers.authorization, `Bearer e2e-${HOME_USER_ID}`);
      const payload = JSON.parse(await readRequestBody(req));
      harnessBulkRequests.push({ payload, idempotencyKey: req.headers['idempotency-key'] });
      return respond(res, 200, { data: { results: payload.transactions.map((item, index) => ({ index, transaction: { id: `tx-${harnessBulkRequests.length}-${index}`, ...item } })), failed: [], duplicates: 0 } });
    }
    return respond(res, 404, { message: 'No fake harness route', error: 'NotFound' });
  });
  const harnessPort = await listen(harness);
  harnessUrl = process.env.HARNESS_URL;
  process.env.HARNESS_URL = `http://127.0.0.1:${harnessPort}`;
  sidecar = createSidecarServer();
  const sidecarPort = await listen(sidecar);
  baseUrl = `http://127.0.0.1:${sidecarPort}`;
});

after(async () => {
  if (sidecar?.listening) await close(sidecar);
  if (harness?.listening) await close(harness);
  if (harnessUrl === undefined) delete process.env.HARNESS_URL;
  else process.env.HARNESS_URL = harnessUrl;
  delete process.env.QUOTA_EXHAUSTED;
});

async function api(path, { method = 'GET', body, headers = {} } = {}) {
  const response = await fetch(`${baseUrl}${path}`, {
    method,
    headers: { ...(body === undefined ? {} : { 'content-type': 'application/json' }), ...headers },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await response.text();
  return { response, body: text ? JSON.parse(text) : null };
}

async function login(email = 'demo@spenditos.test', password = 'Passw0rd!demo') {
  const result = await api('/auth/login', { method: 'POST', body: { email, password } });
  assert.equal(result.response.status, 200);
  return result.body.data;
}

function multipart(fields, fileBytes, filename, fileFirst = false) {
  const boundary = `----sidecar-${Math.random().toString(16).slice(2)}`;
  const parts = [];
  const fieldParts = Object.entries(fields).map(([name, value]) => Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="${name}"\r\n\r\n${value}\r\n`));
  const filePart = Buffer.concat([
    Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="file"; filename="${filename}"\r\nContent-Type: text/csv\r\n\r\n`),
    fileBytes,
    Buffer.from('\r\n'),
  ]);
  parts.push(...(fileFirst ? [filePart, ...fieldParts] : [...fieldParts, filePart]));
  parts.push(Buffer.from(`--${boundary}--\r\n`));
  return { boundary, body: Buffer.concat(parts) };
}

test('auth: seed login, errors, registration verification, and refresh', async () => {
  const good = await login();
  assert.match(good.accessToken, /^ey/);
  const claims = JSON.parse(Buffer.from(good.idToken.split('.')[1], 'base64url').toString('utf8'));
  assert.equal(claims.sub, HOME_USER_ID);
  assert.equal(claims.email, 'demo@spenditos.test');

  const wrong = await api('/auth/login', { method: 'POST', body: { email: 'demo@spenditos.test', password: 'incorrect' } });
  assert.equal(wrong.response.status, 401);
  assert.equal(wrong.body.error, 'Unauthorized');

  const weak = await api('/auth/register', { method: 'POST', body: { email: 'weak@spenditos.test', password: 'password1', timezone: 'UTC' } });
  assert.equal(weak.response.status, 400);
  assert.equal(weak.body.error, 'InvalidPassword');

  const registration = await api('/auth/register', { method: 'POST', body: { email: 'new@spenditos.test', password: 'Strong1!pass', timezone: 'America/Toronto' } });
  assert.equal(registration.response.status, 201);
  assert.equal(registration.body.data.needsConfirmation, true);
  const unverified = await api('/auth/login', { method: 'POST', body: { email: 'new@spenditos.test', password: 'Strong1!pass' } });
  assert.equal(unverified.response.status, 403);
  assert.equal(unverified.body.error, 'UserNotConfirmed');
  const badCode = await api('/auth/confirm-registration', { method: 'POST', body: { email: 'new@spenditos.test', code: '000000' } });
  assert.equal(badCode.response.status, 400);
  assert.equal(badCode.body.error, 'CodeMismatch');
  const verified = await api('/auth/confirm-registration', { method: 'POST', body: { email: 'new@spenditos.test', code: '123456' } });
  assert.equal(verified.response.status, 200);
  const registeredLogin = await login('new@spenditos.test', 'Strong1!pass');

  const refreshed = await api('/auth/refresh', { method: 'POST', body: { refreshToken: registeredLogin.refreshToken } });
  assert.equal(refreshed.response.status, 200);
  assert.ok(refreshed.body.data.accessToken);
  assert.ok(refreshed.body.data.idToken);
  const unknownRefresh = await api('/auth/refresh', { method: 'POST', body: { refreshToken: 'not-a-refresh-token' } });
  assert.equal(unknownRefresh.response.status, 401);
  assert.equal(unknownRefresh.body.error, 'Unauthorized');
});

test('S3 form ordering and CSV statement analyze, preview, and idempotent import', async () => {
  const tokens = await login();
  const auth = { authorization: `Bearer ${tokens.accessToken}` };
  const fixture = await readFile(new URL('./fixtures/sample-bank-statement.csv', import.meta.url));
  const urlResult = await api('/transactions/upload-url', { method: 'POST', headers: auth, body: { mimeType: 'text/csv', documentType: 'bank_statement' } });
  assert.equal(urlResult.response.status, 200);
  const { uploadUrl, fields, documentKey } = urlResult.body.data;

  const invalidOrder = multipart(fields, fixture, 'sample-bank-statement.csv', true);
  const rejected = await fetch(uploadUrl, { method: 'POST', headers: { 'content-type': `multipart/form-data; boundary=${invalidOrder.boundary}` }, body: invalidOrder.body });
  assert.equal(rejected.status, 400);

  const valid = multipart(fields, fixture, 'sample-bank-statement.csv');
  const uploaded = await fetch(uploadUrl, { method: 'POST', headers: { 'content-type': `multipart/form-data; boundary=${valid.boundary}` }, body: valid.body });
  assert.equal(uploaded.status, 204);

  const analyze = await api('/statements/analyze-spreadsheet', { method: 'POST', headers: auth, body: { filename: 'sample-bank-statement.csv', documentKey } });
  assert.equal(analyze.response.status, 200);
  assert.equal(analyze.body.data.mapping.dateColumn, 'Date');
  assert.equal(analyze.body.data.mapping.descriptionColumn, 'Description');
  assert.equal(analyze.body.data.mapping.amountColumn, 'Amount');
  assert.equal(analyze.body.data.preview.length, 2);

  const preview = await api('/statements/preview-spreadsheet', { method: 'POST', headers: auth, body: { filename: 'sample-bank-statement.csv', documentKey, mapping: analyze.body.data.mapping } });
  assert.equal(preview.response.status, 200);
  assert.equal(preview.body.data.transactions.length, 2);
  assert.equal(preview.body.data.transactions[0].date, '2026-09-01');
  assert.equal(preview.body.data.transactions[0].amount, -64.2);
  assert.equal(preview.body.data.transactions[1].amount, 2500);

  harnessBulkRequests = [];
  const importBody = { transactions: preview.body.data.transactions };
  const imported = await api('/statements/import-spreadsheet', { method: 'POST', headers: { ...auth, 'Idempotency-Key': 'synthetic-statement-01' }, body: importBody });
  assert.equal(imported.response.status, 200);
  assert.deepEqual(imported.body.data.stats, { created: 2, updated: 0, skipped: 0 });
  assert.equal(harnessBulkRequests.length, 1);
  assert.equal(harnessBulkRequests[0].payload.source, 'import');
  assert.deepEqual(harnessBulkRequests[0].payload.transactions.map(item => item.idempotencyKeyId), ['row-0', 'row-1']);
  assert.equal(harnessBulkRequests[0].idempotencyKey, 'synthetic-statement-01');
  assert.equal(harnessBulkRequests[0].payload.transactions[0].amount, 64.2);
  const replay = await api('/statements/import-spreadsheet', { method: 'POST', headers: { ...auth, 'Idempotency-Key': 'synthetic-statement-01' }, body: importBody });
  assert.equal(replay.response.status, 200);
  assert.deepEqual(replay.body.data.stats, { created: 0, updated: 0, skipped: 2 });
  assert.equal(harnessBulkRequests.length, 1);
});

test('typed capture parsing and voice quota exhaustion', async () => {
  const tokens = await login();
  const auth = { authorization: `Bearer ${tokens.accessToken}` };
  const started = await api('/transactions/analyze-audio', { method: 'POST', headers: auth, body: {
    documentKey: `users/${HOME_USER_ID}/typed/note.txt`,
    mode: 'audio-transcript', inputType: 'typed', referenceDate: '2026-10-06', userCurrency: 'CAD',
    transcript: 'I spent $12.50 on lunch yesterday and got $30 from side work today',
  } });
  assert.equal(started.response.status, 202);
  const first = await api(`/transactions/analysis/${started.body.data.analysisId}`, { headers: auth });
  assert.equal(first.body.data.status, 'processing');
  const completed = await api(`/transactions/analysis/${started.body.data.analysisId}`, { headers: auth });
  assert.equal(completed.body.data.status, 'success');
  assert.equal(completed.body.data.items.length, 2);
  assert.equal(completed.body.data.items[0].amount, 12.5);
  assert.equal(completed.body.data.items[0].date, '2026-10-05');
  assert.equal(completed.body.data.items[0].transactionType, 'expense');
  assert.equal(completed.body.data.items[0].description, 'lunch');
  assert.equal(completed.body.data.items[1].date, '2026-10-06');
  assert.equal(completed.body.data.items[1].transactionType, 'income');

  process.env.QUOTA_EXHAUSTED = '1';
  try {
    const quota = await api('/transactions/analyze-audio', { method: 'POST', headers: auth, body: { documentKey: `users/${HOME_USER_ID}/audio/fake.m4a`, mode: 'transcribe', inputType: 'voice' } });
    assert.equal(quota.response.status, 403);
    assert.equal(quota.body.error, 'QuotaExhausted');
    assert.equal(quota.body.quotaType, 'voiceEntries');
  } finally {
    delete process.env.QUOTA_EXHAUSTED;
  }
});

test('settings patch premium by account and reject malformed or expired JWTs', async () => {
  const demo = await login();
  const settings = await api('/user/settings', { headers: { authorization: `Bearer ${demo.accessToken}` } });
  assert.equal(settings.response.status, 200);
  assert.equal(settings.body.data.premium.active, false);
  assert.equal(settings.body.data.currency, 'CAD');
  const pro = await login('pro@spenditos.test', 'Passw0rd!pro1');
  const proSettings = await api('/user/settings', { headers: { authorization: `Bearer ${pro.accessToken}` } });
  assert.equal(proSettings.response.status, 200);
  assert.equal(proSettings.body.data.premium.active, true);
  const malformed = await api('/categories', { headers: { authorization: 'Bearer malformed' } });
  assert.equal(malformed.response.status, 401);
  assert.equal(malformed.body.error, 'Unauthorized');
  const expiredPayload = Buffer.from(JSON.stringify({ sub: HOME_USER_ID, email: 'demo@spenditos.test', exp: Math.floor(Date.now() / 1000) - 1, iat: 1, token_use: 'access' })).toString('base64url');
  const expired = await api('/categories', { headers: { authorization: `Bearer e30.${expiredPayload}.` } });
  assert.equal(expired.response.status, 401);
  assert.equal(expired.body.error, 'Unauthorized');
});
