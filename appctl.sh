#!/system/bin/sh
. /data/adb/modules/cyber-app-manager/lib.sh
ACTION="$1"
PKG="$2"
FORCE="$3"
[ -z "$ACTION" ] || [ -z "$PKG" ] && { echo '{"ok":false,"error":"missing args"}'; exit 1; }
[ "$FORCE" != "FORCE" ] && is_protected "$PKG" && { echo '{"ok":false,"error":"protected"}'; exit 2; }
ok=true; result=""
case "$ACTION" in
    disable) result=$(pm disable-user --user 0 "$PKG" 2>&1) ;;
    enable)  result=$(pm enable --user 0 "$PKG" 2>&1) ;;
    uninstall) result=$(pm uninstall --user 0 "$PKG" 2>&1) ;;
    force-stop) result=$(am force-stop "$PKG" 2>&1) ;;
    clear-data) result=$(pm clear --user 0 "$PKG" 2>&1) ;;
    *) echo '{"ok":false,"error":"unknown action"}'; exit 1 ;;
esac
[ $? -ne 0 ] && ok=false
log_event "$ACTION $PKG => $result"
[ "$ok" = true ] && echo '{"ok":true}' || echo '{"ok":false,"error":"'"$result"'"}'
