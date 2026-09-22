#!/system/bin/sh
# Cyber App Manager — re-applies the pinned-frozen list.
# Shevery runs this once per Shizuku session (when allowed by the module's access policy),
# so packages that an update or the OEM re-enabled are frozen again.
. "${MODDIR:-$(dirname "$0")}/lib.sh"
enforce_pinned
