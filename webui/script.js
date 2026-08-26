/**
 * Cyber App Manager v1.1.2
 * Shevery ADB Module - WebUI
 *
 * FIXED: Uses window.Shizuku.exec() per official Shevery API
 * exec() returns JSON: {ok, exitCode, stdout, stderr, timedOut}
 */

const App = {
    apps: [], filtered: [], selected: new Set(),
    filter: 'all', bridgeReady: false,
    consoleOpen: false, pendingAction: null,

    init() {
        this.bindEvents();
        this.detectBridge();
    },

    // ─── BRIDGE DETECTION ───
    detectBridge() {
        this.log('Checking for window.Shizuku...');
        if (typeof window.Shizuku === 'undefined') {
            this.bridgeReady = false;
            this.log('window.Shizuku NOT FOUND', 'err');
            document.getElementById('bridge-status').textContent =
                'window.Shizuku missing. Enable WebUI Shell Bridge in module settings.';
            document.getElementById('bridge-status').style.color = 'var(--red)';
            this.showToast('Bridge not found. Check module settings.', 'err');
            return;
        }
        // Test with a simple command
        try {
            const testJson = window.Shizuku.exec('id');
            const test = JSON.parse(testJson);
            if (test.ok) {
                this.bridgeReady = true;
                this.log('window.Shizuku OK. uid=' + test.stdout.trim(), 'ok');
                document.getElementById('trust-pill').innerHTML =
                    '<span class="dot green"></span>FULL TRUST';
                document.getElementById('bridge-status').textContent =
                    'Bridge: window.Shizuku | uid: ' + test.stdout.trim();
                document.getElementById('bridge-status').style.color = 'var(--green)';
                setTimeout(() => this.loadApps(), 200);
            } else {
                throw new Error(test.stderr || 'exec returned ok=false');
            }
        } catch (e) {
            this.bridgeReady = false;
            this.log('window.Shizuku test failed: ' + e.message, 'err');
            document.getElementById('bridge-status').textContent =
                'Bridge error: ' + e.message;
            document.getElementById('bridge-status').style.color = 'var(--red)';
        }
    },

    // ─── EXEC: window.Shizuku.exec returns JSON string ───
    exec(cmd) {
        if (!this.bridgeReady) throw new Error('No bridge');
        this.log('> ' + cmd);
        const raw = window.Shizuku.exec(cmd);
        const result = JSON.parse(raw);
        const out = result.stdout || '';
        const preview = out.substring(0, 120).replace(/\n/g, '\\n');
        this.log('< exit=' + result.exitCode + ' | ' + preview + (out.length > 120 ? '...' : ''), result.ok ? 'ok' : 'err');
        if (!result.ok) throw new Error(result.stderr || 'exit ' + result.exitCode);
        return out;
    },

    // ─── LOAD APPS: direct pm via Shizuku bridge ───
    async loadApps() {
        const listEl = document.getElementById('app-list');
        listEl.innerHTML = '<div class="loading-state"><div class="spinner"></div><p>SCANNING PACKAGES...</p></div>';

        try {
            const [allRaw, sysRaw, disRaw, userRaw] = [
                this.exec('pm list packages -u'),
                this.exec('pm list packages -s'),
                this.exec('pm list packages -d'),
                this.exec('pm list packages -3')
            ];

            this.log('Raw: all=' + allRaw.length + ' sys=' + sysRaw.length + ' dis=' + disRaw.length + ' user=' + userRaw.length);

            const allSet = this._parseLines(allRaw);
            const sysSet = this._parseLines(sysRaw);
            const disSet = this._parseLines(disRaw);
            const userSet = this._parseLines(userRaw);

            if (allSet.size === 0) {
                throw new Error('pm list packages returned empty');
            }

            const protectedSet = new Set([
                'android','com.android.systemui','com.android.settings','com.android.phone',
                'com.android.shell','com.android.providers.settings','com.android.inputmethod.latin',
                'com.android.packageinstaller','com.google.android.packageinstaller',
                'com.google.android.gms','com.google.android.gsf'
            ]);

            this.apps = [];
            let idx = 1;
            for (const pkg of allSet) {
                const isSys = sysSet.has(pkg);
                const isDis = disSet.has(pkg);
                this.apps.push({
                    id: idx++, package: pkg,
                    type: isSys ? 'system' : 'user',
                    status: isDis ? 'disabled' : 'enabled',
                    protected: protectedSet.has(pkg),
                    selected: false
                });
            }

            this.updateCounts();
            this.renderApps();
            this.showToast('Loaded ' + this.apps.length + ' packages');

        } catch (e) {
            this.log('SCAN FAILED: ' + e.message, 'err');
            listEl.innerHTML = `
                <div class="loading-state">
                    <p style="color:var(--red);font-size:16px">SCAN FAILED</p>
                    <p class="sub">${e.message}</p>
                    <button class="reg-btn" style="margin-top:16px" onclick="App.loadApps()">RETRY</button>
                </div>`;
            this.showToast(e.message, 'err');
        }
    },

    _parseLines(raw) {
        const set = new Set();
        if (!raw) return set;
        for (const line of raw.split(/\r?\n/)) {
            const pkg = line.replace(/^package:/, '').trim();
            if (pkg) set.add(pkg);
        }
        return set;
    },

    updateCounts() {
        const total = this.apps.length;
        const enabled = this.apps.filter(a => a.status === 'enabled').length;
        const frozen = this.apps.filter(a => a.status === 'disabled').length;
        const sys = this.apps.filter(a => a.type === 'system').length;
        const user = this.apps.filter(a => a.type === 'user').length;

        document.getElementById('stat-total').textContent = total;
        document.getElementById('stat-enabled').textContent = enabled;
        document.getElementById('stat-frozen').textContent = frozen;
        document.getElementById('stat-system').textContent = sys;

        document.getElementById('count-all').textContent = total;
        document.getElementById('count-user').textContent = user;
        document.getElementById('count-system').textContent = sys;
        document.getElementById('count-disabled').textContent = frozen;

        setTimeout(() => {
            document.getElementById('bar-total').style.width = '100%';
            document.getElementById('bar-enabled').style.width = total ? Math.round((enabled/total)*100) + '%' : '0%';
            document.getElementById('bar-frozen').style.width = total ? Math.round((frozen/total)*100) + '%' : '0%';
            document.getElementById('bar-system').style.width = total ? Math.round((sys/total)*100) + '%' : '0%';
        }, 100);
    },

    renderApps() {
        const query = (document.getElementById('search-input').value || '').toLowerCase();
        this.filtered = this.apps.filter(app => {
            const matchF = this.filter === 'all' ||
                (this.filter === 'system' && app.type === 'system') ||
                (this.filter === 'user' && app.type === 'user') ||
                (this.filter === 'disabled' && app.status === 'disabled');
            return matchF && (!query || app.package.toLowerCase().includes(query));
        });

        const listEl = document.getElementById('app-list');
        if (this.filtered.length === 0) {
            listEl.innerHTML = '<div class="loading-state"><p>NO PACKAGES FOUND</p></div>';
            return;
        }

        listEl.innerHTML = '';
        this.filtered.forEach(app => {
            const el = document.createElement('div');
            el.className = 'app-item' + (app.selected ? ' selected' : '');
            const tags = [];
            if (app.type === 'system') tags.push('<span class="tag tag-system">SYSTEM</span>');
            tags.push(app.status === 'enabled'
                ? '<span class="tag tag-enabled">ENABLED</span>'
                : '<span class="tag tag-disabled">FROZEN</span>');
            if (app.protected) tags.push('<span class="tag tag-protected">PROTECTED</span>');

            el.innerHTML = `
                <div class="app-check ${app.selected ? 'checked' : ''}" data-pkg="${app.package}"></div>
                <div class="app-idx">${String(app.id).padStart(3, '0')}</div>
                <div class="app-info">
                    <div class="app-name">${app.package}</div>
                    <div class="app-tags">${tags.join('')}</div>
                </div>
                <button class="app-open" data-pkg="${app.package}">OPEN</button>`;

            el.querySelector('.app-check').addEventListener('click', (e) => {
                e.stopPropagation(); this.toggleSelect(app.package);
            });
            el.addEventListener('click', () => this.toggleSelect(app.package));
            el.querySelector('.app-open').addEventListener('click', (e) => {
                e.stopPropagation();
                this.exec('am start -a android.settings.APPLICATION_DETAILS_SETTINGS -d package:' + app.package).catch(()=>{});
            });
            listEl.appendChild(el);
        });
    },

    toggleSelect(pkg) {
        const app = this.apps.find(a => a.package === pkg);
        if (!app) return;
        app.selected = !app.selected;
        app.selected ? this.selected.add(pkg) : this.selected.delete(pkg);
        this.renderApps();
        document.getElementById('selected-count').textContent = this.selected.size + ' SELECTED';
    },

    async runAction(action, pkgs) {
        if (!pkgs || pkgs.length === 0) { this.showToast('No packages selected', 'err'); return; }
        for (const pkg of pkgs) {
            try {
                let out;
                try {
                    out = this.exec('sh /data/adb/modules/cyber-app-manager/appctl.sh ' + action + ' ' + pkg);
                } catch (e) {
                    this.log('appctl.sh failed, direct pm fallback', 'warn');
                    let cmd;
                    if (action === 'disable') cmd = 'pm disable-user --user 0 ' + pkg;
                    else if (action === 'enable') cmd = 'pm enable --user 0 ' + pkg;
                    else if (action === 'uninstall') cmd = 'pm uninstall --user 0 ' + pkg;
                    else if (action === 'force-stop') cmd = 'am force-stop ' + pkg;
                    out = this.exec(cmd);
                }
                let resp = {ok: true};
                try { resp = JSON.parse(out); } catch(e) {}
                if (!resp.ok) this.log('FAIL ' + pkg + ': ' + (resp.error || out), 'err');
                else this.log('OK ' + pkg, 'ok');
            } catch (e) {
                this.log('ERR ' + pkg + ': ' + e.message, 'err');
            }
        }
        this.selected.clear(); this.apps.forEach(a => a.selected = false);
        document.getElementById('selected-count').textContent = '0 SELECTED';
        this.loadApps();
    },

    confirm(title, msg, onConfirm) {
        this.pendingAction = onConfirm;
        document.getElementById('modal-title').textContent = title;
        document.getElementById('modal-msg').textContent = msg;
        document.getElementById('modal-overlay').classList.add('active');
    },
    closeModal() { document.getElementById('modal-overlay').classList.remove('active'); this.pendingAction = null; },

    bindEvents() {
        document.getElementById('search-input').addEventListener('input', () => this.renderApps());
        document.getElementById('search-clear').addEventListener('click', () => {
            document.getElementById('search-input').value = ''; this.renderApps();
        });
        document.querySelectorAll('.filter-btn').forEach(btn => {
            btn.addEventListener('click', (e) => {
                document.querySelectorAll('.filter-btn').forEach(b => b.classList.remove('active'));
                e.currentTarget.classList.add('active');
                this.filter = e.currentTarget.dataset.filter; this.renderApps();
            });
        });
        document.getElementById('select-page').addEventListener('click', () => {
            this.filtered.forEach(a => { a.selected = true; this.selected.add(a.package); });
            this.renderApps(); document.getElementById('selected-count').textContent = this.selected.size + ' SELECTED';
        });
        document.getElementById('clear-selection').addEventListener('click', () => {
            this.selected.clear(); this.apps.forEach(a => a.selected = false);
            this.renderApps(); document.getElementById('selected-count').textContent = '0 SELECTED';
        });

        const batch = (id, action, title, msgFn) => {
            document.getElementById(id).addEventListener('click', () => {
                const pkgs = Array.from(this.selected);
                if (action === 'uninstall') {
                    const ok = pkgs.filter(p => { const a = this.apps.find(x => x.package === p); return a && !a.protected && a.type === 'user'; });
                    if (ok.length === 0) { this.showToast('No uninstallable packages', 'err'); return; }
                    this.confirm('UNINSTALL', 'Remove ' + ok.length + ' package(s)?', () => this.runAction('uninstall', ok));
                    return;
                }
                this.confirm(title, msgFn(pkgs.length), () => this.runAction(action, pkgs));
            });
        };
        batch('batch-freeze', 'disable', 'FREEZE', n => 'Freeze ' + n + ' package(s)?');
        batch('batch-unfreeze', 'enable', 'UNFREEZE', n => 'Enable ' + n + ' package(s)?');
        batch('batch-forcestop', 'force-stop', 'FORCE-STOP', n => 'Force-stop ' + n + ' package(s)?');
        document.getElementById('batch-restore').addEventListener('click', () => this.showToast('Use snapshot feature'));

        document.getElementById('console-toggle').addEventListener('click', () => {
            this.consoleOpen = !this.consoleOpen;
            document.getElementById('console-drawer').classList.toggle('open', this.consoleOpen);
            document.getElementById('console-toggle').querySelector('span').textContent = this.consoleOpen ? '▼ CONSOLE' : '▲ CONSOLE';
        });
        document.getElementById('clear-console').addEventListener('click', () => {
            document.getElementById('console-body').innerHTML = '';
        });
        document.getElementById('modal-cancel').addEventListener('click', () => this.closeModal());
        document.getElementById('modal-confirm').addEventListener('click', () => {
            if (this.pendingAction) this.pendingAction(); this.closeModal();
        });
    },

    log(msg, type) {
        const body = document.getElementById('console-body');
        const line = document.createElement('div');
        line.className = 'log-line ' + (type || '');
        line.textContent = msg;
        body.appendChild(line); body.scrollTop = body.scrollHeight;
        console.log('[CAM]', msg);
    },
    showToast(msg, type) {
        const t = document.getElementById('toast');
        t.textContent = msg;
        t.className = 'toast' + (type === 'err' ? ' err' : '');
        t.classList.add('show');
        setTimeout(() => t.classList.remove('show'), 3000);
    }
};

document.addEventListener('DOMContentLoaded', () => App.init());
