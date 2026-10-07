import http from 'node:http';
import https from 'node:https';
import { randomUUID, createHash } from 'node:crypto';
import { pathToFileURL } from 'node:url';

const HOME_USER_ID = '22222222-2222-4222-8222-222222222222';
const DEMO_EMAIL = 'demo@spenditos.test';
const PRO_EMAIL = 'pro@spenditos.test';
const DEFAULT_TTL = 60 * 60;
const MAX_JSON_BYTES = 2 * 1024 * 1024;
const MIME_TYPES = {
  receipt: ['image/jpeg', 'image/jpg', 'image/png', 'image/webp', 'image/heic', 'application/pdf'],
  audio: ['audio/mpeg', 'audio/mp3', 'audio/wav', 'audio/x-wav', 'audio/webm', 'audio/mp4', 'audio/x-m4a', 'audio/aac', 'audio/ogg', 'text/plain'],
  bank_statement: ['image/jpeg', 'image/jpg', 'image/png', 'image/webp', 'image/heic', 'application/pdf', 'text/csv', 'application/vnd.ms-excel', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'],
};
const MAX_BYTES = { receipt: 10 * 1024 * 1024, audio: 25 * 1024 * 1024, bank_statement: 25 * 1024 * 1024 };
const BASE_CATEGORIES = [
  { id: 'sidecar-expense-food', name: 'Groceries', color: '#4CAF50', icon: 'cart', isIncome: false, sortOrder: 0, isSystemSuggestion: true },
  { id: 'sidecar-expense-transport', name: 'Transportation', color: '#607D8B', icon: 'car', isIncome: false, sortOrder: 1, isSystemSuggestion: true },
  { id: 'sidecar-expense-other', name: 'Uncategorized', color: '#9ca3af', icon: 'category', isIncome: false, sortOrder: 2, isSystemSuggestion: true },
  { id: 'sidecar-income-salary', name: 'Salary', color: '#2196F3', icon: 'income', isIncome: true, sortOrder: 3, isSystemSuggestion: true },
  { id: 'sidecar-income-other', name: 'Uncategorized Income', color: '#34d399', icon: 'income', isIncome: true, sortOrder: 4, isSystemSuggestion: true },
];

function newState() {
  const accounts = new Map();
  const addSeed = (email, password, premiumActive) => {
    const account = { id: HOME_USER_ID, email, password, verified: true, premiumActive, seeded: true, settings: null, forgotCode: null };
    accounts.set(email, account);
    return account;
  };
  addSeed(DEMO_EMAIL, 'Passw0rd!demo', false);
  addSeed(PRO_EMAIL, 'Passw0rd!pro1', true);
  return {
    accounts,
    refreshTokens: new Map(),
    uploads: new Map(),
    categories: new Map(),
    categoryEdits: new Map(),
    deletedCategories: new Map(),
    analyses: new Map(),
    importRows: new Map(),
  };
}

function json(res, status, value, headers = {}) {
  res.writeHead(status, { 'content-type': 'application/json; charset=utf-8', ...headers });
  res.end(JSON.stringify(value));
}

function apiError(res, status, message, error, extra = {}) {
  json(res, status, { message, error, ...extra });
}

function errorOnly(res, status, error, extra = {}) {
  json(res, status, { error, ...extra });
}

async function readBuffer(req, limit = MAX_JSON_BYTES) {
  const chunks = [];
  let size = 0;
  for await (const chunk of req) {
    size += chunk.length;
    if (size > limit) throw Object.assign(new Error('Request body too large'), { status: 413 });
    chunks.push(chunk);
  }
  return Buffer.concat(chunks);
}

async function readJson(req) {
  let raw;
  try {
    raw = (await readBuffer(req)).toString('utf8');
  } catch (err) {
    if (err.status === 413) throw err;
    return null;
  }
  try { return JSON.parse(raw); } catch { return null; }
}

function b64url(value) {
  return Buffer.from(value).toString('base64url');
}

function issueTokens(account, ttl = Number(process.env.TOKEN_TTL_SECONDS) || DEFAULT_TTL) {
  const now = Math.floor(Date.now() / 1000);
  const makeToken = tokenUse => `${b64url(JSON.stringify({ alg: 'none' }))}.${b64url(JSON.stringify({ sub: account.id, email: account.email, exp: now + Math.max(1, ttl), iat: now, token_use: tokenUse }))}.`;
  const refreshToken = `e2e-refresh-${randomUUID()}`;
  state.refreshTokens.set(refreshToken, account.email);
  return { accessToken: makeToken('access'), idToken: makeToken('id'), refreshToken };
}

function principal(req) {
  const header = req.headers.authorization;
  if (!header?.startsWith('Bearer ')) return null;
  const token = header.slice(7);
  const parts = token.split('.');
  if (parts.length < 2) return null;
  try {
    const claims = JSON.parse(Buffer.from(parts[1], 'base64url').toString('utf8'));
    if (typeof claims.sub !== 'string' || typeof claims.email !== 'string' || !Number.isFinite(claims.exp) || claims.exp <= Math.floor(Date.now() / 1000)) return null;
    const account = state.accounts.get(claims.email.toLowerCase());
    if (!account || account.id !== claims.sub || !account.verified) return null;
    return { account, claims };
  } catch { return null; }
}

function requirePrincipal(req, res) {
  const who = principal(req);
  if (!who) apiError(res, 401, 'Unauthorized', 'Unauthorized');
  return who;
}

function validateEmail(email) {
  return typeof email === 'string' && email.length <= 320 && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

function passwordPolicy(password) {
  if (typeof password !== 'string' || password.length < 8 || password.length > 128) return 'Invalid registration payload';
  if (!/[a-z]/.test(password) || !/[A-Z]/.test(password) || !/[0-9]/.test(password) || !/[^A-Za-z0-9]/.test(password)) return 'Password policy violation: Password must contain uppercase, lowercase, number, and symbol characters';
  return null;
}

function accountForSub(userId) {
  return [...state.accounts.values()].find(account => account.id === userId);
}

function defaultSettings(account) {
  return {
    userId: account.id,
    currency: 'USD',
    mainIncomeTemplateId: null,
    country: null,
    timezone: 'UTC',
    setupCompleted: false,
    displayName: null,
    premium: { tier: account.premiumActive ? 'pro' : null, active: account.premiumActive, expiresAt: null },
  };
}

function settingsWithPremium(account, settings) {
  return { ...settings, userId: account.id, premium: { ...(settings.premium ?? {}), tier: account.premiumActive ? 'pro' : (settings.premium?.tier ?? null), active: account.premiumActive } };
}

function localCategories(account) {
  const custom = state.categories.get(account.id) ?? [];
  const edits = state.categoryEdits.get(account.id) ?? new Map();
  const deleted = state.deletedCategories.get(account.id) ?? new Set();
  const base = BASE_CATEGORIES.map(category => ({ ...category, userId: account.id }));
  return [...base, ...custom].filter(category => !deleted.has(category.id)).map(category => ({ ...category, ...(edits.get(category.id) ?? {}) }));
}

function categoryOverlays(account, harnessCategories) {
  const custom = state.categories.get(account.id) ?? [];
  const edits = state.categoryEdits.get(account.id) ?? new Map();
  const deleted = state.deletedCategories.get(account.id) ?? new Set();
  return [...harnessCategories, ...custom]
    .filter(category => !deleted.has(category.id))
    .map(category => ({ ...category, ...(edits.get(category.id) ?? {}) }));
}

function userIdFromKey(key) {
  return /^users\/([^/]+)\//.exec(key)?.[1] ?? null;
}

function isOwnedKey(key, userId) {
  return typeof key === 'string' && key.startsWith(`users/${userId}/`);
}

function parseCsv(buffer) {
  let text = buffer.toString('utf8').replace(/^\uFEFF/, '');
  const rows = [];
  let row = [], field = '', quoted = false;
  for (let i = 0; i < text.length; i++) {
    const ch = text[i];
    if (quoted) {
      if (ch === '"' && text[i + 1] === '"') { field += '"'; i++; }
      else if (ch === '"') quoted = false;
      else field += ch;
    } else if (ch === '"' && field.length === 0) quoted = true;
    else if (ch === ',') { row.push(field); field = ''; }
    else if (ch === '\n' || ch === '\r') {
      if (ch === '\r' && text[i + 1] === '\n') i++;
      row.push(field); field = '';
      if (row.some(value => value !== '') || rows.length) rows.push(row);
      row = [];
    } else field += ch;
  }
  if (field.length || row.length) { row.push(field); rows.push(row); }
  return rows;
}

function dateFormatFor(value) {
  const v = String(value ?? '').trim();
  if (/^\d{4}-\d{1,2}-\d{1,2}$/.test(v)) return 'YYYY-MM-DD';
  if (/^\d{4}\/\d{1,2}\/\d{1,2}$/.test(v)) return 'YYYY/MM/DD';
  if (/^\d{1,2}\/\d{1,2}\/\d{4}$/.test(v)) return 'MM/DD/YYYY';
  if (/^\d{1,2}-\d{1,2}-\d{4}$/.test(v)) return 'DD-MM-YYYY';
  return 'MM/DD/YYYY';
}

function parseDate(value, format) {
  const raw = String(value ?? '').trim();
  let y, m, d, match;
  if (format === 'YYYY-MM-DD' || format === 'YYYY/MM/DD') {
    match = /^(\d{4})[-/](\d{1,2})[-/](\d{1,2})$/.exec(raw);
    if (match) [, y, m, d] = match;
  } else if (format === 'DD/MM/YYYY' || format === 'DD-MM-YYYY') {
    match = /^(\d{1,2})[-/](\d{1,2})[-/](\d{4})$/.exec(raw);
    if (match) [, d, m, y] = match;
  } else {
    match = /^(\d{1,2})[-/](\d{1,2})[-/](\d{4})$/.exec(raw);
    if (match) [, m, d, y] = match;
  }
  if (!match) return null;
  y = Number(y); m = Number(m); d = Number(d);
  const leap = y % 4 === 0 && (y % 100 !== 0 || y % 400 === 0);
  const monthDays = [31, leap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
  if (m < 1 || m > 12 || d < 1 || d > monthDays[m - 1]) return null;
  return `${String(y).padStart(4, '0')}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
}

function parseAmount(value) {
  let raw = String(value ?? '').trim();
  if (!raw) return 0;
  const negative = raw.includes('-') || (raw.includes('(') && raw.includes(')'));
  const comma = raw.lastIndexOf(','), period = raw.lastIndexOf('.');
  if (comma > period) raw = raw.replace(/\./g, '').replace(',', '.');
  else raw = raw.replace(/,/g, '');
  const parsed = Number.parseFloat(raw.replace(/[^\d.]/g, ''));
  if (!Number.isFinite(parsed)) return 0;
  return negative ? -Math.abs(parsed) : parsed;
}

function suggestionFor(description) {
  const value = description.toLowerCase();
  if (/grocery|grocer|market|food|restaurant|lunch|cafe|coffee/.test(value)) return 'Groceries';
  if (/salary|payroll|paycheck|wage|income/.test(value)) return 'Salary';
  if (/gas|fuel|transit|uber|taxi|parking/.test(value)) return 'Transportation';
  return null;
}

function statementRows(upload, mapping) {
  const rows = parseCsv(upload.bytes);
  const headers = (rows[0] ?? []).map(value => value.trim());
  const indexOf = name => name ? headers.indexOf(name) : -1;
  const dateIdx = indexOf(mapping.dateColumn), descIdx = indexOf(mapping.descriptionColumn);
  const amountIdx = indexOf(mapping.amountColumn), debitIdx = indexOf(mapping.debitColumn), creditIdx = indexOf(mapping.creditColumn);
  return rows.slice(1).map(row => {
    const date = parseDate(row[dateIdx], mapping.dateFormat || dateFormatFor(row[dateIdx]));
    const description = String(row[descIdx] ?? '').trim();
    let amount = 0;
    if (amountIdx >= 0) amount = parseAmount(row[amountIdx]);
    else if (debitIdx >= 0 && creditIdx >= 0) {
      const debit = Math.abs(parseAmount(row[debitIdx]));
      const credit = Math.abs(parseAmount(row[creditIdx]));
      amount = mapping.amountIsNegativeForDebit === false ? debit - credit : credit - debit;
    }
    return { date, description, amount, merchant: null, categoryId: suggestionFor(description) };
  }).filter(row => row.date && row.description && row.amount !== 0);
}

function parseCapture(transcript, referenceDate, currency) {
  const text = String(transcript ?? '').trim();
  const matches = [...text.matchAll(/\$?\b\d+(?:[.,]\d{1,2})?\b/g)];
  const items = [];
  for (let i = 0; i < matches.length; i++) {
    const match = matches[i], end = match.index + match[0].length;
    let after = text.slice(end, i + 1 < matches.length ? matches[i + 1].index : text.length);
    const clause = after.split(/\band\b/i)[0];
    const before = text.slice(i === 0 ? 0 : matches[i - 1].index + matches[i - 1][0].length, match.index);
    const priorClause = before.split(/\band\b/i).at(-1) ?? before;
    const context = `${priorClause} ${clause}`.toLowerCase();
    let description = clause
      .replace(/\b(today|yesterday|\d{4}-\d{2}-\d{2})\b/gi, '')
      .replace(/\b(on|for|at|from|a|an|the)\b/gi, ' ')
      .replace(/[^\p{L}\p{N}' -]/gu, ' ')
      .replace(/\s+/g, ' ').trim();
    if (!description) description = before.replace(/\b(i|spent|paid|me|got|received|salary|income)\b/gi, ' ').replace(/\s+/g, ' ').trim();
    description = description.replace(/\b(and|then|today|yesterday)\b/gi, '').trim();
    const dateToken = /\b(today|yesterday|\d{4}-\d{2}-\d{2})\b/i.exec(`${priorClause} ${clause}`)?.[1];
    const date = captureDate(referenceDate, dateToken);
    const income = /\b(paid me|salary|income|got|received)\b/i.test(context);
    const amountText = match[0].replace('$', '').replace(',', '.');
    items.push({ amount: Number(amountText), currency: currency || 'USD', date, categoryId: null, categoryHint: income ? 'Income' : suggestionFor(description) ?? 'Uncategorized', merchantName: null, description: description || 'Transaction', confidence: 0.84, transactionType: income ? 'income' : 'expense' });
  }
  return items;
}

function captureDate(referenceDate, relative) {
  const now = new Date();
  const localToday = `${String(now.getFullYear()).padStart(4, '0')}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
  const base = /^\d{4}-\d{2}-\d{2}$/.test(String(referenceDate ?? '')) ? String(referenceDate) : localToday;
  if (relative && /^\d{4}-\d{2}-\d{2}$/.test(relative)) return relative;
  const [y, m, d] = base.split('-').map(Number);
  let day = d + (relative?.toLowerCase() === 'yesterday' ? -1 : 0);
  let month = m, year = y;
  const monthDays = [31, year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0) ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
  if (day < 1) { month--; if (!month) { month = 12; year--; } day = monthDays[month - 1]; }
  else if (day > monthDays[month - 1]) { day = 1; month++; if (month > 12) { month = 1; year++; } }
  return `${String(year).padStart(4, '0')}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
}

function proxyToHarness(req, res, userId) {
  const base = process.env.HARNESS_URL || 'http://127.0.0.1:4310';
  const target = new URL(req.url, base);
  const transport = target.protocol === 'https:' ? https : http;
  const headers = { ...req.headers, host: target.host };
  if (userId) headers.authorization = `Bearer e2e-${userId}`;
  delete headers.connection;
  const upstream = transport.request(target, { method: req.method, headers }, upstreamRes => {
    res.writeHead(upstreamRes.statusCode ?? 502, upstreamRes.headers);
    upstreamRes.pipe(res);
  });
  upstream.on('error', () => {
    if (!res.headersSent) apiError(res, 502, 'Local harness unavailable', 'HarnessUnavailable');
    else res.destroy();
  });
  req.pipe(upstream);
}

async function proxyJson(req, userId) {
  return await new Promise(resolve => {
    const chunks = [];
    const base = process.env.HARNESS_URL || 'http://127.0.0.1:4310';
    const target = new URL(req.url, base);
    const transport = target.protocol === 'https:' ? https : http;
    const headers = { ...req.headers, host: target.host, authorization: `Bearer e2e-${userId}` };
    delete headers.connection;
    const upstream = transport.request(target, { method: req.method, headers }, response => {
      response.on('data', chunk => chunks.push(chunk));
      response.on('end', () => {
        let body = null;
        try { body = JSON.parse(Buffer.concat(chunks).toString('utf8')); } catch { /* pass through below */ }
        resolve({ status: response.statusCode ?? 502, headers: response.headers, body, raw: Buffer.concat(chunks) });
      });
    });
    upstream.on('error', () => resolve({ status: 502, headers: {}, body: null, raw: Buffer.alloc(0) }));
    req.pipe(upstream);
  });
}

function sendProxyJson(res, result, mutate) {
  if (!result.body || typeof result.body !== 'object') {
    res.writeHead(result.status, result.headers); res.end(result.raw); return;
  }
  const body = mutate ? mutate(result.body) : result.body;
  json(res, result.status, body);
}

function contentDisposition(headers) {
  const disposition = headers.toString('latin1');
  const name = /(?:^|;)\s*name="([^"]*)"/i.exec(disposition)?.[1];
  const filename = /(?:^|;)\s*filename="([^"]*)"/i.exec(disposition)?.[1];
  return { name, filename };
}

function parseMultipart(buffer, contentType) {
  const boundary = /boundary=(?:"([^"]+)"|([^;]+))/i.exec(contentType)?.slice(1).find(Boolean)?.trim();
  if (!boundary) throw new Error('Multipart boundary is required');
  const delimiter = Buffer.from(`--${boundary}`);
  const fields = {};
  let file = null, sawFile = false, position = buffer.indexOf(delimiter);
  if (position < 0) throw new Error('Malformed multipart body');
  while (position >= 0) {
    position += delimiter.length;
    if (buffer.subarray(position, position + 2).toString() === '--') break;
    if (buffer.subarray(position, position + 2).toString() === '\r\n') position += 2;
    const headerEnd = buffer.indexOf(Buffer.from('\r\n\r\n'), position);
    if (headerEnd < 0) throw new Error('Malformed multipart headers');
    const headers = buffer.subarray(position, headerEnd);
    const disposition = contentDisposition(headers);
    const bodyStart = headerEnd + 4;
    const next = buffer.indexOf(Buffer.concat([Buffer.from('\r\n'), delimiter]), bodyStart);
    if (next < 0) throw new Error('Malformed multipart part');
    const bytes = buffer.subarray(bodyStart, next);
    if (sawFile) throw new Error('All upload fields must appear before the file part');
    if (disposition.filename !== undefined || disposition.name === 'file') {
      sawFile = true;
      file = { bytes: Buffer.from(bytes), filename: disposition.filename ?? 'upload.bin' };
    } else if (disposition.name) fields[disposition.name] = bytes.toString('utf8');
    position = next + 2;
  }
  return { fields, file };
}

async function handleS3(req, res) {
  if (req.headers.authorization) console.warn('[sidecar] Authorization header sent to /__s3; ignored');
  let parsed;
  try { parsed = parseMultipart(await readBuffer(req, 26 * 1024 * 1024), req.headers['content-type'] ?? ''); }
  catch (err) { return apiError(res, 400, err.message, 'InvalidMultipart'); }
  if (!parsed.file) return apiError(res, 400, 'A file part is required', 'InvalidMultipart');
  const key = parsed.fields.key;
  const upload = key && state.uploads.get(key);
  if (!upload) return apiError(res, 400, 'Unknown upload key', 'InvalidUpload');
  for (const [field, value] of Object.entries(upload.fields)) {
    if (parsed.fields[field] !== value) return apiError(res, 400, `Missing or invalid upload field: ${field}`, 'InvalidUpload');
  }
  if (parsed.file.bytes.length > upload.maxBytes) return apiError(res, 400, 'EntityTooLarge', 'EntityTooLarge');
  upload.bytes = parsed.file.bytes;
  upload.filename = parsed.file.filename;
  res.writeHead(204); res.end();
}

async function handleRequest(req, res, server) {
  const url = new URL(req.url, 'http://127.0.0.1');
  const path = url.pathname;
  if (path === '/__sidecar/health' && req.method === 'GET') return json(res, 200, { data: { status: 'ok' } });
  if (path === '/__sidecar/state' && req.method === 'GET') {
    return json(res, 200, { data: { users: [...state.accounts.values()].map(({ id, email, verified, premiumActive }) => ({ id, email, verified, premiumActive })), uploads: [...state.uploads.values()].filter(upload => upload.bytes).length } });
  }
  if (path === '/__sidecar/reset' && req.method === 'POST') { state = newState(); return json(res, 200, { data: { reset: true } }); }
  if (path === '/__s3' && req.method === 'POST') return handleS3(req, res);

  if (path.startsWith('/auth/')) return handleAuth(req, res, path);

  const who = requirePrincipal(req, res);
  if (!who) return;
  const { account } = who;

  if (path === '/user/settings' && req.method === 'GET') {
    if (!account.seeded) {
      if (!account.settings) return apiError(res, 404, 'User settings not found', 'NotFound');
      return json(res, 200, { data: settingsWithPremium(account, account.settings) });
    }
    return proxyJson(req, account.id).then(result => sendProxyJson(res, result, body => {
      if (result.status >= 400) return body;
      if (!body.data || typeof body.data !== 'object') return body;
      return { ...body, data: settingsWithPremium(account, body.data) };
    }));
  }
  if (path === '/user/settings' && req.method === 'PUT') {
    const payload = await readJson(req);
    if (!payload || typeof payload !== 'object') return apiError(res, 400, 'Invalid request payload', 'ValidationError');
    if (account.seeded) {
      const result = await proxyJson(reqFromBody(req, payload), account.id);
      return sendProxyJson(res, result, body => body.data ? { ...body, data: settingsWithPremium(account, body.data) } : body);
    }
    account.settings = { ...defaultSettings(account), ...(account.settings ?? {}), ...payload };
    return json(res, 200, { data: settingsWithPremium(account, account.settings) });
  }
  if (path === '/user/settings/main-income' && req.method === 'PATCH') {
    const payload = await readJson(req);
    if (!payload || typeof payload !== 'object') return apiError(res, 400, 'Invalid request payload', 'ValidationError');
    if (account.seeded) {
      const result = await proxyJson(reqFromBody(req, payload), account.id);
      if (result.status !== 404) return sendProxyJson(res, result);
    }
    account.settings = { ...defaultSettings(account), ...(account.settings ?? {}), mainIncomeTemplateId: payload.mainIncomeTemplateId ?? payload.templateId ?? null };
    return json(res, 200, { data: settingsWithPremium(account, account.settings) });
  }
  if (path === '/user/change-password' && req.method === 'POST') {
    const payload = await readJson(req);
    if (!payload || typeof payload.currentPassword !== 'string' || typeof payload.newPassword !== 'string' || payload.newPassword.length < 8 || payload.newPassword.length > 128) return apiError(res, 400, 'Invalid request payload', 'ValidationError');
    if (payload.currentPassword !== account.password) return apiError(res, 400, 'Current password is incorrect', 'InvalidPassword');
    if (passwordPolicy(payload.newPassword)) return apiError(res, 400, 'New password does not meet requirements', 'WeakPassword');
    account.password = payload.newPassword;
    return json(res, 200, { data: { message: 'Password changed successfully' } });
  }
  if (path === '/user/country-detection' && req.method === 'GET') return json(res, 200, { data: { country: 'US' } });

  if (path === '/categories' && req.method === 'GET') {
    if (!account.seeded) return json(res, 200, { data: localCategories(account) });
    return proxyJson(req, account.id).then(result => sendProxyJson(res, result, body => {
      if (result.status >= 400 || !Array.isArray(body.data)) return body;
      return { ...body, data: categoryOverlays(account, body.data) };
    }));
  }
  if ((path === '/categories' || path === '/categories/batch') && req.method === 'POST') {
    const payload = await readJson(req);
    const list = path.endsWith('/batch') ? payload : [payload];
    if (!Array.isArray(list) || !list.length || list.length > 100 || list.some(item => !item || typeof item.name !== 'string' || !item.name.trim() || typeof item.isIncome !== 'boolean')) return apiError(res, 400, 'Validation failed', 'ValidationError');
    const custom = state.categories.get(account.id) ?? [];
    const created = list.map(item => ({ id: `cat-${randomUUID()}`, userId: account.id, name: item.name.trim(), color: item.color ?? '#818cf8', icon: item.icon ?? null, isIncome: item.isIncome, sortOrder: item.sortOrder ?? custom.length, isSystemSuggestion: item.isSystemSuggestion ?? false }));
    state.categories.set(account.id, [...custom, ...created]);
    return json(res, path.endsWith('/batch') ? 201 : 201, { data: path.endsWith('/batch') ? created : created[0] });
  }
  const categoryMatch = /^\/categories\/([^/]+)$/.exec(path);
  if (categoryMatch && req.method === 'PUT') {
    const categoryId = decodeURIComponent(categoryMatch[1]);
    const payload = await readJson(req);
    const all = account.seeded ? await readSeedCategories(req, account) : localCategories(account);
    if (!all.find(item => item.id === categoryId)) return apiError(res, 404, 'Category not found', 'NotFound');
    const edits = state.categoryEdits.get(account.id) ?? new Map();
    const updated = { ...(all.find(item => item.id === categoryId)), ...(payload ?? {}) };
    edits.set(categoryId, updated); state.categoryEdits.set(account.id, edits);
    return json(res, 200, { data: updated });
  }
  if (categoryMatch && req.method === 'DELETE') {
    const categoryId = decodeURIComponent(categoryMatch[1]);
    const deleted = state.deletedCategories.get(account.id) ?? new Set(); deleted.add(categoryId); state.deletedCategories.set(account.id, deleted);
    state.categories.set(account.id, (state.categories.get(account.id) ?? []).filter(item => item.id !== categoryId));
    return json(res, 200, { data: { message: 'Category deleted successfully' } });
  }

  // Push registration: the harness has no device-token route, so acknowledge it (nothing is stored).
  if (path === '/user/device-token' && (req.method === 'POST' || req.method === 'DELETE')) {
    await readJson(req);
    return json(res, 200, { data: { registered: req.method === "POST" } });
  }
  if (path === '/transactions/upload-url' && req.method === 'POST') return uploadUrl(req, res, account, server);
  if (path === '/statements/analyze-spreadsheet' && req.method === 'POST') return analyzeStatement(req, res, account);
  if (path === '/statements/preview-spreadsheet' && req.method === 'POST') return previewStatement(req, res, account);
  if (path === '/statements/import-spreadsheet' && req.method === 'POST') return importStatement(req, res, account);
  if (path === '/transactions/analyze-audio' && req.method === 'POST') return analyzeAudio(req, res, account);
  if (path === '/transactions/analyze-image' && req.method === 'POST') return analyzeImage(req, res, account);
  const analysisMatch = /^\/transactions\/analysis\/([^/]+)$/.exec(path);
  if (analysisMatch && req.method === 'GET') return analysisStatus(res, decodeURIComponent(analysisMatch[1]), account);

  // The harness has no analysis log, so a bulk save that references a sidecar analysisId would
  // fail there; drop the link (the real server only uses it for audit) and forward the rest.
  if (path === '/transactions/bulk' && req.method === 'POST') {
    const payload = await readJson(req);
    if (payload && !Array.isArray(payload) && 'analysisId' in payload) delete payload.analysisId;
    return sendProxyJson(res, await proxyJson(reqFromBody(req, payload), account.id));
  }

  return proxyToHarness(req, res, account.id);
}

function reqFromBody(req, body) {
  // Preserve the original path and headers while substituting the consumed JSON body.
  const shim = new ReadableRequestShim(req, Buffer.from(JSON.stringify(body)));
  return shim;
}

class ReadableRequestShim {
  constructor(original, body) {
    this.url = original.url; this.method = original.method;
    this.headers = { ...original.headers, 'content-length': String(body.length), 'content-type': 'application/json' };
    this.body = body;
  }
  pipe(destination) { destination.end(this.body); return destination; }
}

async function readSeedCategories(req, account) {
  const result = await proxyJson(emptyRequest('/categories'), account.id);
  return result.status === 200 && Array.isArray(result.body?.data) ? categoryOverlays(account, result.body.data) : [];
}

function emptyRequest(url) {
  return { url, method: 'GET', headers: {}, pipe(destination) { destination.end(); return destination; } };
}

async function handleAuth(req, res, path) {
  const payload = await readJson(req);
  if (!payload || typeof payload !== 'object') return apiError(res, 400, 'Request body must be valid JSON', 'InvalidJson');
  const email = typeof payload.email === 'string' ? payload.email.trim().toLowerCase() : '';
  if (path === '/auth/register' && req.method === 'POST') {
    if (!validateEmail(email) || typeof payload.password !== 'string' || payload.password.length < 8 || payload.password.length > 128 || (payload.timezone !== undefined && (typeof payload.timezone !== 'string' || !payload.timezone))) return apiError(res, 400, 'Invalid registration payload', 'ValidationError');
    const policy = passwordPolicy(payload.password);
    if (policy && policy !== 'Invalid registration payload') return apiError(res, 400, policy, 'InvalidPassword');
    if (state.accounts.has(email)) return apiError(res, 409, 'A user with this email already exists', 'UserAlreadyExists');
    const account = { id: randomUUID(), email, password: payload.password, verified: false, premiumActive: false, seeded: false, settings: null, forgotCode: null, verificationCode: '123456' };
    state.accounts.set(email, account);
    return json(res, 201, { data: { userId: account.id, needsConfirmation: true } });
  }
  if (path === '/auth/confirm-registration' && req.method === 'POST') {
    const account = state.accounts.get(email);
    if (!account || account.seeded) return apiError(res, 400, 'Invalid verification code', 'CodeMismatch');
    if (payload.code !== '123456') return apiError(res, 400, 'Invalid verification code', 'CodeMismatch');
    account.verified = true;
    return json(res, 200, { data: { message: 'Registration confirmed successfully' } });
  }
  if (path === '/auth/resend-code' && req.method === 'POST') {
    if (!validateEmail(email)) return apiError(res, 400, 'Invalid request. Please provide a valid email.', 'ValidationError');
    return json(res, 200, { data: { message: 'Confirmation code sent if account exists' } });
  }
  if (path === '/auth/login' && req.method === 'POST') {
    if (!validateEmail(email) || typeof payload.password !== 'string' || !payload.password || payload.password.length > 128) return apiError(res, 400, 'Invalid login payload', 'ValidationError');
    const account = state.accounts.get(email);
    if (!account || payload.password !== account.password) return apiError(res, 401, 'Invalid email or password', 'Unauthorized');
    if (!account.verified) return apiError(res, 403, 'Please verify your email address before signing in', 'UserNotConfirmed');
    return json(res, 200, { data: issueTokens(account) });
  }
  if (path === '/auth/refresh' && req.method === 'POST') {
    const accountEmail = state.refreshTokens.get(payload.refreshToken);
    const account = accountEmail && state.accounts.get(accountEmail);
    if (!account || !account.verified) return apiError(res, 401, 'Invalid or expired refresh token', 'Unauthorized');
    const tokens = issueTokens(account);
    state.refreshTokens.delete(tokens.refreshToken); // Refresh does not rotate the supplied token.
    state.refreshTokens.set(payload.refreshToken, account.email);
    const { accessToken, idToken } = tokens;
    return json(res, 200, { data: { accessToken, idToken } });
  }
  if (path === '/auth/logout' && req.method === 'POST') {
    state.refreshTokens.delete(payload.refreshToken);
    return json(res, 200, { data: { message: 'Successfully logged out' } });
  }
  if (path === '/auth/forgot-password' && req.method === 'POST') {
    if (!validateEmail(email)) return apiError(res, 400, 'Invalid email', 'ValidationError');
    const account = state.accounts.get(email);
    if (account) account.forgotCode = '123456';
    return json(res, 200, { data: { message: 'Reset code sent if email exists' } });
  }
  if (path === '/auth/confirm-password' && req.method === 'POST') {
    const account = state.accounts.get(email);
    if (!account || account.forgotCode !== payload.code) return apiError(res, 400, 'Invalid confirmation code', 'CodeMismatch');
    if (typeof payload.password !== 'string' || payload.password.length < 8 || payload.password.length > 128) return apiError(res, 400, 'Invalid payload', 'ValidationError');
    const policy = passwordPolicy(payload.password);
    if (policy && policy !== 'Invalid registration payload') return apiError(res, 400, policy, 'InvalidPassword');
    account.password = payload.password; account.forgotCode = null;
    return json(res, 200, { data: { message: 'Password has been reset successfully' } });
  }
  return apiError(res, 404, 'Not found', 'NotFound');
}

async function uploadUrl(req, res, account, server) {
  const body = await readJson(req);
  const type = body?.documentType ?? 'receipt';
  const mimeType = typeof body?.mimeType === 'string' ? body.mimeType.split(';')[0].trim().toLowerCase() : '';
  if (!MIME_TYPES[type]) return apiError(res, 400, 'Validation failed', 'ValidationError');
  if (!MIME_TYPES[type].includes(mimeType)) return json(res, 400, { message: `Unsupported file type for ${type}. Allowed: ${MIME_TYPES[type].join(', ')}` });
  const extByMime = { 'text/csv': 'csv', 'application/pdf': 'pdf', 'image/jpeg': 'jpg', 'audio/mp4': 'm4a', 'audio/mpeg': 'mp3', 'text/plain': 'txt' };
  const key = `users/${account.id}/${type}/${randomUUID()}.${extByMime[mimeType] ?? 'bin'}`;
  const fields = { key, 'Content-Type': body.mimeType };
  state.uploads.set(key, { key, userId: account.id, type, mimeType, maxBytes: MAX_BYTES[type], fields, bytes: null, filename: null });
  const address = server.address();
  const port = address && typeof address === 'object' ? address.port : (Number(process.env.SIDECAR_PORT) || 4311);
  return json(res, 200, { data: { uploadUrl: `http://127.0.0.1:${port}/__s3`, fields, documentKey: key, expiresIn: 300, maxFileSizeBytes: MAX_BYTES[type] } });
}

async function analyzeStatement(req, res, account) {
  const body = await readJson(req);
  if (typeof body?.filename !== 'string' || typeof body.documentKey !== 'string') return apiError(res, 400, 'Validation failed', 'ValidationError');
  if (!isOwnedKey(body.documentKey, account.id)) return apiError(res, 403, 'Access denied to this document', 'Forbidden');
  if (/\.xlsx?$/i.test(body.filename)) return apiError(res, 400, 'XLSX is not supported by the local sidecar', 'UnsupportedFile');
  const upload = state.uploads.get(body.documentKey);
  if (!upload?.bytes) return apiError(res, 404, 'Uploaded document not found', 'NotFound');
  if (!/\.csv$/i.test(body.filename) && upload.mimeType !== 'text/csv') return apiError(res, 400, 'Only CSV statements are supported by the local sidecar', 'UnsupportedFile');
  const rows = parseCsv(upload.bytes);
  if (rows.length < 2) return apiError(res, 400, 'Spreadsheet must contain a header and at least one row', 'ValidationError');
  const headers = (rows[0] ?? []).map(item => item.trim());
  const normalized = headers.map(value => value.toLowerCase().replace(/[^a-z]/g, ''));
  const find = names => { const idx = normalized.findIndex(name => names.includes(name)); return idx >= 0 ? headers[idx] : undefined; };
  const dateColumn = find(['date', 'transactiondate', 'posteddate']);
  const descriptionColumn = find(['description', 'memo', 'details', 'merchant', 'payee']);
  const amountColumn = find(['amount', 'value', 'transactionamount']);
  const debitColumn = find(['debit', 'withdrawal', 'withdrawals']);
  const creditColumn = find(['credit', 'deposit', 'deposits']);
  if (!dateColumn || !descriptionColumn || (!amountColumn && !(debitColumn && creditColumn))) return apiError(res, 400, 'Could not identify date, description, and amount columns', 'ValidationError');
  const dateFormat = dateFormatFor(rows[1]?.[headers.indexOf(dateColumn)]);
  const mapping = { dateColumn, dateFormat, descriptionColumn, ...(amountColumn ? { amountColumn } : { debitColumn, creditColumn }), amountIsNegativeForDebit: true, confidence: 0.9 };
  return json(res, 200, { data: { mapping, headers, preview: rows.slice(1, 6).map(row => headers.map((_, index) => row[index] ?? '')) } });
}

async function previewStatement(req, res, account) {
  const body = await readJson(req);
  if (typeof body?.filename !== 'string' || typeof body.documentKey !== 'string' || !body.mapping || typeof body.mapping.confidence !== 'number') return apiError(res, 400, 'Validation failed', 'ValidationError');
  if (!isOwnedKey(body.documentKey, account.id)) return apiError(res, 403, 'Access denied to this document', 'Forbidden');
  if (/\.xlsx?$/i.test(body.filename)) return apiError(res, 400, 'XLSX is not supported by the local sidecar', 'UnsupportedFile');
  const upload = state.uploads.get(body.documentKey);
  if (!upload?.bytes) return apiError(res, 404, 'Uploaded document not found', 'NotFound');
  const transactions = statementRows(upload, body.mapping);
  const categories = await categoriesFor(account, req);
  return json(res, 200, { data: { transactions, categories } });
}

async function categoriesFor(account, req) {
  if (!account.seeded) return localCategories(account);
  return readSeedCategories(req, account);
}

async function importStatement(req, res, account) {
  const body = await readJson(req);
  if (!Array.isArray(body?.transactions) || !body.transactions.length) return apiError(res, 400, 'Validation failed', 'ValidationError');
  const idempotencyKey = req.headers['idempotency-key'] ?? req.headers['x-idempotency-key'];
  const categories = await categoriesFor(account, req);
  const seen = idempotencyKey ? (state.importRows.get(`${account.id}:${idempotencyKey}`) ?? new Set()) : new Set();
  const prepared = [];
  for (let index = 0; index < body.transactions.length; index++) {
    const row = body.transactions[index];
    if (!row || typeof row.date !== 'string' || typeof row.description !== 'string' || !Number.isFinite(row.amount)) return apiError(res, 400, 'Validation failed', 'ValidationError');
    const categoryValue = typeof row.categoryId === 'string' ? row.categoryId : null;
    const category = categoryValue && categories.find(item => item.id === categoryValue || item.name.toLowerCase() === categoryValue.toLowerCase());
    const amount = Math.abs(row.amount);
    const item = { amount, date: row.date, description: row.description, ...(category ? { categoryId: category.id } : {}), idempotencyKeyId: `row-${index}` };
    const fingerprint = createHash('sha256').update(JSON.stringify(item)).digest('hex');
    if (!seen.has(fingerprint)) prepared.push({ item, fingerprint });
  }
  let created = 0, skipped = body.transactions.length - prepared.length;
  if (prepared.length) {
    for (let offset = 0; offset < prepared.length; offset += 100) {
      const chunk = prepared.slice(offset, offset + 100);
      const forwardBody = { transactions: chunk.map(({ item }) => item), source: 'import' };
      const result = await sendBulkToHarness(req, account.id, forwardBody, idempotencyKey);
      let forwarded = result;
      // The current harness bulk handler accepts manual/voice/text only. Keep
      // the requested import source for compatible harnesses, and retry the
      // same deterministic rows as manual if its schema rejects that source.
      if (result.status === 400 && result.body?.error === 'ValidationError') {
        console.warn('[sidecar] harness bulk route rejected source=import; retrying with source=manual');
        forwarded = await sendBulkToHarness(req, account.id, { ...forwardBody, source: 'manual' }, idempotencyKey);
      }
      if (forwarded.status < 200 || forwarded.status >= 300) return sendProxyJson(res, forwarded);
      const data = forwarded.body?.data ?? {};
      const results = Array.isArray(data.results) ? data.results : [];
      const failed = Array.isArray(data.failed) ? data.failed.length : 0;
      const duplicates = Number(data.duplicates ?? 0);
      created += results.length;
      skipped += duplicates;
      if (failed) skipped += failed;
      for (const entry of chunk) seen.add(entry.fingerprint);
    }
    if (idempotencyKey) state.importRows.set(`${account.id}:${idempotencyKey}`, seen);
  }
  const stats = { created, updated: 0, skipped };
  return json(res, 200, { data: { message: `Imported: ${created} new. Skipped duplicates: ${skipped}.`, count: created, stats } });
}

async function sendBulkToHarness(req, userId, payload, idempotencyKey) {
  const base = process.env.HARNESS_URL || 'http://127.0.0.1:4310';
  const target = new URL('/transactions/bulk', base);
  const transport = target.protocol === 'https:' ? https : http;
  const body = Buffer.from(JSON.stringify(payload));
  return await new Promise(resolve => {
    const headers = { 'content-type': 'application/json', 'content-length': String(body.length), authorization: `Bearer e2e-${userId}` };
    if (idempotencyKey) headers['idempotency-key'] = idempotencyKey;
    const upstream = transport.request(target, { method: 'POST', headers }, response => {
      const chunks = [];
      response.on('data', chunk => chunks.push(chunk));
      response.on('end', () => {
        const raw = Buffer.concat(chunks);
        let parsed = null; try { parsed = JSON.parse(raw.toString('utf8')); } catch { /* raw proxy error below */ }
        resolve({ status: response.statusCode ?? 502, headers: response.headers, body: parsed, raw });
      });
    });
    upstream.on('error', () => resolve({ status: 502, headers: {}, body: null, raw: Buffer.alloc(0) }));
    upstream.end(body);
  });
}

async function analyzeAudio(req, res, account) {
  const body = await readJson(req);
  if (body?.action === 'close-session') {
    if (typeof body.analysisId !== 'string' || !['restarted', 'abandoned'].includes(body.outcome)) return apiError(res, 400, 'Invalid capture session action', 'BadRequest');
    const analysis = state.analyses.get(body.analysisId);
    if (!analysis || analysis.userId !== account.id || analysis.kind !== 'audio') return apiError(res, 404, 'Capture session not found', 'NotFound');
    const rootId = analysis.captureSessionId ?? analysis.id;
    const root = state.analyses.get(rootId);
    if (root) root.outcome = body.outcome;
    return json(res, 200, { data: { captureSessionId: rootId, outcome: root?.outcome ?? body.outcome } });
  }
  const mode = body?.mode ?? 'auto';
  const inputType = body?.inputType ?? 'voice';
  const transcript = typeof body?.transcript === 'string' ? body.transcript.trim() : '';
  const validModes = ['audio', 'transcribe', 'audio-transcript', 'auto'];
  if (!validModes.includes(mode) || !['voice', 'typed'].includes(inputType) || (body?.documentKey !== undefined && typeof body.documentKey !== 'string') || (body?.documentKey === undefined && inputType !== 'typed') || (body?.parentAnalysisId && inputType !== 'typed') || (inputType === 'typed' && (!transcript || (mode !== 'auto' && mode !== 'audio-transcript'))) || (mode === 'transcribe' && (inputType === 'typed' || transcript)) || (mode === 'audio-transcript' && !transcript)) return apiError(res, 400, 'Invalid audio analysis input', 'BadRequest');
  const documentKey = body.documentKey ?? `users/${account.id}/typed/${randomUUID()}.txt`;
  if (!isOwnedKey(documentKey, account.id)) return apiError(res, 403, 'Access denied to this document', 'Forbidden');
  if (process.env.QUOTA_EXHAUSTED === '1' && inputType === 'voice') {
    const next = new Date(); next.setUTCMonth(next.getUTCMonth() + 1, 1); next.setUTCHours(0, 0, 0, 0);
    return errorOnly(res, 403, 'QuotaExhausted', { quotaType: 'voiceEntries', limit: 20, used: 20, remaining: 0, resetDate: next.toISOString() });
  }
  if (body.parentAnalysisId) {
    const parent = state.analyses.get(body.parentAnalysisId);
    if (!parent || parent.userId !== account.id || parent.polls < 2 || !parent.transcript) return apiError(res, 404, 'Analysis not found', 'NotFound');
  }
  const analysisId = randomUUID();
  const isTranscribe = mode === 'transcribe';
  const resolvedTranscript = isTranscribe ? 'I spent 12.50 on lunch yesterday and 30 on gas today' : transcript;
  const items = isTranscribe ? [] : parseCapture(resolvedTranscript || 'I spent 12.50 on lunch today', body.referenceDate, body.userCurrency);
  const captureSessionId = body.parentAnalysisId ? state.analyses.get(body.parentAnalysisId).captureSessionId : analysisId;
  state.analyses.set(analysisId, { id: analysisId, userId: account.id, kind: 'audio', polls: 0, documentKey, transcript: resolvedTranscript || null, items, captureSessionId, parentAnalysisId: body.parentAnalysisId ?? null, mode: isTranscribe ? 'transcribe' : 'audio-transcript', inputType });
  return json(res, 202, { data: { analysisId, documentKey, status: 'pending' } });
}

async function analyzeImage(req, res, account) {
  const body = await readJson(req);
  if (typeof body?.documentKey !== 'string' || !isOwnedKey(body.documentKey, account.id)) return apiError(res, 400, 'Invalid request payload', 'ValidationError');
  const upload = state.uploads.get(body.documentKey);
  if (!upload?.bytes) return apiError(res, 404, 'Uploaded document not found', 'NotFound');
  const analysisId = randomUUID();
  state.analyses.set(analysisId, { id: analysisId, userId: account.id, kind: 'receipt', polls: 0, documentKey: body.documentKey, result: { amount: 12.5, description: 'Corner Shop purchase', date: '2026-10-01', merchantName: 'Fake Market', currency: 'CAD', categoryId: null, categoryHint: 'Groceries' } });
  return json(res, 202, { data: { analysisId, documentKey: body.documentKey, status: 'pending' } });
}

function analysisStatus(res, id, account) {
  const analysis = state.analyses.get(id);
  if (!analysis || analysis.userId !== account.id) return apiError(res, 404, 'Analysis not found', 'NotFound');
  analysis.polls++;
  if (analysis.polls === 1) return json(res, 200, { data: { analysisId: id, status: 'processing', ...(analysis.parentAnalysisId ? { parentAnalysisId: analysis.parentAnalysisId } : {}), ...(analysis.captureSessionId ? { captureSessionId: analysis.captureSessionId } : {}) } });
  if (analysis.kind === 'receipt') return json(res, 200, { data: { analysisId: id, status: 'success', result: analysis.result } });
  return json(res, 200, { data: { analysisId: id, status: 'success', items: analysis.items, transcript: analysis.transcript, captureSessionId: analysis.captureSessionId, ...(analysis.parentAnalysisId ? { parentAnalysisId: analysis.parentAnalysisId } : {}) } });
}

let state = newState();

export function createSidecarServer() {
  let server;
  server = http.createServer((req, res) => {
    res.on('finish', () => console.log(`${req.method} ${new URL(req.url, 'http://127.0.0.1').pathname} ${res.statusCode}`));
    Promise.resolve(handleRequest(req, res, server)).catch(err => {
      if (!res.headersSent) apiError(res, err.status ?? 500, err.status === 413 ? 'Request body too large' : 'Sidecar request failed', err.status === 413 ? 'PayloadTooLarge' : 'InternalError');
      else res.destroy();
    });
  });
  return server;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const port = Number(process.env.SIDECAR_PORT || 4311);
  const server = createSidecarServer();
  server.listen(port, '127.0.0.1', () => {
    const address = server.address();
    console.log(`sidecar listening http://127.0.0.1:${address.port}`);
  });
}
