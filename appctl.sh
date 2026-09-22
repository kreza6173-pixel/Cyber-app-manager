#!/system/bin/sh
# Cyber App Manager — single-package control for automation and the Shevery terminal.
# Usage: sh appctl.sh <freeze|unfreeze|force-stop|remove|restore|clear-data> <package>
# (disable, enable and uninstall are accepted as aliases.)
# Prints one line of JSON. Protected packages are always refused.
. "${MODDIR:-$(dirname "$0")}/lib.sh"

ACTION="$1"
PKG="$2"

if [ -z "$ACTION" ] || [ -z "$PKG" ]; then
    echo '{"ok":false,"error":"usage: appctl.sh <freeze|unfreeze|force-stop|remove|restore|clear-data> <package>"}'
    exit 1
fi

cam_op "$ACTION" "$PKG"
rc=$?
if [ "$rc" -eq 0 ]; then
    echo '{"ok":true,"action":"'"$(json_escape "$ACTION")"'","package":"'"$(json_escape "$PKG")"'","message":"'"$(json_escape "$CAM_MSG")"'"}'
else
    echo '{"ok":false,"action":"'"$(json_escape "$ACTION")"'","package":"'"$(json_escape "$PKG")"'","error":"'"$(json_escape "$CAM_MSG")"'"}'
fi
exit "$rc"
