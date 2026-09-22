/**
 * Cyber App Manager v1.2.0 — WebUI core
 *
 * Everything runs through window.Shizuku (Shevery ADB-module bridge). No network, no module-directory
 * scripts: the shell scripts shipped with the module mirror these operations for Actions and services.
 */
(function () {
'use strict';

const BUILD = '1.2.0';
const $ = id => document.getElementById(id);
const esc = s => String(s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const shq = s => "'" + String(s).replace(/'/g, "'\\''") + "'";
const tick = () => new Promise(r => setTimeout(r, 0));
const PKG_RE = /^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)*$/;
const FAIL_RE = /(exception|failure|failed|error|not installed for|unknown package|denied)/i;

function fmtBytes(n) {
  if (!n) return '0 B';
  const u = ['B', 'KB', 'MB', 'GB', 'TB']; let i = 0;
  while (n >= 1024 && i < u.length - 1) { n /= 1024; i++; }
  return (i === 0 ? n : n.toFixed(1)) + ' ' + u[i];
}
const firstLine = s => String(s || '').split('\n').map(x => x.trim()).filter(Boolean)[0] || '';

// ═════════════════════════════════════════════════════════════════════════════
// Storage (WebView localStorage, tolerant of failures)
// ═════════════════════════════════════════════════════════════════════════════
const mem = {};
const LS = {
  get(k) { try { const v = localStorage.getItem(k); return v === null ? (mem[k] === undefined ? null : mem[k]) : v; } catch (e) { return mem[k] === undefined ? null : mem[k]; } },
  set(k, v) { mem[k] = v; try { localStorage.setItem(k, v); } catch (e) {} },
  del(k) { delete mem[k]; try { localStorage.removeItem(k); } catch (e) {} },
  json(k, d) { try { const v = JSON.parse(LS.get(k)); return v === null || v === undefined ? d : v; } catch (e) { return d; } },
  setJson(k, v) { LS.set(k, JSON.stringify(v)); },
};

// ═════════════════════════════════════════════════════════════════════════════
// Console log
// ═════════════════════════════════════════════════════════════════════════════
const Log = {
  lines: [], max: 400,
  add(msg, type) {
    this.lines.push({ msg: String(msg), type: type || '' });
    if (this.lines.length > this.max) this.lines.shift();
    const body = $('console-body'); if (!body) return;
    const d = document.createElement('div');
    d.className = 'log-line ' + (type || '');
    d.textContent = String(msg).slice(0, 400);
    body.appendChild(d);
    while (body.childNodes.length > this.max) body.removeChild(body.firstChild);
    body.scrollTop = body.scrollHeight;
  },
  text() { return this.lines.map(l => l.msg).join('\n'); },
  lastError() { for (let i = this.lines.length - 1; i >= 0; i--) if (this.lines[i].type === 'err') return this.lines[i].msg; return ''; },
};

// ═════════════════════════════════════════════════════════════════════════════
// UI primitives: toast, modal, progress, picker, tabs
// ═════════════════════════════════════════════════════════════════════════════
const Toast = {
  timer: null,
  show(msg, type, opt) {
    const t = $('toast'); if (!t) return;
    t.innerHTML = '';
    const s = document.createElement('span'); s.textContent = msg; t.appendChild(s);
    if (opt && opt.action) {
      const b = document.createElement('button'); b.textContent = opt.action;
      b.addEventListener('click', () => { t.classList.remove('show'); opt.fn(); });
      t.appendChild(b);
    }
    t.className = 'toast show' + (type === 'err' ? ' err' : type === 'ok' ? ' ok' : '');
    clearTimeout(this.timer);
    this.timer = setTimeout(() => t.classList.remove('show'), (opt && opt.ms) || 3200);
  },
};

const Modal = {
  resolve: null,
  /** ask({title, msg, list, confirm, danger, typed}) -> Promise<boolean> */
  ask(o) {
    return new Promise(res => {
      if (this.resolve) this.resolve(false);
      this.resolve = res;
      $('modal-title').textContent = o.title || 'CONFIRM';
      $('modal-msg').textContent = o.msg || '';
      const list = $('modal-list');
      list.innerHTML = (o.list || []).map(x => '<div>' + esc(x) + '</div>').join('');
      list.style.display = (o.list && o.list.length) ? 'block' : 'none';
      const typed = $('modal-typed'), ok = $('modal-confirm');
      ok.textContent = o.confirm || 'CONFIRM';
      ok.className = 'modal-confirm' + (o.danger ? ' danger' : '');
      typed.value = '';
      if (o.typed) {
        typed.style.display = 'block'; typed.placeholder = 'type ' + o.typed + ' to confirm';
        ok.disabled = true; this.need = o.typed.toLowerCase();
      } else { typed.style.display = 'none'; ok.disabled = false; this.need = ''; }
      $('modal-overlay').classList.add('active');
    });
  },
  close(v) {
    $('modal-overlay').classList.remove('active');
    const r = this.resolve; this.resolve = null;
    if (r) r(!!v);
  },
};

const Progress = {
  start(label, total) { this.total = total; $('progress-label').textContent = label; $('progress-fill').style.width = '0%'; $('progress').classList.add('show'); },
  step(i, name) { $('progress-label').textContent = (i + 1) + '/' + this.total + '  ' + name; $('progress-fill').style.width = Math.round((i / this.total) * 100) + '%'; },
  end() { $('progress-fill').style.width = '100%'; setTimeout(() => $('progress').classList.remove('show'), 250); },
};

/** Themed drop-down replacing native <select> (native popups render out of style in WebViews). */
function initPicker(el, options, initial, onChange) {
  let cur = options.some(o => o.value === initial) ? initial : (options[0] && options[0].value);
  el.classList.add('picker');
  el.innerHTML = '<button type="button" class="picker-btn"><span class="picker-label"></span><span class="picker-caret">▾</span></button>' +
                 '<div class="pick-menu"><div class="pick-list"></div></div>';
  const btn = el.querySelector('.picker-btn'), menu = el.querySelector('.pick-menu'), list = el.querySelector('.pick-list');
  const paint = () => { const o = options.find(x => x.value === cur); el.querySelector('.picker-label').textContent = o ? o.label : ''; };
  const close = () => { menu.style.display = 'none'; el.classList.remove('open'); };
  btn.addEventListener('click', () => {
    if (menu.style.display === 'block') { close(); return; }
    list.innerHTML = options.map(o => '<div class="pick-item' + (o.value === cur ? ' sel' : '') + '" data-v="' + esc(o.value) + '">' + esc(o.label) + '</div>').join('');
    menu.style.display = 'block'; el.classList.add('open');
  });
  list.addEventListener('click', e => {
    const it = e.target.closest && e.target.closest('[data-v]');
    if (!it) return;
    cur = it.dataset.v; paint(); close();
    if (onChange) onChange(cur);
  });
  document.addEventListener('click', e => { if (menu.style.display === 'block' && !el.contains(e.target)) close(); });
  Object.defineProperty(el, 'value', { get: () => cur, set: v => { cur = v; paint(); }, configurable: true });
  paint();
}

const Tabs = {
  cur: 'apps',
  show(name) {
    this.cur = name;
    document.querySelectorAll('.tab').forEach(b => b.classList.toggle('active', b.dataset.tab === name));
    document.querySelectorAll('.panel').forEach(p => p.classList.toggle('active', p.id === 'tab-' + name));
    document.body.classList.toggle('on-apps', name === 'apps');
    Tray.update();
    if (name === 'debloat') Debloat.render();
    if (name === 'vault') Vault.render();
    window.scrollTo(0, 0);
  },
};

// ═════════════════════════════════════════════════════════════════════════════
// Bridge to the Shevery shell
// ═════════════════════════════════════════════════════════════════════════════
const Bridge = {
  ready: false, info: null, uid: '', user: 0, hostPkg: '',
  raw(cmd, opts) {
    const S = window.Shizuku;
    let res = (opts && typeof S.execWithOptions === 'function') ? S.execWithOptions(cmd, JSON.stringify(opts)) : S.exec(cmd);
    if (typeof res === 'string') res = JSON.parse(res);
    return res || {};
  },
  /** Never throws. Returns {ok, code, out, err, timedOut, ms}. */
  run(cmd, opts, quiet) {
    const t0 = Date.now(); let r;
    if (!quiet) Log.add('> ' + String(cmd).replace(/\s+/g, ' ').slice(0, 220));
    try { r = this.raw(cmd, opts); } catch (e) { r = { ok: false, exitCode: -1, stdout: '', stderr: String(e && e.message || e) }; }
    const out = { ok: !!r.ok, code: r.exitCode, out: r.stdout || '', err: r.stderr || '', timedOut: !!r.timedOut, ms: Date.now() - t0 };
    if (!quiet) {
      const p = out.out.replace(/\n/g, '\\n');
      Log.add('< exit=' + out.code + ' | ' + p.slice(0, 120) + (p.length > 120 ? '…' : '') + (out.err ? ' | ' + out.err.slice(0, 100) : ''), out.ok ? 'ok' : 'err');
    }
    return out;
  },
  detect() {
    const S = window.Shizuku;
    if (typeof S === 'undefined' || !S) return { ok: false, why: 'window.Shizuku is missing. Enable the WebUI shell bridge (Full access) or trust the module in Shevery.' };
    try { this.info = JSON.parse(S.getModuleInfo()); } catch (e) { this.info = null; }
    const r = this.run('id', null, true);
    if (!r.ok) return { ok: false, why: 'Bridge error: ' + (r.err || 'exec returned ok=false') };
    this.ready = true;
    this.uid = (r.out.match(/uid=(\d+)/) || [])[1] || '';
    const m = this.info && this.info.moduleDir ? String(this.info.moduleDir).match(/\/data\/(?:user\/\d+|data)\/([^/]+)\//) : null;
    this.hostPkg = m ? m[1] : '';
    return { ok: true };
  },
  trusted() { return this.info && typeof this.info.trusted === 'boolean' ? this.info.trusted : null; },
};

// ═════════════════════════════════════════════════════════════════════════════
// Persistent files on the device (falls back to localStorage)
// ═════════════════════════════════════════════════════════════════════════════
const Store = {
  dir: '/data/local/tmp/cyber-app-manager', ok: false,
  init() {
    const r = Bridge.run('mkdir -p ' + shq(this.dir + '/snaps') + ' && echo ok', null, true);
    this.ok = r.ok && /ok/.test(r.out);
    Log.add('store: ' + (this.ok ? this.dir : 'localStorage fallback'), this.ok ? 'ok' : 'warn');
  },
  path(rel) { return this.dir + '/' + rel; },
  read(rel) {
    if (this.ok) { const r = Bridge.run('cat ' + shq(this.path(rel)) + ' 2>/dev/null', null, true); if (r.ok) return r.out; }
    return LS.get('cam_f:' + rel);
  },
  write(rel, text) {
    if (this.ok) {
      const p = this.path(rel);
      const r = Bridge.run('cat > ' + shq(p), { stdin: text, timeoutSeconds: 30 }, true);
      if (r.ok) { LS.del('cam_f:' + rel); return true; }
    }
    LS.set('cam_f:' + rel, text); return !!LS.get('cam_f:' + rel);
  },
  del(rel) {
    if (this.ok) Bridge.run('rm -f ' + shq(this.path(rel)), null, true);
    LS.del('cam_f:' + rel);
  },
  /** Snapshot files: returns [{id, head:[lines], frozen, removed, total}] */
  listSnaps() {
    const out = [];
    if (this.ok) {
      const d = this.dir + '/snaps';
      const cmd = 'for f in ' + shq(d) + '/*.txt; do [ -f "$f" ] || continue; echo "@@$(basename "$f" .txt)"; head -n 6 "$f"; ' +
                  'echo "@#$(grep -c "|d$" "$f") $(grep -c "|r$" "$f") $(grep -c "|" "$f")"; done';
      const r = Bridge.run(cmd, null, true);
      let cur = null;
      r.out.split('\n').forEach(line => {
        if (line.indexOf('@@') === 0) { cur = { id: line.slice(2).trim(), head: [] }; out.push(cur); }
        else if (line.indexOf('@#') === 0 && cur) { const n = line.slice(2).trim().split(' '); cur.frozen = +n[0] || 0; cur.removed = +n[1] || 0; cur.total = +n[2] || 0; }
        else if (cur && line.charAt(0) === '#') cur.head.push(line);
      });
    }
    Object.keys(mem).concat((() => { try { return Object.keys(localStorage); } catch (e) { return []; } })()).forEach(k => {
      const m = k.match(/^cam_f:snaps\/(.+)\.txt$/);
      if (m && !out.some(o => o.id === m[1])) {
        const t = LS.get(k) || '';
        out.push({ id: m[1], head: t.split('\n').filter(l => l.charAt(0) === '#').slice(0, 6), frozen: (t.match(/\|d$/gm) || []).length, removed: (t.match(/\|r$/gm) || []).length, total: (t.match(/\|/g) || []).length });
      }
    });
    return out;
  },
};

// ═════════════════════════════════════════════════════════════════════════════
// Settings
// ═════════════════════════════════════════════════════════════════════════════
const Settings = {
  v: Object.assign({ autoSnap: true, overlays: false, confirm: true, sort: 'name' }, LS.json('cam_settings', {})),
  save() { LS.setJson('cam_settings', this.v); },
};

// ═════════════════════════════════════════════════════════════════════════════
// Registry data
// ═════════════════════════════════════════════════════════════════════════════
const Reg = {
  apps: [], map: new Map(), usage: null,
  get(p) { return this.map.get(p); },
  set(list) { this.apps = list; this.map = new Map(list.map(a => [a.pkg, a])); },
  counts() {
    const live = this.apps.filter(a => a.state !== 'r');
    return {
      total: live.length,
      enabled: live.filter(a => a.state === 'e').length,
      frozen: live.filter(a => a.state === 'd').length,
      system: live.filter(a => a.sys).length,
      user: live.filter(a => !a.sys).length,
      removed: this.apps.length - live.length,
      running: live.filter(a => a.running).length,
    };
  },
};
const Sel = new Set();

// ═════════════════════════════════════════════════════════════════════════════
// Guard — never lets destructive operations touch protected packages
// ═════════════════════════════════════════════════════════════════════════════
const DESTRUCTIVE = new Set(['freeze', 'stop', 'remove', 'clear', 'bg', 'perm']);
const Guard = {
  dyn: {},
  load() {
    this.dyn = {};
    const cmd = [
      "echo '@L'; cmd package resolve-activity --brief -c android.intent.category.HOME 2>/dev/null | tail -n 1",
      "echo '@I'; settings get secure default_input_method 2>/dev/null",
      "echo '@D'; cmd role holders android.app.role.DIALER 2>/dev/null; settings get secure dialer_default_application 2>/dev/null",
      "echo '@S'; cmd role holders android.app.role.SMS 2>/dev/null; settings get secure sms_default_application 2>/dev/null",
      "echo '@W'; cmd webviewupdate get-webview-provider 2>/dev/null",
      "echo '@U'; am get-current-user 2>/dev/null",
    ].join('; ');
    const r = Bridge.run(cmd, null, true);
    const names = { L: 'current launcher', I: 'current keyboard', D: 'default dialer', S: 'default SMS app', W: 'WebView provider' };
    let sec = '';
    r.out.split('\n').forEach(line => {
      line = line.trim();
      if (/^@[A-Z]$/.test(line)) { sec = line.charAt(1); return; }
      if (!line || line === 'null') return;
      if (sec === 'U') { const n = parseInt(line, 10); if (!isNaN(n)) Bridge.user = n; return; }
      if (!names[sec]) return;
      const m = line.match(/[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+/);
      if (m && PKG_RE.test(m[0])) this.dyn[m[0]] = names[sec];
    });
    if (Bridge.hostPkg) this.dyn[Bridge.hostPkg] = 'Shevery (hosts this module)';
    Log.add('guard: ' + Object.keys(this.dyn).length + ' dynamic protections, user ' + Bridge.user, 'ok');
  },
  /** '' when allowed, otherwise the reason it is refused. */
  reason(pkg, action) {
    if (!DESTRUCTIVE.has(action)) return '';
    if (this.dyn[pkg]) return 'protected: ' + this.dyn[pkg];
    const a = Reg.get(pkg);
    const c = KB.classify(pkg, !!(a && a.sys));
    if (c.risk === 'core') return c.cat === 'overlay' ? 'protected: resource overlay' : 'protected: core system package';
    return '';
  },
};

// ═════════════════════════════════════════════════════════════════════════════
// Pins (enforce list, re-applied by service.sh on every Shizuku session)
// ═════════════════════════════════════════════════════════════════════════════
const Pins = {
  set: new Set(),
  load() {
    const t = Store.read('enforce.list') || '';
    this.set = new Set(t.split('\n').map(s => s.trim()).filter(p => p && PKG_RE.test(p)));
  },
  save() { return Store.write('enforce.list', Array.from(this.set).sort().join('\n') + (this.set.size ? '\n' : '')); },
  toggle(pkgs, on) {
    pkgs.forEach(p => { on ? this.set.add(p) : this.set.delete(p); const a = Reg.get(p); if (a) a.pinned = on; });
    return this.save();
  },
  /** Pinned packages that are currently enabled again (updates and OEM services undo freezes). */
  drift() { return Array.from(this.set).filter(p => { const a = Reg.get(p); return a && a.state === 'e'; }); },
};

// ═════════════════════════════════════════════════════════════════════════════
// Scan
// ═════════════════════════════════════════════════════════════════════════════
const Scan = {
  busy: false,
  list(flags) {
    const base = 'pm list packages ' + flags + " 2>/dev/null | sed 's/^package://'";
    const r = Bridge.run(base, null, true);
    let lines = r.out.split('\n');
    if (r.out.length >= 60000) {   // the bridge keeps only the last 64 KB, so read in slices
      lines = [];
      for (let k = 0; k < 40; k++) {
        const s = Bridge.run(base + ' | tail -n +' + (k * 600 + 1) + ' | head -n 600', null, true).out.split('\n').filter(Boolean);
        lines = lines.concat(s);
        if (s.length < 600) break;
      }
    }
    return new Set(lines.map(s => s.trim()).filter(p => p && PKG_RE.test(p)));
  },
  async run(o) {
    o = o || {};
    if (this.busy) return;
    this.busy = true;
    const listEl = $('app-list');
    if (!o.quiet) listEl.innerHTML = '<div class="loading-state"><div class="spinner"></div><p>SCANNING PACKAGES…</p></div>';
    await tick();
    try {
      Guard.load();
      const all = this.list('-u'), inst = this.list(''), sys = this.list('-s'), dis = this.list('-d');
      if (!all.size) throw new Error('pm list packages returned nothing');
      const ps = Bridge.run('ps -A -o NAME 2>/dev/null', null, true).out;
      const running = new Set();
      ps.split('\n').forEach(n => { n = n.trim().split(':')[0]; if (n && all.has(n)) running.add(n); });
      const apps = [], notes = LS.json('cam_notes', {});
      Array.from(all).forEach(pkg => {
        const isSys = sys.has(pkg);
        const c = KB.classify(pkg, isSys);
        const prev = Reg.get(pkg);
        apps.push({
          pkg: pkg, sys: isSys, state: !inst.has(pkg) ? 'r' : dis.has(pkg) ? 'd' : 'e',
          running: running.has(pkg), name: c.name, cat: c.cat, risk: c.risk, desc: c.desc, known: c.known,
          pinned: Pins.set.has(pkg), note: notes[pkg] || '', bytes: prev && prev.bytes || 0,
        });
      });
      Reg.set(apps);
      Array.from(Sel).forEach(p => { if (!Reg.get(p)) Sel.delete(p); });
      Log.add('scan: ' + apps.length + ' packages (' + Reg.counts().frozen + ' frozen, ' + Reg.counts().removed + ' removed)', 'ok');
      UI.stats(); Registry.render(true); Tray.update();
      if (!o.quiet) Toast.show('Loaded ' + apps.length + ' packages', 'ok');
    } catch (e) {
      Log.add('SCAN FAILED: ' + e.message, 'err');
      listEl.innerHTML = '<div class="loading-state"><p class="bad">SCAN FAILED</p><p class="sub">' + esc(e.message) + '</p><button class="reg-btn" id="btn-retry">RETRY</button></div>';
      const b = $('btn-retry'); if (b) b.addEventListener('click', () => Scan.run());
      Toast.show(e.message, 'err');
    } finally { this.busy = false; }
  },
};

// ═════════════════════════════════════════════════════════════════════════════
// Stats & tray
// ═════════════════════════════════════════════════════════════════════════════
const UI = {
  stats() {
    const c = Reg.counts();
    $('stat-total').textContent = c.total; $('stat-enabled').textContent = c.enabled;
    $('stat-frozen').textContent = c.frozen; $('stat-system').textContent = c.system;
    $('count-all').textContent = c.total; $('count-user').textContent = c.user; $('count-system').textContent = c.system;
    $('count-disabled').textContent = c.frozen; $('count-removed').textContent = c.removed; $('count-running').textContent = c.running;
    const pct = n => c.total ? Math.round((n / c.total) * 100) + '%' : '0%';
    $('bar-total').style.width = '100%'; $('bar-enabled').style.width = pct(c.enabled);
    $('bar-frozen').style.width = pct(c.frozen); $('bar-system').style.width = pct(c.system);
  },
};

const Tray = {
  update() {
    const n = Sel.size;
    $('selected-count').textContent = n + ' SELECTED';
    $('sel-count').textContent = n + ' SELECTED';
    $('action-bar').classList.toggle('show', n > 0 && Tabs.cur === 'apps');
    $('btn-pin').textContent = Array.from(Sel).length && Array.from(Sel).every(p => Pins.set.has(p)) ? 'UNPIN' : 'PIN';
  },
};

// ═════════════════════════════════════════════════════════════════════════════
// Registry (search / filter / sort / windowed rendering)
// ═════════════════════════════════════════════════════════════════════════════
const RISK_ORDER = { safe: 0, caution: 1, user: 2, core: 3 };
const Registry = {
  view: { q: '', tab: 'all', risk: 'any', sort: Settings.v.sort || 'name', limit: 60 },
  filtered: [],
  compute() {
    const v = this.view, q = v.q.trim().toLowerCase();
    let list = Reg.apps.filter(a => {
      if (a.cat === 'overlay' && !Settings.v.overlays && v.tab !== 'selected') return false;
      const live = a.state !== 'r';
      switch (v.tab) {
        case 'user': if (!(live && !a.sys)) return false; break;
        case 'system': if (!(live && a.sys)) return false; break;
        case 'disabled': if (a.state !== 'd') return false; break;
        case 'removed': if (a.state !== 'r') return false; break;
        case 'running': if (!(live && a.running)) return false; break;
        case 'selected': if (!Sel.has(a.pkg)) return false; break;
        default: if (!live) return false;
      }
      if (v.risk !== 'any') {
        const r = a.risk === 'user' ? 'user' : a.risk;
        if (v.risk === 'core' ? r !== 'core' : r !== v.risk) return false;
      }
      if (q && (a.pkg.toLowerCase().indexOf(q) < 0 && a.name.toLowerCase().indexOf(q) < 0 && a.cat.indexOf(q) < 0 && (a.desc || '').toLowerCase().indexOf(q) < 0)) return false;
      return true;
    });
    const by = v.sort;
    list.sort((a, b) => {
      if (by === 'risk') { const d = RISK_ORDER[a.risk] - RISK_ORDER[b.risk]; if (d) return d; }
      else if (by === 'usage') { const d = (b.bytes || 0) - (a.bytes || 0); if (d) return d; }
      else if (by === 'state') { const d = (a.state === 'e' ? 1 : 0) - (b.state === 'e' ? 1 : 0); if (d) return d; }
      return a.name.localeCompare(b.name) || a.pkg.localeCompare(b.pkg);
    });
    this.filtered = list;
    return list;
  },
  tagsHtml(a) {
    const t = [];
    t.push(a.sys ? '<span class="tag tag-system">SYSTEM</span>' : '<span class="tag tag-user">USER</span>');
    if (a.state === 'd') t.push('<span class="tag tag-disabled">FROZEN</span>');
    else if (a.state === 'r') t.push('<span class="tag tag-removed">REMOVED</span>');
    if (a.risk === 'safe') t.push('<span class="tag tag-safe">SAFE</span>');
    else if (a.risk === 'caution') t.push('<span class="tag tag-caution">CAUTION</span>');
    else if (a.risk === 'core') t.push('<span class="tag tag-protected">' + (a.cat === 'overlay' ? 'OVERLAY' : 'PROTECTED') + '</span>');
    if (a.running) t.push('<span class="tag tag-run">RUNNING</span>');
    if (a.pinned) t.push('<span class="tag tag-pin">PIN</span>');
    if (a.bytes) t.push('<span class="tag tag-data">' + fmtBytes(a.bytes) + '</span>');
    return t.join('');
  },
  itemHtml(a) {
    const s = Sel.has(a.pkg);
    return '<div class="app-item' + (s ? ' selected' : '') + ' st-' + a.state + '" data-pkg="' + a.pkg + '">' +
      '<div class="app-check' + (s ? ' checked' : '') + '"></div>' +
      '<div class="app-info"><div class="app-name">' + esc(a.name) + '</div><div class="app-pkg">' + esc(a.pkg) + '</div>' +
      '<div class="app-tags">' + this.tagsHtml(a) + '</div></div>' +
      '<div class="app-btns"><button class="mini" data-act="info" title="Details">i</button><button class="mini ai" data-act="ask" title="Ask AI">AI</button></div></div>';
  },
  render(reset) {
    if (reset) this.view.limit = 60;
    const list = this.compute();
    const el = $('app-list');
    if (!list.length) { el.innerHTML = '<div class="loading-state"><p>NO PACKAGES FOUND</p></div>'; $('result-count').textContent = '0'; return; }
    const shown = list.slice(0, this.view.limit);
    let html = shown.map(a => this.itemHtml(a)).join('');
    if (list.length > shown.length) html += '<button class="more-btn" data-act="more">SHOW ' + Math.min(80, list.length - shown.length) + ' MORE · ' + (list.length - shown.length) + ' LEFT</button>';
    el.innerHTML = html;
    $('result-count').textContent = list.length + (list.length === 1 ? ' PACKAGE' : ' PACKAGES');
  },
  toggle(pkg) {
    Sel.has(pkg) ? Sel.delete(pkg) : Sel.add(pkg);
    const el = $('app-list').querySelector('.app-item[data-pkg="' + pkg + '"]');
    if (el) {
      const s = Sel.has(pkg);
      el.classList.toggle('selected', s);
      el.querySelector('.app-check').classList.toggle('checked', s);
    }
    if (this.view.tab === 'selected') this.render();
    Tray.update();
  },
  bind() {
    const list = $('app-list');
    list.addEventListener('click', e => {
      const act = e.target.closest('[data-act]');
      const item = e.target.closest('.app-item');
      if (act && act.dataset.act === 'more') { this.view.limit += 80; this.render(); return; }
      if (!item) return;
      const pkg = item.dataset.pkg;
      if (act && act.dataset.act === 'info') { Sheet.open(pkg); return; }
      if (act && act.dataset.act === 'ask') { if (CAM.onAsk) CAM.onAsk(pkg); return; }
      this.toggle(pkg);
    });
    $('search-input').addEventListener('input', e => { this.view.q = e.target.value; this.render(true); });
    $('search-clear').addEventListener('click', () => { $('search-input').value = ''; this.view.q = ''; this.render(true); });
    document.querySelectorAll('.filter-btn').forEach(b => b.addEventListener('click', () => {
      document.querySelectorAll('.filter-btn').forEach(x => x.classList.remove('active'));
      b.classList.add('active'); this.view.tab = b.dataset.filter; this.render(true);
    }));
    initPicker($('risk-pick'), [
      { value: 'any', label: 'ANY RISK' }, { value: 'safe', label: 'SAFE TO FREEZE' },
      { value: 'caution', label: 'CAUTION' }, { value: 'user', label: 'USER APPS' }, { value: 'core', label: 'PROTECTED' },
    ], 'any', v => { this.view.risk = v; this.render(true); });
    initPicker($('sort-pick'), [
      { value: 'name', label: 'SORT: NAME' }, { value: 'risk', label: 'SORT: RISK' },
      { value: 'state', label: 'SORT: FROZEN FIRST' }, { value: 'usage', label: 'SORT: DATA USE' },
    ], this.view.sort, v => {
      this.view.sort = v; Settings.v.sort = v; Settings.save();
      if (v === 'usage' && !Reg.apps.some(a => a.bytes)) Toast.show('Load the USAGE tab first to sort by data', 'err');
      this.render(true);
    });
    $('btn-select-all').addEventListener('click', () => {
      this.compute().forEach(a => Sel.add(a.pkg));
      this.render(); Tray.update();
    });
    $('clear-selection').addEventListener('click', () => { Sel.clear(); this.render(); Tray.update(); });
  },
};

// ═════════════════════════════════════════════════════════════════════════════
// Operations (validated, confirmed, logged, undoable)
// ═════════════════════════════════════════════════════════════════════════════
const U = () => Bridge.user;
const ACTIONS = {
  freeze:   { label: 'FREEZE',     verb: 'Freeze',     to: 'd', pre: a => a.state === 'e', skip: 'not enabled', cmd: p => 'pm disable-user --user ' + U() + ' ' + p, undo: 'unfreeze', snap: true },
  unfreeze: { label: 'UNFREEZE',   verb: 'Unfreeze',   to: 'e', pre: a => a.state === 'd', skip: 'not frozen', cmd: p => 'pm enable --user ' + U() + ' ' + p, undo: 'freeze', snap: true },
  stop:     { label: 'FORCE-STOP', verb: 'Force-stop', pre: a => a.state !== 'r', skip: 'removed', cmd: p => 'am force-stop ' + p },
  remove:   { label: 'REMOVE',     verb: 'Remove',     to: 'r', pre: a => a.state !== 'r', skip: 'already removed', cmd: p => 'pm uninstall --user ' + U() + ' ' + p, danger: true, typed: 'REMOVE', snap: true },
  restore:  { label: 'RESTORE',    verb: 'Restore',    to: 'e', pre: a => a.state === 'r', skip: 'not removed', cmd: p => 'pm install-existing --user ' + U() + ' ' + p, snap: true },
  clear:    { label: 'CLEAR DATA', verb: 'Clear data of', pre: a => a.state !== 'r', skip: 'removed', cmd: p => 'pm clear --user ' + U() + ' ' + p, danger: true, typed: 'CLEAR' },
};

const Undo = {
  records: LS.json('cam_undo', []),
  push(rec) { this.records.unshift(rec); this.records = this.records.slice(0, 10); LS.setJson('cam_undo', this.records); Vault.render(); },
  async apply(i) {
    const rec = this.records[i]; if (!rec) return;
    const groups = {};
    rec.changes.forEach(c => { if (c.undo) (groups[c.undo] = groups[c.undo] || []).push(c.pkg); });
    const acts = Object.keys(groups);
    if (!acts.length) { Toast.show('Nothing reversible in that batch', 'err'); return; }
    for (const a of acts) await Ops.batch(a, groups[a], { noConfirm: true, noUndo: true, label: 'UNDO' });
    this.records.splice(i, 1); LS.setJson('cam_undo', this.records); Vault.render();
  },
};

const Ops = {
  busy: false,
  plan(action, pkgs) {
    const spec = ACTIONS[action], plan = [], skipped = [];
    pkgs.forEach(pkg => {
      if (!PKG_RE.test(pkg)) { skipped.push([pkg, 'invalid package name']); return; }
      const a = Reg.get(pkg);
      if (!a) { skipped.push([pkg, 'not found']); return; }
      const why = Guard.reason(pkg, action);
      if (why) { skipped.push([a.name, why]); return; }
      if (!spec.pre(a)) { skipped.push([a.name, spec.skip]); return; }
      plan.push(a);
    });
    return { plan, skipped };
  },
  async batch(action, pkgs, o) {
    o = o || {};
    const spec = ACTIONS[action];
    if (!spec) return { ok: 0, fail: 0 };
    if (this.busy) { Toast.show('Another operation is running', 'err'); return { ok: 0, fail: 0 }; }
    const { plan, skipped } = this.plan(action, pkgs);
    if (!plan.length) {
      Toast.show(skipped.length ? 'Nothing to do — ' + skipped[0][1] : 'Nothing selected', 'err');
      skipped.forEach(s => Log.add('skip ' + s[0] + ': ' + s[1], 'warn'));
      return { ok: 0, fail: 0 };
    }
    if (!o.noConfirm && (Settings.v.confirm || spec.danger)) {
      const cautions = plan.filter(a => a.risk === 'caution').length;
      const lines = plan.slice(0, 14).map(a => a.name + '  ·  ' + a.pkg);
      if (plan.length > 14) lines.push('… and ' + (plan.length - 14) + ' more');
      if (skipped.length) lines.push('— skipped ' + skipped.length + ': ' + skipped.slice(0, 3).map(s => s[0] + ' (' + s[1] + ')').join(', ') + (skipped.length > 3 ? '…' : ''));
      let msg = spec.verb + ' ' + plan.length + ' package' + (plan.length > 1 ? 's' : '') + '?';
      if (cautions) msg += ' ' + cautions + ' marked CAUTION may remove a feature you use.';
      if (action === 'remove') msg += ' System apps stay recoverable with RESTORE; user apps do not.';
      const ok = await Modal.ask({ title: spec.label, msg, list: lines, confirm: spec.label, danger: !!spec.danger || action === 'freeze', typed: spec.typed && plan.length > 0 ? spec.typed : '' });
      if (!ok) return { ok: 0, fail: 0 };
    }
    this.busy = true;
    let good = 0; const fails = [], changes = [];
    try {
      if (spec.snap && Settings.v.autoSnap && !o.noSnap) Vault.autoSnapshot(spec.label.toLowerCase() + ' ' + plan.length);
      Progress.start(spec.label, plan.length);
      for (let i = 0; i < plan.length; i++) {
        const a = plan[i];
        Progress.step(i, a.name);
        await tick();
        const r = Bridge.run(spec.cmd(a.pkg));
        const text = r.out + ' ' + r.err;
        if (r.ok && !FAIL_RE.test(text)) {
          good++;
          const from = a.state;
          if (spec.to) { a.state = spec.to; a.running = false; }
          if (action === 'stop') a.running = false;
          if (spec.to) changes.push({ pkg: a.pkg, name: a.name, from: from, to: spec.to, undo: action === 'remove' && !a.sys ? '' : (spec.undo || '') });
        } else { fails.push(a.name + ': ' + (firstLine(text) || 'failed')); Log.add('FAIL ' + a.pkg + ': ' + firstLine(text), 'err'); }
      }
    } finally { Progress.end(); this.busy = false; }
    UI.stats(); Registry.render(); Tray.update(); if (Tabs.cur === 'debloat') Debloat.render(); if (Tabs.cur === 'vault') Vault.render();
    const rec = changes.length && !o.noUndo ? { ts: Date.now(), label: spec.label + ' ' + changes.length, changes: changes } : null;
    if (rec) Undo.push(rec);
    const msg = (o.label || spec.label) + ': ' + good + ' ok' + (fails.length ? ', ' + fails.length + ' failed' : '') + (skipped.length ? ', ' + skipped.length + ' skipped' : '');
    Toast.show(msg, fails.length ? 'err' : 'ok', rec && rec.changes.some(c => c.undo) ? { action: 'UNDO', fn: () => Undo.apply(0), ms: 9000 } : null);
    if (fails.length) Log.add(fails.join(' | '), 'err');
    if (changes.length) setTimeout(() => Scan.run({ quiet: true }), 900);
    return { ok: good, fail: fails.length };
  },
};

// ═════════════════════════════════════════════════════════════════════════════
// Vault: snapshots, history, pins, settings
// ═════════════════════════════════════════════════════════════════════════════
const Vault = {
  snaps: [],
  text(name, auto, label) {
    const lines = ['# cam-snapshot 1', '# name=' + name.replace(/[\r\n]/g, ' '), '# ts=' + Date.now(), '# auto=' + (auto ? 1 : 0), '# label=' + String(label || '').replace(/[\r\n]/g, ' ')];
    let body = Reg.apps.map(a => a.pkg + '|' + a.state);
    let partial = 0;
    if (body.join('\n').length > 60000) { body = Reg.apps.filter(a => a.state !== 'e').map(a => a.pkg + '|' + a.state); partial = 1; }
    lines.push('# partial=' + partial);
    return lines.join('\n') + '\n' + body.join('\n') + '\n';
  },
  slug(s) { return String(s).toLowerCase().replace(/[^a-z0-9._-]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 40) || 'snap'; },
  save(name, auto, label) {
    const id = (auto ? 'auto-' : 'snap-') + Date.now().toString(36) + '-' + this.slug(name);
    const ok = Store.write('snaps/' + id + '.txt', this.text(name, auto, label));
    if (ok && auto) this.prune();
    return ok ? id : '';
  },
  autoSnapshot(label) { return this.save('before ' + label, true, label); },
  prune() {
    const autos = Store.listSnaps().filter(s => s.id.indexOf('auto-') === 0).sort((a, b) => a.id < b.id ? 1 : -1);
    autos.slice(8).forEach(s => Store.del('snaps/' + s.id + '.txt'));
  },
  parse(text) {
    const meta = {}, states = new Map();
    String(text || '').split('\n').forEach(l => {
      l = l.trim();
      if (!l) return;
      if (l.charAt(0) === '#') { const m = l.match(/^#\s*(\w+)=(.*)$/); if (m) meta[m[1]] = m[2]; return; }
      const p = l.split('|');
      if (p.length === 2 && PKG_RE.test(p[0]) && /^[edr]$/.test(p[1])) states.set(p[0], p[1]);
    });
    return { meta, states };
  },
  read(id) { const t = Store.read('snaps/' + id + '.txt'); return t ? this.parse(t) : null; },
  /** Differences between a snapshot and the current device state. */
  diff(snap) {
    const out = [];
    snap.states.forEach((want, pkg) => {
      const a = Reg.get(pkg);
      if (!a || a.state === want) return;
      let action = '';
      if (want === 'd' && a.state === 'e') action = 'freeze';
      else if (want === 'e' && a.state === 'd') action = 'unfreeze';
      else if (want !== 'r' && a.state === 'r') action = 'restore';
      else if (want === 'r' && a.state !== 'r' && a.sys) action = 'remove';
      if (action) out.push({ pkg, name: a.name, from: a.state, to: want, action });
    });
    return out;
  },
  async restore(id) {
    const snap = this.read(id);
    if (!snap) { Toast.show('Snapshot unreadable', 'err'); return; }
    const d = this.diff(snap);
    if (!d.length) { Toast.show('Device already matches this snapshot', 'ok'); return; }
    const by = {}; d.forEach(x => (by[x.action] = by[x.action] || []).push(x.pkg));
    const lines = Object.keys(by).map(k => k.toUpperCase() + ': ' + by[k].length + ' package(s)');
    const ok = await Modal.ask({ title: 'RESTORE SNAPSHOT', msg: 'Apply ' + d.length + ' change(s) to match "' + (snap.meta.name || id) + '"?', list: lines, confirm: 'APPLY' });
    if (!ok) return;
    Vault.save('before restore', true, 'restore');
    const order = ['restore', 'unfreeze', 'freeze', 'remove'];
    for (const act of order) if (by[act]) await Ops.batch(act, by[act], { noConfirm: true, noSnap: true, label: 'RESTORE' });
  },
  exportJson(id) {
    const s = this.read(id); if (!s) return '';
    const states = {}; s.states.forEach((v, k) => { states[k] = v; });
    return JSON.stringify({ format: 'cam-snapshot-v1', name: s.meta.name || id, ts: +s.meta.ts || 0, partial: s.meta.partial === '1', states: states });
  },
  importJson(text) {
    let j; try { j = JSON.parse(text); } catch (e) { throw new Error('Not valid JSON'); }
    if (!j || j.format !== 'cam-snapshot-v1' || typeof j.states !== 'object') throw new Error('Not a Cyber App Manager snapshot');
    const lines = ['# cam-snapshot 1', '# name=' + String(j.name || 'imported').replace(/[\r\n]/g, ' '), '# ts=' + (+j.ts || Date.now()), '# auto=0', '# label=imported', '# partial=' + (j.partial ? 1 : 0)];
    let n = 0;
    Object.keys(j.states).forEach(p => { if (PKG_RE.test(p) && /^[edr]$/.test(j.states[p])) { lines.push(p + '|' + j.states[p]); n++; } });
    if (!n) throw new Error('Snapshot contains no packages');
    const id = 'snap-' + Date.now().toString(36) + '-' + this.slug(j.name || 'imported');
    if (!Store.write('snaps/' + id + '.txt', lines.join('\n') + '\n')) throw new Error('Could not store snapshot');
    return id;
  },
  render() {
    const box = $('snap-list'); if (!box) return;
    this.snaps = Store.listSnaps().sort((a, b) => a.id < b.id ? 1 : -1);
    const meta = s => { const m = {}; s.head.forEach(l => { const x = l.match(/^#\s*(\w+)=(.*)$/); if (x) m[x[1]] = x[2]; }); return m; };
    box.innerHTML = this.snaps.length ? this.snaps.map(s => {
      const m = meta(s), when = m.ts ? new Date(+m.ts).toLocaleString() : '';
      return '<div class="v-item" data-id="' + esc(s.id) + '"><div class="v-main"><div class="v-name">' + esc(m.name || s.id) + (m.auto === '1' ? ' <span class="tag tag-data">AUTO</span>' : '') + '</div>' +
        '<div class="v-sub">' + esc(when) + ' · ' + s.frozen + ' frozen · ' + s.removed + ' removed · ' + s.total + ' pkgs' + (m.partial === '1' ? ' · partial' : '') + '</div></div>' +
        '<div class="v-btns"><button class="mini" data-v="diff">DIFF</button><button class="mini" data-v="restore">RESTORE</button><button class="mini" data-v="export">EXPORT</button><button class="mini bad" data-v="delete">DEL</button></div></div>';
    }).join('') : '<div class="v-empty">NO SNAPSHOTS YET</div>';

    const drift = Pins.drift();
    $('pin-summary').textContent = Pins.set.size + ' pinned · ' + drift.length + ' re-enabled since';
    $('btn-enforce').disabled = !drift.length;
    $('pin-list').innerHTML = Pins.set.size ? Array.from(Pins.set).sort().map(p => {
      const a = Reg.get(p);
      return '<div class="v-item"><div class="v-main"><div class="v-name">' + esc(a ? a.name : p) + (a && a.state === 'e' ? ' <span class="tag tag-disabled">RE-ENABLED</span>' : '') + '</div><div class="v-sub">' + esc(p) + '</div></div>' +
        '<div class="v-btns"><button class="mini" data-pin="' + esc(p) + '">UNPIN</button></div></div>';
    }).join('') : '<div class="v-empty">NOTHING PINNED</div>';

    $('undo-list').innerHTML = Undo.records.length ? Undo.records.map((r, i) =>
      '<div class="v-item"><div class="v-main"><div class="v-name">' + esc(r.label) + '</div><div class="v-sub">' + esc(new Date(r.ts).toLocaleString()) + ' · ' + r.changes.length + ' change(s)</div></div>' +
      '<div class="v-btns"><button class="mini" data-undo="' + i + '">UNDO</button></div></div>').join('') : '<div class="v-empty">NO RECENT BATCHES</div>';

    ['autoSnap', 'overlays', 'confirm'].forEach(k => { const c = $('set-' + k); if (c) c.checked = !!Settings.v[k]; });
  },
  bind() {
    $('btn-snap-save').addEventListener('click', () => {
      const n = ($('snap-name').value || '').trim() || ('snapshot ' + new Date().toLocaleString());
      const id = this.save(n, false, 'manual');
      Toast.show(id ? 'Snapshot saved' : 'Could not save snapshot', id ? 'ok' : 'err'); $('snap-name').value = ''; this.render();
    });
    $('snap-list').addEventListener('click', async e => {
      const b = e.target.closest('[data-v]'); if (!b) return;
      const id = b.closest('.v-item').dataset.id;
      if (b.dataset.v === 'restore') { await this.restore(id); this.render(); }
      else if (b.dataset.v === 'delete') { if (await Modal.ask({ title: 'DELETE SNAPSHOT', msg: 'Delete this snapshot permanently?', confirm: 'DELETE', danger: true })) { Store.del('snaps/' + id + '.txt'); this.render(); } }
      else if (b.dataset.v === 'export') { const t = this.exportJson(id); $('snap-io').value = t; $('snap-io').select(); Toast.show('JSON is in the box below — copy it', 'ok'); }
      else if (b.dataset.v === 'diff') {
        const s = this.read(id); if (!s) return;
        const d = this.diff(s);
        await Modal.ask({ title: 'DIFF vs NOW', msg: d.length ? d.length + ' difference(s) from this snapshot:' : 'No differences.', list: d.slice(0, 40).map(x => x.name + ': ' + x.from + ' → ' + x.to + '  (' + x.action + ')'), confirm: 'CLOSE' });
      }
    });
    $('btn-snap-import').addEventListener('click', () => {
      try { const id = this.importJson($('snap-io').value); Toast.show('Snapshot imported', 'ok'); $('snap-io').value = ''; this.render(); Log.add('imported ' + id, 'ok'); }
      catch (e) { Toast.show(e.message, 'err'); }
    });
    $('pin-list').addEventListener('click', e => {
      const b = e.target.closest('[data-pin]'); if (!b) return;
      Pins.toggle([b.dataset.pin], false); Registry.render(); this.render(); Tray.update();
    });
    $('btn-enforce').addEventListener('click', () => { Ops.batch('freeze', Pins.drift(), { label: 'ENFORCE' }); });
    $('undo-list').addEventListener('click', e => { const b = e.target.closest('[data-undo]'); if (b) Undo.apply(+b.dataset.undo); });
    ['autoSnap', 'overlays', 'confirm'].forEach(k => $('set-' + k).addEventListener('change', e => {
      Settings.v[k] = e.target.checked; Settings.save(); if (k === 'overlays') Registry.render(true);
    }));
  },
};

// ═════════════════════════════════════════════════════════════════════════════
// Debloat presets
// ═════════════════════════════════════════════════════════════════════════════
const Debloat = {
  eligible(pkgs) { return pkgs.map(p => Reg.get(p)).filter(a => a && a.state === 'e' && !Guard.reason(a.pkg, 'freeze')); },
  smart() { return Reg.apps.filter(a => a.sys && a.state === 'e' && a.risk === 'safe' && a.known && !Guard.reason(a.pkg, 'freeze')); },
  card(id, name, desc, list) {
    return '<div class="d-card" data-id="' + esc(id) + '"><div class="d-head"><span class="d-name">' + esc(name) + '</span><span class="d-count">' + list.length + ' on this device</span></div>' +
      '<p class="d-desc">' + esc(desc) + '</p>' +
      (list.length ? '<div class="d-pkgs">' + list.slice(0, 6).map(a => '<span class="tag tag-safe">' + esc(a.name) + '</span>').join('') + (list.length > 6 ? '<span class="tag tag-data">+' + (list.length - 6) + '</span>' : '') + '</div>' +
        '<div class="d-btns"><button class="reg-btn" data-d="review">REVIEW</button><button class="reg-btn hot" data-d="freeze">FREEZE ' + list.length + '</button></div>'
        : '<div class="d-none">Nothing left to freeze here.</div>') + '</div>';
  },
  render() {
    const box = $('debloat-list'); if (!box) return;
    if (!Reg.apps.length) { box.innerHTML = '<div class="v-empty">SCAN NOT READY</div>'; return; }
    const smart = this.smart();
    let html = this.card('@smart', 'Known-safe candidates', 'Every enabled system package the knowledge base marks as safe to freeze, from any vendor.', smart);
    KB.PRESETS.forEach(p => { html += this.card(p.id, p.name, p.desc, this.eligible(p.pkgs)); });
    box.innerHTML = html;
  },
  bind() {
    $('debloat-list').addEventListener('click', e => {
      const b = e.target.closest('[data-d]'); if (!b) return;
      const id = b.closest('.d-card').dataset.id;
      const list = id === '@smart' ? this.smart() : this.eligible((KB.PRESETS.find(p => p.id === id) || { pkgs: [] }).pkgs);
      if (b.dataset.d === 'review') {
        Sel.clear(); list.forEach(a => Sel.add(a.pkg));
        Registry.view.tab = 'selected';
        document.querySelectorAll('.filter-btn').forEach(x => x.classList.toggle('active', x.dataset.filter === 'selected'));
        Tabs.show('apps'); Registry.render(true); Tray.update();
      } else Ops.batch('freeze', list.map(a => a.pkg), { label: 'DEBLOAT' });
    });
  },
};

// ═════════════════════════════════════════════════════════════════════════════
// Data usage (on-device aggregation of dumpsys netstats — see VOID//WALL for the rationale)
// ═════════════════════════════════════════════════════════════════════════════
const USAGE_SH = [
  "if [ $((4294967296+1)) = 4294967297 ]; then S=1; else S=1000; fi",
  "L=; ok=0; ty=o; cu=; cm=0; cw=0; co=0",
  "flush() { [ -n \"$cu\" ] || return 0; eval \"M$cu=\\$((\\${M$cu:-0}+cm)); W$cu=\\$((\\${W$cu:-0}+cw)); O$cu=\\$((\\${O$cu:-0}+co))\"; case \" $L \" in *\" $cu \"*) ;; *) L=\"$L $cu\";; esac; cu=; cm=0; cw=0; co=0; }",
  "dumpsys netstats detail 2>/dev/null | grep -E '^ *(ident=|st=)' | {",
  "while IFS= read -r l; do",
  "  case \"$l\" in",
  "    *ident=*)",
  "      flush; ok=0",
  "      case \"$l\" in *\" uid=\"*\" tag=\"*)",
  "        u=${l#* uid=}; u=${u%% *}; t=${l#* tag=}; t=${t%% *}",
  "        case \"$u\" in ''|*[!0-9]*) ;; *) [ \"$t\" = 0x0 ] && ok=1;; esac;;",
  "      esac",
  "      if [ $ok = 1 ]; then",
  "        cu=$u",
  "        case \"$l\" in *type=MOBILE*) ty=m;; *type=WIFI*) ty=w;; *) ty=o;; esac",
  "      fi;;",
  "    *)",
  "      [ $ok = 1 ] || continue",
  "      s=${l#*st=}; s=${s%% *}",
  "      [ ${#s} -gt 11 ] && s=${s%???}",
  "      [ \"$s\" -ge @CUT@ ] 2>/dev/null || continue",
  "      r=${l#*rb=}; r=${r%% *}; x=${l#*tb=}; x=${x%% *}",
  "      if [ $S = 1 ]; then b=$((r+x)); else r=${r%???}; x=${x%???}; b=$((${r:-0}+${x:-0})); fi",
  "      case $ty in m) cm=$((cm+b));; w) cw=$((cw+b));; *) co=$((co+b));; esac;;",
  "  esac",
  "done",
  "flush",
  "echo \"S $S\"",
  "for u in $L; do eval \"echo \\\"$u \\${M$u} \\${W$u} \\${O$u}\\\"\"; done",
  "}",
].join('\n');

const Usage = {
  names() {
    const map = {};
    (Bridge.run('pm list packages -U 2>/dev/null', null, true).out || '').split('\n').forEach(l => {
      const m = l.match(/^package:(\S+)\s+uid:(\d+)/); if (m) map[m[2]] = m[1];
    });
    return map;
  },
  rows(cut) {
    const res = Bridge.run(USAGE_SH.replace('@CUT@', String(cut)), { timeoutSeconds: 90 }, true);
    const lines = res.out.split('\n').map(s => s.trim()).filter(Boolean);
    if (!lines.length || lines[0].charAt(0) !== 'S') return { error: 'exit ' + res.code + (res.timedOut ? ' (timed out)' : '') + ' ' + (res.err || ''), rows: [] };
    const scale = parseInt(lines[0].split(' ')[1], 10) || 1, names = this.names();
    const rows = lines.slice(1).map(l => {
      const p = l.split(' '), mobile = (parseInt(p[1], 10) || 0) * scale, wifi = (parseInt(p[2], 10) || 0) * scale, other = (parseInt(p[3], 10) || 0) * scale;
      return { uid: p[0], pkg: names[p[0]] || ('uid:' + p[0]), mobile, wifi, other, bytes: mobile + wifi + other };
    }).filter(r => r.bytes > 0).sort((a, b) => b.bytes - a.bytes);
    return { rows };
  },
  battery() {
    const res = Bridge.run("dumpsys batterystats --checkin 2>/dev/null | grep -E '^[0-9]+,[0-9]+,l,nt,'", { timeoutSeconds: 60 }, true);
    const names = this.names();
    const rows = res.out.split('\n').map(l => l.trim().split(',')).filter(f => f.length > 7).map(f => {
      const n = i => parseInt(f[i], 10) || 0, mobile = n(4) + n(5), wifi = n(6) + n(7);
      return { uid: f[1], pkg: names[f[1]] || ('uid:' + f[1]), mobile, wifi, other: 0, bytes: mobile + wifi };
    }).filter(r => r.bytes > 0).sort((a, b) => b.bytes - a.bytes);
    return { rows };
  },
  probe() {
    const d = 'dumpsys netstats detail 2>&1';
    const cmd = [
      'echo "ident lines: $(' + d + ' | grep -c ident=)"',
      'echo "uid idents: $(' + d + " | grep -cE 'ident=.* uid=[0-9]+ ')\"",
      'echo "bucket lines: $(' + d + " | grep -cE '^ *st=')\"",
      'echo "sections:"; ' + d + " | grep -E '^[A-Za-z][A-Za-z ]*:' | head -12",
      'echo "sample ident:"; ' + d + " | grep -E -m2 'uid=[0-9]+ ' | cut -c1-200",
    ].join('; ');
    return (Bridge.run(cmd, { timeoutSeconds: 90 }, true).out || '').trim() || '(no output)';
  },
  /** mode: boot | day | all | charge */
  collect(mode) {
    if (mode === 'charge') { const b = this.battery(); return b.rows.length ? { rows: b.rows, source: 'battery' } : { rows: [], source: 'battery', probe: 'dumpsys batterystats returned no per-app network lines.' }; }
    const up = parseFloat(((Bridge.run('cat /proc/uptime', null, true).out || '').trim().split(/\s+/)[0])) || 0;
    const now = Math.floor(Date.now() / 1000);
    const cut = mode === 'all' ? 0 : mode === 'day' ? now - 86400 : Math.max(0, now - Math.floor(up) - 7200);
    const first = this.rows(cut);
    if (first.rows.length) return { rows: first.rows, source: 'netstats' };
    const all = cut === 0 ? first : this.rows(0);
    if (all.rows.length) return { rows: [], source: 'netstats', note: 'Android has history for ' + all.rows.length + ' apps, but none inside this range yet. Try "Last 24 hours" or "All recorded history".' };
    const b = this.battery();
    if (b.rows.length) return { rows: b.rows, source: 'battery', note: 'dumpsys netstats has no per-app data on this device, so these are battery-stats totals since the last full charge.' };
    return { rows: [], source: 'none', error: all.error || '', probe: this.probe() };
  },
  split(x) {
    const p = [];
    if (x.mobile) p.push('📶 ' + fmtBytes(x.mobile));
    if (x.wifi) p.push('📡 ' + fmtBytes(x.wifi));
    if (x.other) p.push('🔒 ' + fmtBytes(x.other));
    return p.join(' · ');
  },
  bind() {
    initPicker($('usage-range'), [
      { value: 'boot', label: 'Since last boot' }, { value: 'day', label: 'Last 24 hours' },
      { value: 'all', label: 'All recorded history' }, { value: 'charge', label: 'Since last full charge (battery stats)' },
    ], 'boot');
    $('btn-usage').addEventListener('click', async () => {
      const box = $('usage-result'), note = $('usage-note'), diag = $('usage-diag');
      note.textContent = ''; diag.style.display = 'none'; box.innerHTML = '<div class="loading-state"><div class="spinner"></div><p>READING NETSTATS…</p></div>';
      await tick();
      const r = this.collect($('usage-range').value);
      if (r.note) note.textContent = r.note;
      if (!r.rows.length) {
        box.innerHTML = '<div class="v-empty">NO USAGE DATA FOR THIS RANGE</div>';
        if (r.probe || r.error) { diag.style.display = 'block'; diag.textContent = 'Diagnostics (share this if usage stays empty):\n' + (r.error ? r.error + '\n' : '') + (r.probe || ''); Log.add('usage: ' + (r.error || 'empty'), 'err'); }
        return;
      }
      Reg.apps.forEach(a => { a.bytes = 0; });
      r.rows.forEach(x => { const a = Reg.get(x.pkg); if (a) a.bytes = x.bytes; });
      Reg.usage = r.rows;
      box.innerHTML = r.rows.slice(0, 40).map(x => {
        const a = Reg.get(x.pkg);
        return '<div class="u-row" data-pkg="' + esc(x.pkg) + '"><div class="u-main"><div class="u-name">' + esc(a ? a.name : x.pkg) + '</div><div class="u-sub">' + esc(x.pkg) + ' · ' + Usage.split(x) + '</div></div><b>' + fmtBytes(x.bytes) + '</b></div>';
      }).join('');
      Registry.render();
    });
    $('usage-result').addEventListener('click', e => { const r = e.target.closest('.u-row'); if (r && Reg.get(r.dataset.pkg)) Sheet.open(r.dataset.pkg); });
  },
};

// ═════════════════════════════════════════════════════════════════════════════
// Detail sheet: info, background limits, permissions
// ═════════════════════════════════════════════════════════════════════════════
const BUCKETS = { 5: 'exempted', 10: 'active', 20: 'working set', 30: 'frequent', 40: 'rare', 45: 'restricted', 50: 'never' };
const Sheet = {
  pkg: '',
  parseDump(text) {
    const o = { perms: [] };
    let section = '', inUser = false;
    String(text || '').split('\n').forEach(line => {
      const t = line.trim();
      let m;
      if ((m = t.match(/^versionCode=(\d+)(?:.*targetSdk=(\d+))?/))) { o.versionCode = m[1]; if (m[2]) o.targetSdk = m[2]; }
      else if ((m = t.match(/^versionName=(.+)$/))) o.versionName = m[1];
      else if ((m = t.match(/^firstInstallTime=(.+)$/))) o.installed = m[1];
      else if ((m = t.match(/^lastUpdateTime=(.+)$/))) o.updated = m[1];
      else if ((m = t.match(/^installerPackageName=(.+)$/))) o.installer = m[1];
      else if ((m = t.match(/^User (\d+):/))) {
        inUser = +m[1] === Bridge.user; section = '';
        if (inUser) {
          if ((m = t.match(/\bstopped=(true|false)/))) o.stopped = m[1] === 'true';
          if ((m = t.match(/\benabled=(\d)/))) o.enabledState = +m[1];
        }
      } else if (t === 'install permissions:') section = 'install';
      else if (t === 'runtime permissions:') section = 'runtime';
      else if (section === 'runtime' && inUser && (m = t.match(/^([A-Za-z0-9_.]+): granted=(true|false)/))) o.perms.push({ name: m[1], granted: m[2] === 'true' });
    });
    return o;
  },
  load(pkg) {
    const cmd = 'dumpsys package ' + pkg + " 2>/dev/null | sed -n '/^Packages:/,$p' | grep -E 'versionName=|versionCode=|firstInstallTime=|lastUpdateTime=|installerPackageName=|^ *User [0-9]+:|permissions:|granted=' | head -n 400";
    const r = Bridge.run(cmd, null, true);
    const info = this.parseDump(r.out);
    const bg = Bridge.run('cmd appops get ' + pkg + ' RUN_ANY_IN_BACKGROUND 2>/dev/null', null, true).out;
    const m = bg.match(/RUN_ANY_IN_BACKGROUND:\s*(\w+)/);
    info.bgMode = m ? m[1] : 'default';
    const b = parseInt((Bridge.run('am get-standby-bucket ' + pkg + ' 2>/dev/null', null, true).out || '').trim().split(/\s+/)[0], 10);
    info.bucket = isNaN(b) ? 0 : b;
    return info;
  },
  async open(pkg) {
    const a = Reg.get(pkg); if (!a) return;
    this.pkg = pkg;
    const el = $('sheet-body');
    el.innerHTML = '<div class="loading-state"><div class="spinner"></div><p>READING PACKAGE…</p></div>';
    $('sheet').classList.add('open'); $('sheet-shade').classList.add('open');
    await tick();
    const info = this.load(pkg);
    this.render(a, info);
  },
  close() { $('sheet').classList.remove('open'); $('sheet-shade').classList.remove('open'); this.pkg = ''; },
  render(a, info) {
    const note = a.note ? '<p class="sheet-note"><b>AI NOTE (unverified)</b> ' + esc(a.note) + '</p>' : '';
    const kv = [['VERSION', (info.versionName || '—') + (info.versionCode ? ' (' + info.versionCode + ')' : '')], ['TARGET SDK', info.targetSdk || '—'], ['INSTALLER', info.installer || (a.sys ? 'system image' : '—')],
      ['INSTALLED', info.installed || '—'], ['UPDATED', info.updated || '—'], ['STOPPED', info.stopped === undefined ? '—' : info.stopped ? 'yes' : 'no']];
    const restricted = /^(ignore|deny|errored)$/.test(info.bgMode);
    const perms = info.perms.length ? info.perms.map(p =>
      '<div class="perm"><span class="perm-name">' + esc(p.name.replace(/^android\.permission\.(group\.)?/, '')) + '</span><button class="mini ' + (p.granted ? 'on' : '') + '" data-perm="' + esc(p.name) + '" data-on="' + (p.granted ? 1 : 0) + '">' + (p.granted ? 'GRANTED' : 'DENIED') + '</button></div>').join('')
      : '<div class="v-empty">NO RUNTIME PERMISSIONS</div>';
    const st = a.state;
    $('sheet-body').innerHTML =
      '<div class="sheet-head"><div><div class="sheet-name">' + esc(a.name) + '</div><div class="sheet-pkg">' + esc(a.pkg) + '</div></div><button class="mini" id="sheet-x">×</button></div>' +
      '<div class="app-tags">' + Registry.tagsHtml(a) + '</div>' +
      '<p class="sheet-desc">' + esc(a.desc || '') + '</p>' + note +
      '<div class="kv">' + kv.map(x => '<div><span>' + x[0] + '</span><b>' + esc(x[1]) + '</b></div>').join('') + '</div>' +
      '<div class="sheet-actions">' +
        (st === 'e' ? '<button class="act-btn freeze" data-s="freeze">FREEZE</button>' : '') +
        (st === 'd' ? '<button class="act-btn unfreeze" data-s="unfreeze">UNFREEZE</button>' : '') +
        (st !== 'r' ? '<button class="act-btn forcestop" data-s="stop">FORCE-STOP</button><button class="act-btn restore" data-s="settings">APP INFO</button>' : '') +
        (st === 'r' ? '<button class="act-btn unfreeze" data-s="restore">RESTORE</button>' : '<button class="act-btn uninstall" data-s="remove">REMOVE</button><button class="act-btn uninstall" data-s="clear">CLEAR DATA</button>') +
        '<button class="act-btn restore" data-s="pin">' + (a.pinned ? 'UNPIN' : 'PIN FROZEN') + '</button><button class="act-btn ai" data-s="ask">ASK AI</button>' +
      '</div>' +
      (st !== 'r' ? '<div class="sheet-sec">BACKGROUND</div>' +
        '<div class="perm"><span class="perm-name">Restrict background activity</span><button class="mini ' + (restricted ? 'on' : '') + '" data-bg="' + (restricted ? 1 : 0) + '">' + (restricted ? 'RESTRICTED' : 'ALLOWED') + '</button></div>' +
        '<div class="perm"><span class="perm-name">Standby bucket: ' + esc(BUCKETS[info.bucket] || 'unknown') + '</span><button class="mini" data-bucket="restricted">SET RESTRICTED</button></div>' +
        '<div class="sheet-sec">RUNTIME PERMISSIONS</div>' + perms : '');
  },
  bind() {
    $('sheet-shade').addEventListener('click', () => this.close());
    $('sheet-body').addEventListener('click', async e => {
      if (e.target.id === 'sheet-x') { this.close(); return; }
      const pkg = this.pkg, a = Reg.get(pkg); if (!a) return;
      const s = e.target.closest('[data-s]');
      if (s) {
        const act = s.dataset.s;
        if (act === 'settings') { Bridge.run('am start -a android.settings.APPLICATION_DETAILS_SETTINGS -d package:' + pkg); return; }
        if (act === 'ask') { this.close(); if (CAM.onAsk) CAM.onAsk(pkg); return; }
        if (act === 'pin') { Pins.toggle([pkg], !a.pinned); Registry.render(); Tray.update(); Toast.show(a.pinned ? 'Pinned — re-frozen on every session' : 'Unpinned', 'ok'); this.render(a, this.load(pkg)); return; }
        await Ops.batch(act, [pkg]);
        if (this.pkg === pkg) this.render(Reg.get(pkg), this.load(pkg));
        return;
      }
      const bg = e.target.closest('[data-bg]');
      if (bg) {
        const why = Guard.reason(pkg, 'bg'); if (why) { Toast.show(why, 'err'); return; }
        const restrict = bg.dataset.bg !== '1';
        ['RUN_ANY_IN_BACKGROUND', 'RUN_IN_BACKGROUND'].forEach(op => Bridge.run('cmd appops set ' + pkg + ' ' + op + ' ' + (restrict ? 'ignore' : 'default')));
        this.render(a, this.load(pkg)); return;
      }
      const bk = e.target.closest('[data-bucket]');
      if (bk) {
        const why = Guard.reason(pkg, 'bg'); if (why) { Toast.show(why, 'err'); return; }
        Bridge.run('am set-standby-bucket ' + pkg + ' ' + bk.dataset.bucket);
        this.render(a, this.load(pkg)); return;
      }
      const pm = e.target.closest('[data-perm]');
      if (pm) {
        const why = Guard.reason(pkg, 'perm'); if (why) { Toast.show(why, 'err'); return; }
        const perm = pm.dataset.perm; if (!/^[A-Za-z0-9_.]+$/.test(perm)) return;
        const on = pm.dataset.on === '1';
        const r = Bridge.run('pm ' + (on ? 'revoke' : 'grant') + ' ' + pkg + ' ' + perm);
        if (!r.ok || FAIL_RE.test(r.out + r.err)) Toast.show(firstLine(r.err || r.out) || 'Permission change refused', 'err');
        this.render(a, this.load(pkg));
      }
    });
  },
};

// ═════════════════════════════════════════════════════════════════════════════
// Boot
// ═════════════════════════════════════════════════════════════════════════════
const CAM = { BUILD, $, esc, shq, tick, fmtBytes, firstLine, PKG_RE, LS, Log, Toast, Modal, Progress, initPicker, Tabs, Tray, Bridge, Store, Settings,
              Reg, Sel, Guard, Pins, Scan, Registry, Ops, Undo, Vault, Debloat, Usage, Sheet, onAsk: null, aiBuild: '', hooks: [] };
window.CAM = CAM;

function checkBuild() {
  const meta = document.querySelector('meta[name="cam-build"]');
  const seen = { 'index.html': meta && meta.content, 'script.js': BUILD, 'kb.js': window.KB && window.KB.build, 'ai.js': CAM.aiBuild };
  const off = Object.keys(seen).filter(k => seen[k] !== BUILD);
  if (off.length) {
    const w = $('build-warn');
    w.style.display = 'block';
    w.textContent = '⚠ Files out of sync (' + Object.keys(seen).map(k => k + ' ' + (seen[k] || 'old')).join(', ') + '). Reinstall the full module ZIP, then fully close and reopen the module.';
  }
}

function pills() {
  const t = Bridge.trusted(), mode = Bridge.info && Bridge.info.accessMode;
  $('pill-shell').innerHTML = '<span class="dot cyan"></span>' + (Bridge.uid === '0' ? 'ROOT' : Bridge.uid === '2000' ? 'ADB SHELL' : 'SHELL ' + esc(Bridge.uid));
  $('pill-mode').innerHTML = '<span class="dot green"></span>MODE ' + esc((mode || 'full').toUpperCase());
  $('pill-trust').innerHTML = '<span class="dot ' + (t ? 'green' : 'yellow') + '"></span>' + (t ? 'FULL TRUST' : t === false ? 'NOT TRUSTED' : 'TRUST ?');
}

function bindGlobal() {
  $('modal-cancel').addEventListener('click', () => Modal.close(false));
  $('modal-confirm').addEventListener('click', () => Modal.close(true));
  $('modal-typed').addEventListener('input', e => { $('modal-confirm').disabled = e.target.value.trim().toLowerCase() !== Modal.need; });
  document.querySelectorAll('.tab').forEach(b => b.addEventListener('click', () => Tabs.show(b.dataset.tab)));
  $('console-toggle').addEventListener('click', () => {
    const open = !$('console-drawer').classList.contains('open');
    $('console-drawer').classList.toggle('open', open);
    document.body.classList.toggle('console-open', open);
    $('console-toggle').querySelector('span').textContent = open ? '▼ CONSOLE' : '▲ CONSOLE';
  });
  $('clear-console').addEventListener('click', () => { $('console-body').innerHTML = ''; Log.lines = []; });
  $('copy-console').addEventListener('click', () => { const ta = document.createElement('textarea'); ta.value = Log.text(); document.body.appendChild(ta); ta.select(); try { document.execCommand('copy'); Toast.show('Log copied', 'ok'); } catch (e) { Toast.show('Copy failed', 'err'); } document.body.removeChild(ta); });
  const batch = (id, action) => $(id).addEventListener('click', () => Ops.batch(action, Array.from(Sel)));
  batch('batch-freeze', 'freeze'); batch('batch-unfreeze', 'unfreeze'); batch('batch-forcestop', 'stop'); batch('batch-restore', 'restore'); batch('batch-uninstall', 'remove');
  $('btn-pin').addEventListener('click', () => {
    const pk = Array.from(Sel).filter(p => Reg.get(p) && !Guard.reason(p, 'freeze')); if (!pk.length) { Toast.show('Nothing pinnable selected', 'err'); return; }
    const allPinned = pk.every(p => Pins.set.has(p));
    Pins.toggle(pk, !allPinned); Registry.render(); Tray.update(); Vault.render();
    Toast.show(allPinned ? 'Unpinned ' + pk.length : 'Pinned ' + pk.length + ' — re-frozen on every Shizuku session', 'ok');
  });
  $('btn-refresh').addEventListener('click', () => Scan.run());
  $('btn-smart').addEventListener('click', async () => { await Scan.run(); Debloat.render(); });
}

async function init() {
  bindGlobal(); Registry.bind(); Vault.bind(); Debloat.bind(); Usage.bind(); Sheet.bind();
  Tabs.show('apps');
  checkBuild();
  Log.add('Cyber App Manager ' + BUILD + ' · knowledge base ' + KB.size + ' entries');
  const d = Bridge.detect();
  if (!d.ok) {
    Log.add(d.why, 'err');
    $('app-list').innerHTML = '<div class="loading-state"><p class="bad">SHELL BRIDGE UNAVAILABLE</p><p class="sub">' + esc(d.why) + '</p></div>';
    $('pill-trust').innerHTML = '<span class="dot red"></span>NO BRIDGE';
    Toast.show('Bridge not available', 'err');
    return;
  }
  pills();
  CAM.hooks.forEach(fn => { try { fn(); } catch (e) { Log.add('hook failed: ' + e.message, 'err'); } });
  Log.add('bridge ok · uid=' + Bridge.uid + ' · trusted=' + Bridge.trusted(), 'ok');
  Store.init(); Pins.load();
  await Scan.run();
  const drift = Pins.drift();
  if (drift.length) Toast.show(drift.length + ' pinned package(s) were re-enabled — open VAULT to re-freeze', 'err', { action: 'OPEN', fn: () => Tabs.show('vault'), ms: 7000 });
}

if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init); else init();
})();
