#!/system/bin/sh
. /data/adb/modules/cyber-app-manager/lib.sh
ACTION="$1"; NAME="$2"
case "$ACTION" in
    save)
        [ -z "$NAME" ] && NAME="snap_$(date +%Y%m%d_%H%M%S)"
        FILE="$STATE_DIR/snap_${NAME}.txt"
        pm list packages -u | sed 's/package://g' | while read pkg; do
            st="enabled"; pm list packages -d | grep -q "^package:${pkg}$" && st="disabled"
            echo "${pkg}|${st}"
        done > "$FILE"
        echo "$NAME" >> "$STATE_DIR/snapshots.list"
        echo '{"ok":true,"name":"'"$NAME"'"}'
        ;;
    list)
        echo '{"snapshots":['
        first=1
        while IFS= read -r s; do [ -z "$s" ] && continue; [ "$first" = 1 ] || echo ","; first=0; echo '"'"$s"'"'; done < "$STATE_DIR/snapshots.list"
        echo ']}'
        ;;
    *) echo '{"ok":false,"error":"usage: snapshot.sh save|list [name]"}' ;;
esac
