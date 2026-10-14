import http from 'node:http';
import https from 'node:https';
import { randomUUID, createHash } from 'node:crypto';
import { gzipSync } from 'node:zlib';
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
  // Scan documents are images or spreadsheets; PDFs are rendered to JPEG pages on the device first.
  scan: ['image/jpeg', 'image/jpg', 'image/png', 'image/webp', 'text/csv', 'application/vnd.ms-excel', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'],
};
const MAX_BYTES = { receipt: 10 * 1024 * 1024, audio: 25 * 1024 * 1024, bank_statement: 25 * 1024 * 1024, scan: 10 * 1024 * 1024 };
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
    rules: new Map(),
    scans: new Map(),
    scanSeq: 0,
    scanQuotaExhausted: false,
    exportFail: false,
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

// ---- Rules (merchant mappings) -------------------------------------------------------------
// In memory per harness identity. The list also holds one `ai` mapping so clients can prove they
// show `source == "user"` rules only.
const MIN_CONTAINS_KEY_LENGTH = 3;
const RULE_PAST_CAP = 1000;

function validationFailure(res, field, message) {
  return json(res, 400, { message: 'Validation failed', error: 'ValidationError', details: [{ field, message }] });
}

function normalizeKey(value) {
  return String(value ?? '').toLowerCase().replace(/[^\p{L}\p{N}]/gu, '');
}

function validRegex(pattern) {
  try { new RegExp(pattern, 'i'); return true; } catch { return false; }
}

function rulesFor(account) {
  let list = state.rules.get(account.id);
  if (!list) {
    const now = new Date().toISOString();
    list = [{ id: randomUUID(), userId: account.id, rawPattern: 'AMZN MKTP', cleanMerchantName: 'Amazon', confidence: 0.8, source: 'ai', isRegex: false, action: 'categorize', createdAt: now, updatedAt: now }];
    state.rules.set(account.id, list);
  }
  return list;
}

function userRules(account) {
  return rulesFor(account).filter(rule => rule.source === 'user');
}

function ruleMatches(rule, ...texts) {
  const key = normalizeKey(rule.rawPattern);
  return texts.some(text => {
    if (!text) return false;
    if (rule.isRegex) { try { return new RegExp(rule.rawPattern, 'i').test(text); } catch { return false; } }
    const target = normalizeKey(text);
    return key.length > 0 && (target === key || (key.length >= MIN_CONTAINS_KEY_LENGTH && target.includes(key)));
  });
}

async function checkRuleCategory(res, account, req, categoryId) {
  const category = (await categoriesFor(account, req)).find(item => item.id === categoryId);
  if (!category) { apiError(res, 400, `Category ${categoryId} does not exist`, 'CategoryNotFound'); return null; }
  return category;
}

function parseRuleFields(res, body, { partial }) {
  const out = {};
  const bad = (field, message) => { validationFailure(res, field, message); return null; };
  if (!body || typeof body !== 'object' || Array.isArray(body)) return bad('body', 'Request body must be a JSON object');
  if (body.rawPattern !== undefined || !partial) {
    const value = typeof body.rawPattern === 'string' ? body.rawPattern.trim() : '';
    if (!value || value.length > 200) return bad('rawPattern', 'rawPattern must be 1-200 characters');
    out.rawPattern = value;
  }
  if (body.cleanMerchantName !== undefined) {
    const value = typeof body.cleanMerchantName === 'string' ? body.cleanMerchantName.trim() : '';
    if (!value || value.length > 200) return bad('cleanMerchantName', 'cleanMerchantName must be 1-200 characters');
    out.cleanMerchantName = value;
  }
  if (body.categoryId !== undefined && body.categoryId !== null && (typeof body.categoryId !== 'string' || !body.categoryId)) return bad('categoryId', 'categoryId must be a non-empty string');
  if (body.categoryId !== undefined) out.categoryId = body.categoryId;
  if (body.action !== undefined && !['categorize', 'skip'].includes(body.action)) return bad('action', 'action must be categorize or skip');
  if (body.action !== undefined) out.action = body.action;
  if (body.isRegex !== undefined && typeof body.isRegex !== 'boolean') return bad('isRegex', 'isRegex must be a boolean');
  if (body.isRegex !== undefined) out.isRegex = body.isRegex;
  if (partial && !Object.keys(out).length) return bad('body', 'At least one field must be provided');
  return out;
}

/** Past transactions a rule may move: the harness user's last 24 months, or null if the harness cannot list them. */
async function pastTransactions(account) {
  const now = new Date();
  const fmt = date => date.toISOString().slice(0, 10);
  const start = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() - 24, now.getUTCDate()));
  const out = [];
  let cursor;
  for (let page = 0; page < 10; page++) {
    const query = new URLSearchParams({ startDate: fmt(start), endDate: fmt(now), limit: '100' });
    if (cursor) query.set('cursor', cursor);
    const result = await proxyJson(emptyRequest(`/transactions?${query}`), account.id);
    const data = result.body?.data;
    const list = Array.isArray(data) ? data : data?.transactions;
    if (result.status !== 200 || !Array.isArray(list)) return null;
    out.push(...list);
    cursor = data?.pagination?.nextCursor;
    if (!cursor) break;
  }
  return out.slice(0, RULE_PAST_CAP);
}

/**
 * Transactions a categorize rule for `merchant` would change: description contains the merchant,
 * not already in the category, not from a recurring template, and on the same income/expense side.
 * Returns null when the harness cannot list transactions.
 */
async function ruleTargets(account, req, merchant, categoryId, isRegex = false) {
  const key = normalizeKey(merchant);
  if (!isRegex && key.length < MIN_CONTAINS_KEY_LENGTH) return [];
  const transactions = await pastTransactions(account);
  if (!transactions) return null;
  const categories = await categoriesFor(account, req);
  const target = categories.find(item => item.id === categoryId);
  const incomeById = new Map(categories.map(item => [item.id, item.isIncome]));
  const rule = { rawPattern: merchant, isRegex };
  return transactions.filter(tx => !tx.templateId && tx.categoryId !== categoryId && incomeById.has(tx.categoryId)
    && (!target || incomeById.get(tx.categoryId) === target.isIncome) && ruleMatches(rule, tx.description));
}

async function merchantMappings(req, res, account, id, action) {
  const list = rulesFor(account);
  const now = new Date().toISOString();
  if (req.method === 'GET' && !id) return json(res, 200, { data: list });

  if (req.method === 'GET' && id === 'match-count') {
    const url = new URL(req.url, 'http://local');
    const merchant = url.searchParams.get('merchant')?.trim() ?? '';
    const categoryId = url.searchParams.get('categoryId')?.trim() ?? '';
    if (!merchant) return validationFailure(res, 'merchant', 'merchant is required');
    if (!categoryId) return validationFailure(res, 'categoryId', 'categoryId is required');
    if (!await checkRuleCategory(res, account, req, categoryId)) return;
    const targets = await ruleTargets(account, req, merchant, categoryId);
    // Harness unavailable for listing: a fixed answer keeps the screen usable.
    return json(res, 200, { data: { count: targets ? targets.length : 3 } });
  }

  if (req.method === 'POST' && id && action === 'apply') {
    const rule = list.find(item => item.id === id && item.source === 'user');
    if (!rule) return apiError(res, 404, 'Merchant mapping not found', 'NotFound');
    if (rule.action === 'skip') return apiError(res, 409, 'Skip rules do not change past transactions', 'SkipRuleCannotApply');
    if (!rule.categoryId) return validationFailure(res, 'categoryId', 'The rule has no category to apply');
    if (!await checkRuleCategory(res, account, req, rule.categoryId)) return;
    const targets = await ruleTargets(account, req, rule.rawPattern, rule.categoryId, rule.isRegex);
    if (!targets) return json(res, 200, { data: { updated: 3 } });
    let updated = 0;
    for (const tx of targets) {
      const result = await proxyJson(jsonRequest('PUT', `/transactions/${encodeURIComponent(tx.id)}`, { categoryId: rule.categoryId }), account.id);
      if (result.status >= 200 && result.status < 300) updated++;
    }
    return json(res, 200, { data: { updated } });
  }

  if (req.method === 'DELETE' && id && !action) {
    const index = list.findIndex(m => m.id === id && m.source === 'user');
    if (index >= 0) list.splice(index, 1);
    res.writeHead(204); return res.end();
  }

  if (req.method === 'POST' && !id) {
    const fields = parseRuleFields(res, await readJson(req), { partial: false });
    if (!fields) return;
    const ruleAction = fields.action ?? 'categorize';
    const isRegex = fields.isRegex ?? false;
    if (!normalizeKey(fields.rawPattern)) return validationFailure(res, 'rawPattern', 'rawPattern needs at least one letter or digit');
    if (isRegex && !validRegex(fields.rawPattern)) return validationFailure(res, 'rawPattern', 'rawPattern is not a valid regex');
    if (ruleAction === 'categorize' && !fields.categoryId) return validationFailure(res, 'categoryId', 'categoryId is required for a categorize rule');
    if (ruleAction === 'categorize' && !await checkRuleCategory(res, account, req, fields.categoryId)) return;
    const values = { rawPattern: fields.rawPattern, cleanMerchantName: fields.cleanMerchantName ?? fields.rawPattern, isRegex, action: ruleAction, ...(ruleAction === 'categorize' ? { categoryId: fields.categoryId } : {}) };
    const existing = userRules(account).find(rule => normalizeKey(rule.rawPattern) === normalizeKey(fields.rawPattern));
    if (existing) {
      delete existing.categoryId;
      Object.assign(existing, values, { updatedAt: now });
      return json(res, 200, { data: existing });
    }
    const created = { id: randomUUID(), userId: account.id, ...values, confidence: 1, source: 'user', createdAt: now, updatedAt: now };
    list.push(created);
    return json(res, 201, { data: created });
  }

  if (req.method === 'PUT' && id && !action) {
    const fields = parseRuleFields(res, await readJson(req), { partial: true });
    if (!fields) return;
    const existing = list.find(m => m.id === id);
    if (!existing) return apiError(res, 404, 'Merchant mapping not found', 'NotFound');
    const ruleAction = fields.action ?? existing.action;
    const isRegex = fields.isRegex ?? existing.isRegex;
    const rawPattern = fields.rawPattern ?? existing.rawPattern;
    const categoryId = ruleAction === 'skip' ? undefined : fields.categoryId === undefined ? existing.categoryId : (fields.categoryId ?? undefined);
    if (!normalizeKey(rawPattern)) return validationFailure(res, 'rawPattern', 'rawPattern needs at least one letter or digit');
    if (isRegex && !validRegex(rawPattern)) return validationFailure(res, 'rawPattern', 'rawPattern is not a valid regex');
    if (ruleAction === 'categorize' && !categoryId) return validationFailure(res, 'categoryId', 'categoryId is required for a categorize rule');
    if (ruleAction === 'categorize' && categoryId !== existing.categoryId && !await checkRuleCategory(res, account, req, categoryId)) return;
    if (existing.source === 'user' && fields.rawPattern !== undefined && normalizeKey(rawPattern) !== normalizeKey(existing.rawPattern)
      && userRules(account).some(rule => rule.id !== id && normalizeKey(rule.rawPattern) === normalizeKey(rawPattern))) {
      return apiError(res, 409, 'A rule for that merchant already exists', 'RuleExists');
    }
    delete existing.categoryId;
    Object.assign(existing, { rawPattern, cleanMerchantName: fields.cleanMerchantName ?? existing.cleanMerchantName, action: ruleAction, isRegex, ...(categoryId ? { categoryId } : {}), updatedAt: now });
    return json(res, 200, { data: existing });
  }
  return apiError(res, 405, 'Method not allowed', 'MethodNotAllowed');
}

// ---- Account export ("Download my data") -----------------------------------------------------
async function accountExport(req, res, account) {
  if (state.exportFail) return json(res, 500, { error: 'ExportFailed', message: 'Could not build your data file. Please try again.' });
  const now = new Date().toISOString();
  const categories = await categoriesFor(account, req);
  const doc = {
    format: 'spenditos-export', version: 1, exportedAt: now,
    account: { userId: account.id, email: account.email, currency: account.settings?.currency ?? 'USD', timezone: account.settings?.timezone ?? 'UTC', createdAt: '2026-01-01T00:00:00.000Z' },
    categories,
    incomeAndBills: [
      { id: 'sidecar-income-1', name: 'Paycheque', kind: 'income', amount: 2500, frequency: 'biweekly', categoryId: 'sidecar-income-salary' },
      { id: 'sidecar-bill-1', name: 'Rent', kind: 'bill', amount: 1200, frequency: 'monthly', categoryId: 'sidecar-expense-other' },
    ],
    rules: userRules(account),
    transactions: [
      { id: 'sidecar-tx-1', amount: 12.5, date: '2026-10-01', description: 'Corner Shop purchase', categoryId: 'sidecar-expense-food', transactionType: 'expense' },
      { id: 'sidecar-tx-2', amount: 2500, date: '2026-10-02', description: 'Payroll', categoryId: 'sidecar-income-salary', transactionType: 'income' },
    ],
  };
  const headers = { 'Content-Type': 'application/json; charset=utf-8', 'Content-Disposition': `attachment; filename="spenditos-data-${now.slice(0, 10)}.json"`, 'Cache-Control': 'no-store' };
  let body = Buffer.from(JSON.stringify(doc));
  if (/\bgzip\b/i.test(String(req.headers['accept-encoding'] ?? ''))) { body = gzipSync(body); headers['Content-Encoding'] = 'gzip'; }
  headers['Content-Length'] = String(body.length);
  res.writeHead(200, headers);
  res.end(body);
}

// ---- Scans -----------------------------------------------------------------------------------
// The harness has no scan worker, so scans live in the sidecar. Per account (email), in memory.
// Polling GET /scans/{id}: first two reads report `processing`, the third is `ready`.
const SCAN_STATUSES = ['processing', 'ready', 'failed', 'saved', 'discarded'];
const PDF_GROUP_ID = /^[A-Za-z0-9_-]{1,64}$/;

function scansFor(account) {
  return state.scans.get(account.email) ?? state.scans.set(account.email, new Map()).get(account.email);
}

function addDays(date, days) {
  const [y, m, d] = date.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d + days)).toISOString().slice(0, 10);
}

function validateScanBody(body) {
  const fail = (field, message) => ({ field, message });
  if (!body || typeof body !== 'object' || Array.isArray(body)) return fail('body', 'Request body must be a JSON object');
  if (!Array.isArray(body.documents) || body.documents.length < 1 || body.documents.length > 60) return fail('documents', 'documents must contain 1-60 items');
  for (let i = 0; i < body.documents.length; i++) {
    const doc = body.documents[i];
    if (!doc || typeof doc !== 'object') return fail(`documents[${i}]`, 'document must be an object');
    if (typeof doc.documentKey !== 'string' || !doc.documentKey) return fail(`documents[${i}].documentKey`, 'documentKey is required');
    if (!['image', 'spreadsheet'].includes(doc.kind)) return fail(`documents[${i}].kind`, 'kind must be image or spreadsheet');
    if (doc.filename !== undefined && (typeof doc.filename !== 'string' || doc.filename.length > 255)) return fail(`documents[${i}].filename`, 'filename must be at most 255 characters');
    if (doc.pdfGroupId !== undefined) {
      if (typeof doc.pdfGroupId !== 'string' || !PDF_GROUP_ID.test(doc.pdfGroupId)) return fail(`documents[${i}].pdfGroupId`, 'pdfGroupId must be 1-64 characters of A-Z a-z 0-9 _ -');
      if (doc.kind !== 'image') return fail(`documents[${i}].pdfGroupId`, 'pdfGroupId is only allowed on images');
    }
  }
  if (body.referenceDate !== undefined && !/^\d{4}-\d{2}-\d{2}$/.test(String(body.referenceDate))) return fail('referenceDate', 'referenceDate must be YYYY-MM-DD');
  if (body.locale !== undefined && typeof body.locale !== 'string') return fail('locale', 'locale must be a string');
  if (body.userCurrency !== undefined && typeof body.userCurrency !== 'string') return fail('userCurrency', 'userCurrency must be a string');
  if (body.sourceLabel !== undefined && (typeof body.sourceLabel !== 'string' || body.sourceLabel.length > 80)) return fail('sourceLabel', 'sourceLabel must be at most 80 characters');
  return null;
}

/** 1 credit per ungrouped document; pages sharing a pdfGroupId cost 1 per started block of 10. */
function scanCredits(documents) {
  const groups = new Map();
  let credits = 0;
  for (const doc of documents) {
    if (doc.pdfGroupId) groups.set(doc.pdfGroupId, (groups.get(doc.pdfGroupId) ?? 0) + 1);
    else credits++;
  }
  for (const pages of groups.values()) credits += Math.ceil(pages / 10);
  return credits;
}

async function createScan(req, res, account) {
  const body = await readJson(req);
  const invalid = validateScanBody(body);
  if (invalid) return validationFailure(res, invalid.field, invalid.message);
  if (state.scanQuotaExhausted && !account.premiumActive) {
    const reset = new Date(); reset.setUTCMonth(reset.getUTCMonth() + 1, 1);
    return errorOnly(res, 403, 'QuotaExhausted', { quotaType: 'scanCredits', limit: 10, used: 10, remaining: 0, amount: scanCredits(body.documents), resetDate: reset.toISOString().slice(0, 10) });
  }
  const now = new Date().toISOString();
  const scanId = `scn_${String(++state.scanSeq).padStart(4, '0')}${randomUUID().slice(0, 4)}`;
  const total = body.documents.length;
  const scan = {
    scanId, seq: state.scanSeq, status: 'processing', polls: 0, done: 0, total, kind: null, items: null,
    documents: body.documents.map(({ documentKey, kind, filename, pdfGroupId }) => ({ documentKey, kind, filename, pdfGroupId })),
    referenceDate: body.referenceDate ?? null, userCurrency: body.userCurrency || 'USD',
    sourceLabel: body.sourceLabel || (total === 1 ? '1 file' : `${total} files`), createdAt: now, updatedAt: now,
  };
  scansFor(account).set(scanId, scan);
  return json(res, 202, { data: { scanId, status: 'processing', total } });
}

function scanSummary(scan) {
  const items = scan.items ?? [];
  return {
    scanId: scan.scanId, status: scan.status, progress: { done: scan.done, total: scan.total }, kind: scan.kind,
    itemCount: items.length, flaggedCount: items.filter(item => item.skipReason).length, ruleCount: items.filter(item => item.ruleId).length,
    failedDocuments: 0, sourceLabel: scan.sourceLabel, createdAt: scan.createdAt, updatedAt: scan.updatedAt,
  };
}

/** Rows for a list scan: 12 deterministic rows over ~2 months relative to the reference date. */
function listScanItems(today) {
  const at = days => addDays(today, -days);
  const rows = [
    { merchantName: 'Blue Bottle Coffee', description: 'BLUE BOTTLE COFFEE #12', amount: 4.5, date: at(0), categoryHint: 'dining' },
    { merchantName: 'Blue Bottle Coffee', description: 'BLUE BOTTLE COFFEE #12', amount: 5.25, date: at(2), categoryHint: 'dining' },
    { merchantName: 'Blue Bottle Coffee', description: 'BLUE BOTTLE COFFEE #12', amount: 4.75, date: at(5), categoryHint: 'dining' },
    { merchantName: 'Blue Bottle Coffee', description: 'BLUE BOTTLE COFFEE #12', amount: 6, date: at(9), categoryHint: 'dining' },
    { merchantName: 'Shell', description: 'SHELL GAS STATION', amount: 48.3, date: at(1), categoryHint: 'transport', isPending: true },
    { merchantName: 'Metro', description: 'Metro groceries', amount: 62.18, date: at(3), categoryHint: 'groceries', skipReason: 'duplicate',
      possibleDuplicateOf: { transactionId: 'txn_existing-001', date: at(3), amount: 62.18, description: 'Metro groceries' } },
    { merchantName: null, description: 'PAYMENT – THANK YOU', amount: 420, date: at(6), transactionType: 'income', skipReason: 'payment', confidence: 0.7 },
    { merchantName: null, description: 'E-TRANSFER TO SAVINGS', amount: 300, date: at(14), skipReason: 'transfer', confidence: 0.7 },
    { merchantName: 'Acme Inc', description: 'PAYROLL ACME INC', amount: 2500, date: at(20), transactionType: 'income', categoryHint: 'salary' },
    { merchantName: 'Loblaws', description: 'LOBLAWS #1021', amount: 87.42, date: at(27), categoryHint: 'groceries' },
    { merchantName: 'Netflix', description: 'NETFLIX.COM', amount: 15.99, date: at(35), categoryHint: 'entertainment' },
    { merchantName: 'Corner Store', description: 'CORNER STORE', amount: 7.99, date: null, categoryHint: 'shopping' },
  ];
  return rows;
}

function buildScanItems(scan, account) {
  const today = /^\d{4}-\d{2}-\d{2}$/.test(scan.referenceDate ?? '') ? scan.referenceDate : (() => { const n = new Date(); return `${n.getFullYear()}-${String(n.getMonth() + 1).padStart(2, '0')}-${String(n.getDate()).padStart(2, '0')}`; })();
  const isReceipt = scan.documents.length === 1 && scan.documents[0].kind === 'image' && /receipt/i.test(scan.documents[0].filename ?? '');
  scan.kind = isReceipt ? 'receipt' : 'list';
  const rows = isReceipt
    ? [{ merchantName: 'Metro', description: 'groceries', amount: 38.74, date: addDays(today, -1), categoryHint: 'groceries', isPending: null }]
    : listScanItems(today);
  const seqByDoc = new Map();
  const rules = userRules(account);
  const items = rows.map((row, index) => {
    const documentIndex = index % scan.documents.length;
    const seq = seqByDoc.get(documentIndex) ?? 0;
    seqByDoc.set(documentIndex, seq + 1);
    const item = {
      id: `${scan.scanId}-${documentIndex}-${seq}`, documentIndex, amount: row.amount, currency: scan.userCurrency, date: row.date,
      categoryId: null, categoryHint: row.categoryHint ?? null, merchantName: row.merchantName, description: row.description,
      confidence: row.confidence ?? 1, transactionType: row.transactionType ?? 'expense', possibleDuplicateOf: row.possibleDuplicateOf ?? null,
      skipReason: row.skipReason ?? null, isPending: row.isPending ?? false, ruleId: null,
    };
    // A user rule beats the model: skip rules flag the row, categorize rules file it.
    const rule = rules.find(candidate => ruleMatches(candidate, item.description, item.merchantName));
    if (rule) {
      item.ruleId = rule.id;
      if (rule.action === 'skip') item.skipReason = 'rule';
      else { item.categoryId = rule.categoryId ?? null; item.categoryHint = null; }
    }
    return item;
  });
  // Newest date first, undated last (stable, so ties keep generation order).
  return items.map((item, index) => ({ item, index })).sort((a, b) => (a.item.date === b.item.date ? a.index - b.index : a.item.date === null ? 1 : b.item.date === null ? -1 : a.item.date < b.item.date ? 1 : -1)).map(entry => entry.item);
}

function advanceScan(scan, account) {
  if (scan.status !== 'processing') return;
  scan.polls++;
  scan.updatedAt = new Date().toISOString();
  if (scan.polls <= 2) { scan.done = Math.floor(scan.total * scan.polls / 3); return; }
  scan.items = buildScanItems(scan, account);
  scan.done = scan.total;
  scan.status = 'ready';
}

function scanNotFound(res) {
  return json(res, 404, { message: 'Scan not found' });
}

function getScan(res, account, id) {
  const scan = scansFor(account).get(id);
  if (!scan) return scanNotFound(res);
  advanceScan(scan, account);
  const summary = scanSummary(scan);
  return json(res, 200, { data: ['ready', 'saved'].includes(scan.status) ? { ...summary, items: scan.items } : summary });
}

function listScans(res, account, url) {
  const raw = url.searchParams.get('status');
  const wanted = raw === null ? ['processing', 'ready'] : raw.split(',').map(value => value.trim()).filter(Boolean);
  if (!wanted.length || wanted.some(status => !SCAN_STATUSES.includes(status))) return validationFailure(res, 'status', `status must be a comma-separated list of ${SCAN_STATUSES.join(', ')}`);
  const cutoff = Date.now() - 7 * 24 * 60 * 60 * 1000;
  const scans = [...scansFor(account).values()].filter(scan => wanted.includes(scan.status) && Date.parse(scan.createdAt) >= cutoff)
    .sort((a, b) => b.seq - a.seq).slice(0, 20).map(scanSummary);
  return json(res, 200, { data: { scans } });
}

async function completeScan(req, res, account, id) {
  const body = await readJson(req);
  if (!['saved', 'discarded'].includes(body?.outcome)) return validationFailure(res, 'outcome', 'outcome must be saved or discarded');
  const scan = scansFor(account).get(id);
  if (!scan) return scanNotFound(res);
  if (scan.status === 'processing' || scan.status === 'failed') return apiError(res, 409, 'Scan is not ready', 'ScanNotReady');
  if (scan.status === 'ready') { scan.status = body.outcome; scan.updatedAt = new Date().toISOString(); }
  return json(res, 200, { data: scanSummary(scan) });
}

async function exportCsv(req, res, account) {
  const url = new URL(req.url, 'http://local');
  const start = url.searchParams.get('startDate'), end = url.searchParams.get('endDate');
  if (!/^\d{4}-\d{2}-\d{2}$/.test(start || '') || !/^\d{4}-\d{2}-\d{2}$/.test(end || '')) {
    return json(res, 400, { error: 'InvalidQuery', message: 'startDate and endDate must be in strict YYYY-MM-DD format.' });
  }
  const list = await proxyJson({ url: `/transactions?startDate=${start}&endDate=${end}`, method: 'GET', headers: {}, pipe: d => d.end() }, account.id);
  const rows = (list.body?.data?.transactions ?? list.body?.data ?? []);
  const cats = await proxyJson({ url: '/categories', method: 'GET', headers: {}, pipe: d => d.end() }, account.id);
  const names = new Map((cats.body?.data ?? []).map(c => [c.id, c.name]));
  const esc = v => `"${String(v ?? '').replaceAll('"', '""')}"`;
  const csv = ['Date,Description,Amount,Category', ...rows.map(t => [t.date, esc(t.description), t.amount, esc(names.get(t.categoryId) ?? '')].join(','))].join('\n') + '\n';
  res.writeHead(200, { 'Content-Type': 'text/csv', 'Content-Disposition': `attachment; filename="transactions-${start}-to-${end}.csv"` });
  res.end(csv);
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
  if (path === '/__sidecar/scan-quota' && req.method === 'POST') {
    const body = await readJson(req);
    if (typeof body?.exhausted !== 'boolean') return apiError(res, 400, 'exhausted must be true or false', 'ValidationError');
    state.scanQuotaExhausted = body.exhausted;
    return json(res, 200, { data: { exhausted: state.scanQuotaExhausted } });
  }
  if (path === '/__sidecar/export-fail' && req.method === 'POST') {
    const body = await readJson(req);
    if (typeof body?.fail !== 'boolean') return apiError(res, 400, 'fail must be true or false', 'ValidationError');
    state.exportFail = body.fail;
    return json(res, 200, { data: { fail: state.exportFail } });
  }
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
  // Merchant names: the harness has no route, keep them in memory per account.
  const merchantMatch = /^\/settings\/merchant-mappings(?:\/([^/]+)(?:\/(apply))?)?$/.exec(path);
  if (merchantMatch) return merchantMappings(req, res, account, merchantMatch[1] && decodeURIComponent(merchantMatch[1]), merchantMatch[2]);
  // The harness has no account export route: serve a small synthetic document like the real handler.
  if (path === '/user/export' && req.method === 'GET') return accountExport(req, res, account);
  // Scans: the harness has no scan worker, so the sidecar fakes the whole engine in memory.
  if (path === '/scans' && req.method === 'POST') return createScan(req, res, account);
  if (path === '/scans' && req.method === 'GET') return listScans(res, account, url);
  const scanMatch = /^\/scans\/([^/]+)(\/complete)?$/.exec(path);
  if (scanMatch && !scanMatch[2] && req.method === 'GET') return getScan(res, account, decodeURIComponent(scanMatch[1]));
  if (scanMatch && scanMatch[2] && req.method === 'POST') return completeScan(req, res, account, decodeURIComponent(scanMatch[1]));
  // The harness has no export route: build a CSV like the real handler from its transaction + category lists.
  if (path === '/transactions/export' && req.method === 'GET') return exportCsv(req, res, account);
  if (path === '/transactions/upload-url' && req.method === 'POST') return uploadUrl(req, res, account, server);
  if (path === '/statements/analyze-spreadsheet' && req.method === 'POST') return analyzeStatement(req, res, account);
  if (path === '/statements/preview-spreadsheet' && req.method === 'POST') return previewStatement(req, res, account);
  if (path === '/statements/import-spreadsheet' && req.method === 'POST') return importStatement(req, res, account);
  if (path === '/transactions/analyze-audio' && req.method === 'POST') return analyzeAudio(req, res, account);
  if (path === '/transactions/analyze-image' && req.method === 'POST') return analyzeImage(req, res, account);
  const analysisMatch = /^\/transactions\/analysis\/([^/]+)$/.exec(path);
  if (analysisMatch && req.method === 'GET') return analysisStatus(res, decodeURIComponent(analysisMatch[1]), account);

  // The harness has no analysis or scan log, so a bulk save that references a sidecar analysisId or
  // scanId would fail there; drop the link (the real server only uses it for audit / marking the
  // scan saved) and forward the rest. Scan bulk saves keep source "scan".
  if (path === '/transactions/bulk' && req.method === 'POST') {
    const payload = await readJson(req);
    let scan = null;
    if (payload && !Array.isArray(payload)) {
      if ('analysisId' in payload) delete payload.analysisId;
      if (payload.source === 'scan' && 'scanId' in payload) {
        scan = scansFor(account).get(String(payload.scanId));
        if (!scan) return apiError(res, 404, 'Scan not found', 'ScanNotFound');
        delete payload.scanId;
      }
    }
    let result = await proxyJson(reqFromBody(req, payload), account.id);
    if (scan && result.status === 400 && result.body?.error === 'ValidationError') {
      console.warn('[sidecar] harness bulk route rejected source=scan; retrying with source=manual');
      result = await proxyJson(reqFromBody(req, { ...payload, source: 'manual' }), account.id);
    }
    if (scan && scan.status === 'ready' && result.status >= 200 && result.status < 300) { scan.status = 'saved'; scan.updatedAt = new Date().toISOString(); }
    return sendProxyJson(res, result);
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

function jsonRequest(method, url, body) {
  const bytes = Buffer.from(JSON.stringify(body));
  return { url, method, headers: { 'content-type': 'application/json', 'content-length': String(bytes.length) }, pipe(destination) { destination.end(bytes); return destination; } };
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
  const extByMime = { 'text/csv': 'csv', 'application/pdf': 'pdf', 'image/jpeg': 'jpg', 'image/jpg': 'jpg', 'image/png': 'png', 'image/webp': 'webp', 'application/vnd.ms-excel': 'xls', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet': 'xlsx', 'audio/mp4': 'm4a', 'audio/mpeg': 'mp3', 'text/plain': 'txt' };
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
