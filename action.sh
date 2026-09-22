#!/system/bin/sh
# Cyber App Manager — quick status (tap the module card → Action).
. "${MODDIR:-$(dirname "$0")}/lib.sh"

total=$(pm list packages 2>/dev/null | wc -l | tr -dc '0-9')
system=$(pm list packages -s 2>/dev/null | wc -l | tr -dc '0-9')
user=$(pm list packages -3 2>/dev/null | wc -l | tr -dc '0-9')
frozen=$(pm list packages -d 2>/dev/null | wc -l | tr -dc '0-9')
removed=$(( $(pm list packages -u 2>/dev/null | wc -l | tr -dc '0-9') - total ))
snaps=$(ls "$CAM_STATE/snaps" 2>/dev/null | wc -l | tr -dc '0-9')

echo "CYBER APP MANAGER"
echo "-----------------"
echo "Installed : $total"
echo "System    : $system"
echo "User      : $user"
echo "Frozen    : $frozen"
echo "Removed   : $removed"
echo "Snapshots : $snaps"
echo ""
enforce_pinned
echo ""
echo "Open the WebUI for full control."
