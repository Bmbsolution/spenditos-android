import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import http from 'node:http';
import { after, before, test } from 'node:test';
import { createSidecarServer } from './server.mjs';

const HOME_USER_ID = '22222222-2222-4222-8222-222222222222';
let harness, sidecar, baseUrl, harnessUrl;
let harnessBulkRequests = [];
let harnessCategoryUpdates = [];
const harnessTransactions = [
  { id: 'tx-a', description: 'TIM HORTONS #4521', categoryId: 'harness-groceries' },
  { id: 'tx-b', description: 'Tim Hortons 33', categoryId: 'harness-dining' },
  { id: 'tx-c', description: 'tim hortons refund', categoryId: 'harness-salary' },
  { id: 'tx-d', description: 'TIM HORTONS #2', categoryId: 'harness-groceries', templateId: 'tpl-1' },
  { id: 'tx-e', description: 'TIM HORTONS DRIVE THRU', categoryId: 'harness-groceries' },
  { id: 'tx-f', description: 'Unrelated', categoryId: 'harness-groceries' },
];

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
        { id: 'harness-dining', userId: HOME_USER_ID, name: 'Dining', color: '#FF9800', isIncome: false },
      ] });
    }
    if (req.url === '/user/settings' && req.method === 'GET') {
      assert.equal(req.headers.authorization, `Bearer e2e-${HOME_USER_ID}`);
      return respond(res, 200, { data: { userId: HOME_USER_ID, currency: 'CAD', timezone: 'America/Toronto', setupCompleted: true } });
    }
    if (req.url.startsWith('/transactions?') && req.method === 'GET') {
      return respond(res, 200, { data: { transactions: harnessTransactions, pagination: { hasMore: false } } });
    }
    const txUpdate = /^\/transactions\/([^/]+)$/.exec(req.url);
    if (txUpdate && req.method === 'PUT') {
      const payload = JSON.parse(await readRequestBody(req));
      harnessCategoryUpdates.push({ id: txUpdate[1], ...payload });
      return respond(res, 200, { data: { id: txUpdate[1], ...payload } });
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

const ONE_PIXEL_JPEG = Buffer.from('/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////wAALCAABAAEBAREA/8QAFAABAAAAAAAAAAAAAAAAAAAAA//EABQQAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEAAD8AKp//2Q==', 'base64');

async function authFor(email, password) {
  const tokens = email ? await login(email, password) : await login();
  return { authorization: `Bearer ${tokens.accessToken}` };
}

async function startScan(auth, documents, extra = {}) {
  const result = await api('/scans', { method: 'POST', headers: auth, body: { documents, ...extra } });
  assert.equal(result.response.status, 202);
  return result.body.data;
}

async function pollScan(auth, scanId) {
  const result = await api(`/scans/${scanId}`, { headers: auth });
  assert.equal(result.response.status, 200);
  return result.body.data;
}

test('scan upload-url accepts images and spreadsheets but not PDFs', async () => {
  const auth = await authFor();
  for (const mimeType of ['image/jpeg', 'image/png', 'image/webp', 'text/csv', 'application/vnd.ms-excel', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet']) {
    const result = await api('/transactions/upload-url', { method: 'POST', headers: auth, body: { mimeType, documentType: 'scan' } });
    assert.equal(result.response.status, 200, mimeType);
    assert.match(result.body.data.documentKey, new RegExp(`^users/${HOME_USER_ID}/scan/`));
    assert.equal(result.body.data.maxFileSizeBytes, 10 * 1024 * 1024);
    assert.equal(result.body.data.expiresIn, 300);
    assert.equal(result.body.data.fields.key, result.body.data.documentKey);
  }
  const pdf = await api('/transactions/upload-url', { method: 'POST', headers: auth, body: { mimeType: 'application/pdf', documentType: 'scan' } });
  assert.equal(pdf.response.status, 400);
  assert.match(pdf.body.message, /^Unsupported file type for scan\./);

  const { uploadUrl, fields } = (await api('/transactions/upload-url', { method: 'POST', headers: auth, body: { mimeType: 'image/jpeg', documentType: 'scan' } })).body.data;
  const form = multipart(fields, ONE_PIXEL_JPEG, 'shot.jpg');
  const uploaded = await fetch(uploadUrl, { method: 'POST', headers: { 'content-type': `multipart/form-data; boundary=${form.boundary}` }, body: form.body });
  assert.equal(uploaded.status, 204);
});

test('scan validation mirrors the backend', async () => {
  const auth = await authFor();
  const bad = async body => (await api('/scans', { method: 'POST', headers: auth, body })).response.status;
  const doc = { documentKey: `users/${HOME_USER_ID}/scan/a.jpg`, kind: 'image' };
  assert.equal(await bad({ documents: [] }), 400);
  assert.equal(await bad({ documents: Array.from({ length: 61 }, () => doc) }), 400);
  assert.equal(await bad({ documents: [{ ...doc, kind: 'pdf' }] }), 400);
  assert.equal(await bad({ documents: [{ ...doc, pdfGroupId: 'bad id!' }] }), 400);
  assert.equal(await bad({ documents: [{ ...doc, kind: 'spreadsheet', pdfGroupId: 'g1' }] }), 400);
  assert.equal(await bad({ documents: [doc], sourceLabel: 'x'.repeat(81) }), 400);
  const error = await api('/scans', { method: 'POST', headers: auth, body: { documents: [] } });
  assert.equal(error.body.error, 'ValidationError');
  const ok = await api('/scans', { method: 'POST', headers: auth, body: { documents: Array.from({ length: 60 }, (_, i) => ({ ...doc, pdfGroupId: 'g1', documentKey: `${doc.documentKey}${i}` })) } });
  assert.equal(ok.response.status, 202);
  assert.equal(ok.body.data.total, 60);
  assert.equal((await api('/scans', { method: 'POST', body: { documents: [doc] } })).response.status, 401);
});

test('receipt scan: processing twice, then ready with one item; complete is idempotent', async () => {
  const auth = await authFor();
  const started = await startScan(auth, [{ documentKey: `users/${HOME_USER_ID}/scan/r.jpg`, kind: 'image', filename: 'Grocery-RECEIPT.jpg' }], { referenceDate: '2026-10-11' });
  assert.match(started.scanId, /^scn_/);
  assert.equal(started.status, 'processing');
  assert.equal(started.total, 1);

  const early = await api(`/scans/${started.scanId}/complete`, { method: 'POST', headers: auth, body: { outcome: 'saved' } });
  assert.equal(early.response.status, 409);
  assert.equal(early.body.error, 'ScanNotReady');

  const first = await pollScan(auth, started.scanId);
  const second = await pollScan(auth, started.scanId);
  for (const poll of [first, second]) {
    assert.equal(poll.status, 'processing');
    assert.equal(poll.kind, null);
    assert.equal(poll.items, undefined);
    assert.equal(poll.itemCount, 0);
  }
  const ready = await pollScan(auth, started.scanId);
  assert.equal(ready.status, 'ready');
  assert.equal(ready.kind, 'receipt');
  assert.deepEqual(ready.progress, { done: 1, total: 1 });
  assert.equal(ready.itemCount, 1);
  assert.equal(ready.sourceLabel, '1 file');
  assert.equal(ready.items.length, 1);
  assert.deepEqual(Object.keys(ready.items[0]).sort(), ['amount', 'categoryHint', 'categoryId', 'confidence', 'currency', 'date', 'description', 'documentIndex', 'id', 'isPending', 'merchantName', 'possibleDuplicateOf', 'ruleId', 'skipReason', 'transactionType'].sort());
  assert.equal(ready.items[0].id, `${started.scanId}-0-0`);
  assert.equal(ready.items[0].date, '2026-10-10');

  const listed = await api('/scans', { headers: auth });
  assert.ok(listed.body.data.scans.some(scan => scan.scanId === started.scanId && scan.status === 'ready' && scan.items === undefined));
  const done = await api(`/scans/${started.scanId}/complete`, { method: 'POST', headers: auth, body: { outcome: 'discarded' } });
  assert.equal(done.response.status, 200);
  assert.equal(done.body.data.status, 'discarded');
  const again = await api(`/scans/${started.scanId}/complete`, { method: 'POST', headers: auth, body: { outcome: 'discarded' } });
  assert.equal(again.body.data.status, 'discarded');
  assert.equal((await api(`/scans/${started.scanId}/complete`, { method: 'POST', headers: auth, body: { outcome: 'nope' } })).response.status, 400);
  assert.equal((await api('/scans/scn_missing', { headers: auth })).response.status, 404);
  const hidden = await api('/scans', { headers: auth });
  assert.ok(!hidden.body.data.scans.some(scan => scan.scanId === started.scanId));
  const withDiscarded = await api('/scans?status=discarded', { headers: auth });
  assert.ok(withDiscarded.body.data.scans.some(scan => scan.scanId === started.scanId));
  assert.equal((await api('/scans?status=bogus', { headers: auth })).response.status, 400);
});

test('list scan: deterministic rows, user rules, bulk save marks it saved', async () => {
  const auth = await authFor();
  const skipRule = await api('/settings/merchant-mappings', { method: 'POST', headers: auth, body: { rawPattern: 'Netflix', action: 'skip' } });
  const coffeeRule = await api('/settings/merchant-mappings', { method: 'POST', headers: auth, body: { rawPattern: 'Blue Bottle', categoryId: 'harness-dining' } });
  assert.equal(skipRule.response.status, 201);
  assert.equal(coffeeRule.response.status, 201);

  const started = await startScan(auth, [{ documentKey: `users/${HOME_USER_ID}/scan/l.jpg`, kind: 'image', filename: 'statement.png' }], { referenceDate: '2026-10-11', userCurrency: 'CAD' });
  assert.equal((await pollScan(auth, started.scanId)).status, 'processing');
  assert.equal((await pollScan(auth, started.scanId)).status, 'processing');
  const ready = await pollScan(auth, started.scanId);
  assert.equal(ready.kind, 'list');
  assert.equal(ready.items.length, 12);
  assert.equal(ready.itemCount, 12);
  assert.equal(ready.items.at(-1).date, null);
  const dates = ready.items.filter(item => item.date).map(item => item.date);
  assert.deepEqual(dates, [...dates].sort().reverse());
  assert.ok(dates[0] <= '2026-10-11' && dates.at(-1) >= '2026-09-01');
  assert.ok(new Set(dates.map(date => date.slice(0, 7))).size >= 2);
  assert.ok(ready.items.every(item => item.currency === 'CAD' && item.id.startsWith(`${started.scanId}-0-`)));
  const coffee = ready.items.filter(item => item.merchantName === 'Blue Bottle Coffee');
  assert.equal(coffee.length, 4);
  assert.ok(coffee.every(item => item.categoryId === 'harness-dining' && item.ruleId === coffeeRule.body.data.id));
  assert.equal(ready.items.find(item => item.skipReason === 'duplicate').possibleDuplicateOf.transactionId, 'txn_existing-001');
  assert.equal(ready.items.find(item => item.skipReason === 'payment').description, 'PAYMENT – THANK YOU');
  assert.ok(ready.items.some(item => item.skipReason === 'transfer'));
  assert.ok(ready.items.some(item => item.transactionType === 'income' && !item.skipReason));
  assert.ok(ready.items.some(item => item.isPending === true));
  const ruled = ready.items.find(item => item.skipReason === 'rule');
  assert.equal(ruled.ruleId, skipRule.body.data.id);
  assert.equal(ready.ruleCount, 5);
  assert.equal(ready.flaggedCount, 4);
  const again = await pollScan(auth, started.scanId);
  assert.deepEqual(again.items, ready.items);

  harnessBulkRequests = [];
  const missing = await api('/transactions/bulk', { method: 'POST', headers: auth, body: { transactions: [{ amount: 1, date: '2026-10-01' }], source: 'scan', scanId: 'scn_unknown' } });
  assert.equal(missing.response.status, 404);
  assert.equal(missing.body.error, 'ScanNotFound');
  const saved = await api('/transactions/bulk', { method: 'POST', headers: auth, body: { transactions: [{ amount: 4.5, date: '2026-10-11', description: 'BLUE BOTTLE', categoryId: 'harness-dining', idempotencyKeyId: ready.items[0].id }], source: 'scan', scanId: started.scanId } });
  assert.equal(saved.response.status, 200);
  assert.equal(harnessBulkRequests.length, 1);
  assert.equal(harnessBulkRequests[0].payload.source, 'scan');
  assert.equal('scanId' in harnessBulkRequests[0].payload, false);
  assert.equal((await pollScan(auth, started.scanId)).status, 'saved');
  assert.equal((await api(`/scans/${started.scanId}/complete`, { method: 'POST', headers: auth, body: { outcome: 'saved' } })).body.data.status, 'saved');

  // Other bulk requests are untouched.
  harnessBulkRequests = [];
  await api('/transactions/bulk', { method: 'POST', headers: auth, body: { transactions: [{ amount: 2, date: '2026-10-01' }], source: 'manual', scanId: 'keep-me' } });
  assert.equal(harnessBulkRequests[0].payload.scanId, 'keep-me');
});

test('scan quota flag blocks the non-Pro account only; reset clears scans and flag', async () => {
  const demo = await authFor();
  const pro = await authFor('pro@spenditos.test', 'Passw0rd!pro1');
  const documents = [{ documentKey: `users/${HOME_USER_ID}/scan/q1.jpg`, kind: 'image' }, { documentKey: `users/${HOME_USER_ID}/scan/q2.jpg`, kind: 'image', pdfGroupId: 'p1' }, { documentKey: `users/${HOME_USER_ID}/scan/q3.jpg`, kind: 'image', pdfGroupId: 'p1' }];
  assert.equal((await api('/__sidecar/scan-quota', { method: 'POST', body: { exhausted: 'yes' } })).response.status, 400);
  assert.equal((await api('/__sidecar/scan-quota', { method: 'POST', body: { exhausted: true } })).body.data.exhausted, true);
  const blocked = await api('/scans', { method: 'POST', headers: demo, body: { documents } });
  assert.equal(blocked.response.status, 403);
  assert.equal(blocked.body.error, 'QuotaExhausted');
  assert.equal(blocked.body.quotaType, 'scanCredits');
  assert.equal(blocked.body.amount, 2);
  assert.equal(blocked.body.remaining, 0);
  assert.match(blocked.body.resetDate, /^\d{4}-\d{2}-\d{2}$/);
  assert.equal(blocked.body.data, undefined);
  const allowed = await api('/scans', { method: 'POST', headers: pro, body: { documents } });
  assert.equal(allowed.response.status, 202);
  assert.equal((await api('/scans', { headers: pro })).body.data.scans.length > 0, true);
  assert.equal((await api('/scans', { headers: demo })).body.data.scans.every(scan => scan.scanId !== allowed.body.data.scanId), true);

  await api('/__sidecar/reset', { method: 'POST' });
  const demoAfter = await authFor();
  const proAfter = await authFor('pro@spenditos.test', 'Passw0rd!pro1');
  assert.deepEqual((await api('/scans', { headers: proAfter })).body.data.scans, []);
  assert.equal((await api('/scans', { method: 'POST', headers: demoAfter, body: { documents } })).response.status, 202);
});

test('rules: upsert, partial update, conflicts, delete, match-count, apply', async () => {
  await api('/__sidecar/reset', { method: 'POST' });
  const auth = await authFor();
  const rules = path => api(`/settings/merchant-mappings${path}`, { headers: auth });
  const send = (path, method, body) => api(`/settings/merchant-mappings${path}`, { method, headers: auth, body });

  assert.equal((await send('', 'POST', { rawPattern: '   ' })).response.status, 400);
  assert.equal((await send('', 'POST', { rawPattern: '!!!', categoryId: 'harness-dining' })).response.status, 400);
  const noCategory = await send('', 'POST', { rawPattern: 'Tim Hortons' });
  assert.equal(noCategory.body.error, 'ValidationError');
  assert.equal(noCategory.body.details[0].field, 'categoryId');
  assert.equal((await send('', 'POST', { rawPattern: 'Tim Hortons', categoryId: 'nope' })).body.error, 'CategoryNotFound');

  const created = await send('', 'POST', { rawPattern: 'Tim Hortons', categoryId: 'harness-dining' });
  assert.equal(created.response.status, 201);
  const rule = created.body.data;
  assert.equal(rule.source, 'user');
  assert.equal(rule.action, 'categorize');
  assert.equal(rule.isRegex, false);
  assert.equal(rule.cleanMerchantName, 'Tim Hortons');
  assert.equal(rule.confidence, 1);
  const upserted = await send('', 'POST', { rawPattern: 'TIM-HORTONS', categoryId: 'harness-groceries', cleanMerchantName: 'Tims' });
  assert.equal(upserted.response.status, 200);
  assert.equal(upserted.body.data.id, rule.id);
  assert.equal(upserted.body.data.categoryId, 'harness-groceries');
  assert.equal(upserted.body.data.cleanMerchantName, 'Tims');

  const skip = await send('', 'POST', { rawPattern: 'Payment thank you', action: 'skip', categoryId: 'harness-dining' });
  assert.equal(skip.response.status, 201);
  assert.equal(skip.body.data.action, 'skip');
  assert.equal(skip.body.data.categoryId, undefined);

  const all = (await rules('')).body.data;
  assert.equal(all.filter(item => item.source === 'user').length, 2);
  assert.ok(all.some(item => item.source === 'ai'));

  assert.equal((await send(`/${rule.id}`, 'PUT', {})).response.status, 400);
  assert.equal((await send('/missing', 'PUT', { cleanMerchantName: 'x' })).response.status, 404);
  const renamed = await send(`/${rule.id}`, 'PUT', { cleanMerchantName: 'Timmies', categoryId: 'harness-dining' });
  assert.equal(renamed.response.status, 200);
  assert.equal(renamed.body.data.cleanMerchantName, 'Timmies');
  assert.equal(renamed.body.data.rawPattern, 'TIM-HORTONS');
  const clash = await send(`/${rule.id}`, 'PUT', { rawPattern: 'payment: thank you' });
  assert.equal(clash.response.status, 409);
  assert.equal(clash.body.error, 'RuleExists');
  const toSkip = await send(`/${rule.id}`, 'PUT', { action: 'skip' });
  assert.equal(toSkip.body.data.categoryId, undefined);
  assert.equal((await send(`/${rule.id}/apply`, 'POST')).response.status, 409);
  assert.equal((await send(`/${rule.id}/apply`, 'POST')).body.error, 'SkipRuleCannotApply');
  await send(`/${rule.id}`, 'PUT', { action: 'categorize', categoryId: 'harness-dining' });

  assert.equal((await rules('/match-count?merchant=Tim%20Hortons')).response.status, 400);
  const count = await rules('/match-count?merchant=Tim%20Hortons&categoryId=harness-dining');
  assert.equal(count.response.status, 200);
  assert.equal(count.body.data.count, 2);
  assert.equal((await rules('/match-count?merchant=ab&categoryId=harness-dining')).body.data.count, 0);

  harnessCategoryUpdates = [];
  const applied = await send(`/${rule.id}/apply`, 'POST');
  assert.equal(applied.response.status, 200);
  assert.deepEqual(applied.body.data, { updated: 2 });
  assert.deepEqual(harnessCategoryUpdates.map(update => update.id).sort(), ['tx-a', 'tx-e']);
  assert.ok(harnessCategoryUpdates.every(update => update.categoryId === 'harness-dining'));

  const removed = await send(`/${rule.id}`, 'DELETE');
  assert.equal(removed.response.status, 204);
  assert.equal((await send(`/${rule.id}`, 'DELETE')).response.status, 204);
  assert.equal((await rules('')).body.data.some(item => item.id === rule.id), false);
  assert.equal((await send(`/${rule.id}/apply`, 'POST')).response.status, 404);

  await api('/__sidecar/reset', { method: 'POST' });
  const authAfter = await authFor();
  const afterReset = await api('/settings/merchant-mappings', { headers: authAfter });
  assert.equal(afterReset.body.data.filter(item => item.source === 'user').length, 0);
});

test('user export: unenveloped JSON, gzip on request, rules included, failure toggle', async () => {
  await api('/__sidecar/reset', { method: 'POST' });
  const auth = await authFor();
  await api('/settings/merchant-mappings', { method: 'POST', headers: auth, body: { rawPattern: 'Netflix', action: 'skip' } });

  const plain = await fetch(`${baseUrl}/user/export`, { headers: { ...auth, 'accept-encoding': 'identity' } });
  assert.equal(plain.status, 200);
  assert.equal(plain.headers.get('content-type'), 'application/json; charset=utf-8');
  assert.match(plain.headers.get('content-disposition'), /^attachment; filename="spenditos-data-\d{4}-\d{2}-\d{2}\.json"$/);
  assert.equal(plain.headers.get('cache-control'), 'no-store');
  assert.equal(plain.headers.get('content-encoding'), null);
  const doc = await plain.json();
  assert.equal(doc.data, undefined);
  assert.equal(doc.format, 'spenditos-export');
  assert.equal(doc.version, 1);
  assert.ok(doc.exportedAt && doc.account && doc.categories.length && doc.incomeAndBills.length && doc.transactions.length);
  assert.deepEqual(doc.rules.map(rule => rule.rawPattern), ['Netflix']);

  // fetch (undici) sends Accept-Encoding: gzip and gunzips transparently; read the raw bytes via http.
  const raw = await new Promise((resolve, reject) => {
    http.get(`${baseUrl}/user/export`, { headers: { ...auth, 'accept-encoding': 'gzip' } }, res => {
      const chunks = [];
      res.on('data', chunk => chunks.push(chunk));
      res.on('end', () => resolve({ headers: res.headers, body: Buffer.concat(chunks) }));
    }).on('error', reject);
  });
  assert.equal(raw.headers['content-encoding'], 'gzip');
  const { gunzipSync } = await import('node:zlib');
  assert.equal(JSON.parse(gunzipSync(raw.body).toString('utf8')).format, 'spenditos-export');

  assert.equal((await api('/__sidecar/export-fail', { method: 'POST', body: { fail: 'yes' } })).response.status, 400);
  assert.equal((await api('/__sidecar/export-fail', { method: 'POST', body: { fail: true } })).body.data.fail, true);
  const failed = await api('/user/export', { headers: auth });
  assert.equal(failed.response.status, 500);
  assert.deepEqual(failed.body, { error: 'ExportFailed', message: 'Could not build your data file. Please try again.' });
  await api('/__sidecar/reset', { method: 'POST' });
  assert.equal((await api('/user/export', { headers: await authFor() })).response.status, 200);
  assert.equal((await api('/user/export')).response.status, 401);
});
