#!/system/bin/sh
# Cyber App Manager — snapshots of package states (enabled / frozen / removed).
# Usage: sh snapshot.sh save [name] | list | show <id> | diff <id> | restore <id> | delete <id>
# Snapshots are plain text files shared with the WebUI: $CAM_STATE/snaps/<id>.txt
. "${MODDIR:-$(dirname "$0")}/lib.sh"

ACTION="$1"
ARG="$2"
DIR="$CAM_STATE/snaps"

snap_path() {
    case "$1" in ''|*/*|*..*) return 1 ;; esac
    [ -f "$DIR/$1.txt" ] && { echo "$DIR/$1.txt"; return 0; }
    return 1
}

slug() { printf '%s' "$1" | tr 'A-Z' 'a-z' | tr -c 'a-z0-9._\n-' '-' | cut -c1-40; }

# delta <snapshot file> <outfile> — lines "package|wanted|current|action" for every difference.
delta() {
    _d="$CAM_STATE/.d.$$"
    mkdir -p "$_d"
    grep -E '^[A-Za-z][A-Za-z0-9_.]*\|[edr]$' "$1" | sort -u > "$_d/want"
    current_states "$_d/cur"
    pm list packages -s 2>/dev/null | sed 's/^package://' | sort -u > "$_d/sys"
    comm -23 "$_d/want" "$_d/cur" > "$_d/diff"
    : > "$2"
    while IFS='|' read -r _p _w; do
        [ -n "$_p" ] || continue
        _c=$(grep "^$_p|" "$_d/cur" | head -n 1 | cut -d'|' -f2)
        [ -n "$_c" ] || continue
        _act=""
        if [ "$_w" = d ] && [ "$_c" = e ]; then _act=freeze
        elif [ "$_w" = e ] && [ "$_c" = d ]; then _act=unfreeze
        elif [ "$_w" != r ] && [ "$_c" = r ]; then _act=restore
        elif [ "$_w" = r ] && [ "$_c" != r ] && grep -qx "$_p" "$_d/sys"; then _act=remove
        fi
        [ -n "$_act" ] && echo "$_p|$_w|$_c|$_act" >> "$2"
    done < "$_d/diff"
    rm -rf "$_d"
}

case "$ACTION" in
    save)
        NAME="${ARG:-snapshot}"
        ID="snap-$(date +%s)-$(slug "$NAME")"
        F="$DIR/$ID.txt"
        {
            echo "# cam-snapshot 1"
            echo "# name=$NAME"
            echo "# ts=$(date +%s)000"
            echo "# auto=0"
            echo "# label=cli"
            echo "# partial=0"
        } > "$F"
        current_states "$CAM_STATE/.cur.$$"
        cat "$CAM_STATE/.cur.$$" >> "$F"
        rm -f "$CAM_STATE/.cur.$$"
        log_event "snapshot saved: $ID"
        echo '{"ok":true,"id":"'"$ID"'","packages":'"$(grep -c '|' "$F")"'}'
        ;;
    list)
        for f in "$DIR"/*.txt; do
            [ -f "$f" ] || continue
            id=$(basename "$f" .txt)
            name=$(sed -n 's/^# name=//p' "$f" | head -n 1)
            echo "$id  frozen=$(grep -c '|d$' "$f")  removed=$(grep -c '|r$' "$f")  total=$(grep -c '|' "$f")  $name"
        done
        ;;
    show)
        F=$(snap_path "$ARG") || { echo '{"ok":false,"error":"snapshot not found"}'; exit 1; }
        cat "$F"
        ;;
    diff)
        F=$(snap_path "$ARG") || { echo '{"ok":false,"error":"snapshot not found"}'; exit 1; }
        delta "$F" "$CAM_STATE/.delta.$$"
        cat "$CAM_STATE/.delta.$$"
        echo "# $(wc -l < "$CAM_STATE/.delta.$$" | tr -dc '0-9') difference(s)"
        rm -f "$CAM_STATE/.delta.$$"
        ;;
    restore)
        F=$(snap_path "$ARG") || { echo '{"ok":false,"error":"snapshot not found"}'; exit 1; }
        delta "$F" "$CAM_STATE/.delta.$$"
        ok=0; bad=0; skip=0
        # restore first (a package must exist before it can be frozen), then unfreeze, freeze, remove
        for act in restore unfreeze freeze remove; do
            while IFS='|' read -r p w c a; do
                [ "$a" = "$act" ] || continue
                if cam_op "$act" "$p"; then ok=$((ok+1)); [ "$act" = restore ] && [ "$w" = d ] && cam_op freeze "$p" >/dev/null; else
                    if [ "$?" -eq 3 ]; then skip=$((skip+1)); else bad=$((bad+1)); fi
                fi
            done < "$CAM_STATE/.delta.$$"
        done
        rm -f "$CAM_STATE/.delta.$$"
        log_event "snapshot restore $ARG: ok=$ok failed=$bad protected=$skip"
        echo '{"ok":true,"applied":'"$ok"',"failed":'"$bad"',"protected":'"$skip"'}'
        ;;
    delete)
        F=$(snap_path "$ARG") || { echo '{"ok":false,"error":"snapshot not found"}'; exit 1; }
        rm -f "$F"
        echo '{"ok":true}'
        ;;
    *)
        echo '{"ok":false,"error":"usage: snapshot.sh save [name] | list | show <id> | diff <id> | restore <id> | delete <id>"}'
        exit 1
        ;;
esac
