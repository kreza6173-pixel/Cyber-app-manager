#!/system/bin/sh
# Cyber App Manager — shared helpers.
# POSIX sh (mksh compatible): no arrays, no [[ ]], no "local".
# Sourced by action.sh, service.sh, appctl.sh and snapshot.sh.

# Module scripts run with MODDIR set by Shevery. State lives outside the module directory
# because the shell identity cannot always write into Shevery's private storage.
CAM_STATE="${CAM_STATE:-/data/local/tmp/cyber-app-manager}"
CAM_LOGS="$CAM_STATE/logs"
mkdir -p "$CAM_STATE/snaps" "$CAM_LOGS" 2>/dev/null

CAM_USER="${CAM_USER:-$(am get-current-user 2>/dev/null | tr -dc '0-9')}"
[ -n "$CAM_USER" ] || CAM_USER=0

log_event() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" >> "$CAM_LOGS/cam.log" 2>/dev/null
}

json_escape() {
    printf '%s' "$1" | tr '\n\r' '  ' | sed 's/\\/\\\\/g; s/"/\\"/g'
}

# Packages the manager refuses to freeze, stop, remove or clear (kept in sync with webui/kb.js).
is_core_pkg() {
    case "$1" in
        android|com.android.systemui|com.android.settings|com.android.phone|com.android.server.*|\
        com.android.shell|com.android.providers.*|com.android.inputmethod.latin|\
        com.android.packageinstaller|com.google.android.packageinstaller|\
        com.android.permissioncontroller|com.google.android.permissioncontroller|\
        com.android.bluetooth|com.android.nfc|com.android.location.fused|com.android.keychain|\
        com.android.vpndialogs|com.android.carrierconfig|com.android.networkstack*|\
        com.google.android.networkstack*|com.google.android.gms|com.google.android.gsf|\
        com.google.android.webview|com.android.webview|com.google.android.trichromelibrary*|\
        com.android.trichromelibrary*|com.android.wifi*|com.android.cts.*|com.android.internal.*|\
        com.android.theme.*|com.hamondev.shevery|moe.shizuku.privileged.api|\
        *.auto_generated_rro_*|*.overlay|*.overlay.*|*_overlay|*.rro|*.resources)
            return 0 ;;
    esac
    return 1
}

# Launcher, keyboard, dialer and SMS app are protected whatever they are called.
CAM_DYN=""
load_dynamic_protection() {
    [ -n "$CAM_DYN" ] && return 0
    _l=$(cmd package resolve-activity --brief -c android.intent.category.HOME 2>/dev/null | tail -n 1 | cut -d/ -f1)
    _i=$(settings get secure default_input_method 2>/dev/null | cut -d/ -f1)
    _d=$(cmd role holders android.app.role.DIALER 2>/dev/null | head -n 1)
    _s=$(cmd role holders android.app.role.SMS 2>/dev/null | head -n 1)
    CAM_DYN=" $_l $_i $_d $_s "
}

is_protected() {
    is_core_pkg "$1" && return 0
    load_dynamic_protection
    case "$CAM_DYN" in *" $1 "*) return 0 ;; esac
    return 1
}

# cam_op <freeze|unfreeze|force-stop|remove|restore|clear-data> <package>
# Sets CAM_MSG. Returns 0 on success, 1 on failure, 2 on bad input, 3 when protected.
cam_op() {
    _a="$1"; _p="$2"; CAM_MSG=""
    case "$_p" in
        ''|[!A-Za-z]*|*[!A-Za-z0-9_.]*) CAM_MSG="invalid package name"; return 2 ;;
    esac
    case "$_a" in
        disable) _a=freeze ;;
        enable) _a=unfreeze ;;
        uninstall) _a=remove ;;
    esac
    case "$_a" in
        freeze|force-stop|remove|clear-data)
            if is_protected "$_p"; then CAM_MSG="protected package"; log_event "$_a $_p REFUSED (protected)"; return 3; fi ;;
    esac
    case "$_a" in
        freeze)     _o=$(pm disable-user --user "$CAM_USER" "$_p" 2>&1); _rc=$? ;;
        unfreeze)   _o=$(pm enable --user "$CAM_USER" "$_p" 2>&1); _rc=$? ;;
        force-stop) _o=$(am force-stop "$_p" 2>&1); _rc=$? ;;
        remove)     _o=$(pm uninstall --user "$CAM_USER" "$_p" 2>&1); _rc=$? ;;
        restore)    _o=$(pm install-existing --user "$CAM_USER" "$_p" 2>&1); _rc=$? ;;
        clear-data) _o=$(pm clear --user "$CAM_USER" "$_p" 2>&1); _rc=$? ;;
        *) CAM_MSG="unknown action"; return 2 ;;
    esac
    CAM_MSG=$(printf '%s' "$_o" | tr '\n' ' ')
    if [ "$_rc" -ne 0 ] || printf '%s' "$_o" | grep -qiE 'exception|failure|error'; then
        log_event "$_a $_p FAILED: $CAM_MSG"
        return 1
    fi
    log_event "$_a $_p ok"
    return 0
}

# current_states <outfile> — writes sorted "package|state" lines (e = enabled, d = frozen, r = removed).
current_states() {
    _t="$CAM_STATE/.tmp.$$"
    mkdir -p "$_t"
    pm list packages -u 2>/dev/null | sed 's/^package://' | sort -u > "$_t/all"
    pm list packages 2>/dev/null | sed 's/^package://' | sort -u > "$_t/inst"
    pm list packages -d 2>/dev/null | sed 's/^package://' | sort -u > "$_t/dis"
    {
        comm -23 "$_t/all" "$_t/inst" | sed 's/$/|r/'
        comm -12 "$_t/inst" "$_t/dis" | sed 's/$/|d/'
        comm -23 "$_t/inst" "$_t/dis" | sed 's/$/|e/'
    } | sort > "$1"
    rm -rf "$_t"
}

# enforce_pinned — freezes every pinned package that is installed but enabled. Prints a summary.
enforce_pinned() {
    _list="$CAM_STATE/enforce.list"
    if [ ! -s "$_list" ]; then echo "pinned: nothing to enforce"; return 0; fi
    _t="$CAM_STATE/.enf.$$"
    mkdir -p "$_t"
    grep -E '^[A-Za-z][A-Za-z0-9_.]*$' "$_list" | sort -u > "$_t/pin"
    pm list packages 2>/dev/null | sed 's/^package://' | sort -u > "$_t/inst"
    pm list packages -d 2>/dev/null | sed 's/^package://' | sort -u > "$_t/dis"
    comm -12 "$_t/pin" "$_t/inst" > "$_t/present"
    comm -23 "$_t/present" "$_t/dis" > "$_t/todo"
    _ok=0; _bad=0; _total=$(wc -l < "$_t/pin" | tr -dc '0-9')
    while IFS= read -r _p; do
        [ -n "$_p" ] || continue
        if cam_op freeze "$_p"; then _ok=$((_ok+1)); else _bad=$((_bad+1)); fi
    done < "$_t/todo"
    _kept=$(( $(wc -l < "$_t/present" | tr -dc '0-9') - _ok - _bad ))
    rm -rf "$_t"
    echo "pinned: $_total listed, $_ok re-frozen, $_bad failed, $_kept already frozen"
    log_event "enforce: total=$_total frozen=$_ok failed=$_bad"
    [ "$_bad" -eq 0 ]
}
