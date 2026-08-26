#!/system/bin/sh
MODDIR="${MODDIR:-/data/adb/modules/cyber-app-manager}"
STATE_DIR="$MODDIR/state"
LOGS_DIR="$MODDIR/logs"
mkdir -p "$STATE_DIR" "$LOGS_DIR"

PROTECTED=" android com.android.systemui com.android.settings com.android.phone com.android.shell com.android.providers.settings com.android.inputmethod.latin com.android.packageinstaller com.google.android.packageinstaller com.google.android.gms com.google.android.gsf com.google.android.packageinstaller "

is_protected() {
    case "$PROTECTED" in
        *" $1 "*) return 0 ;;
    esac
    launcher=$(cmd package resolve-activity -c android.intent.category.HOME 2>/dev/null | grep packageName | head -1 | sed 's/.*packageName=//')
    ime=$(settings get secure default_input_method 2>/dev/null | cut -d/ -f1)
    [ "$1" = "$launcher" ] && return 0
    [ "$1" = "$ime" ] && return 0
    return 1
}

log_event() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" >> "$LOGS_DIR/appctl.log"
}
