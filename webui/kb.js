/**
 * Cyber App Manager — package knowledge base
 *
 * Community-style guidance, not a guarantee: behaviour differs between ROMs and versions.
 * Each entry:  [package, friendly name, category, risk, one-line note]
 *   risk: core    — never touched by the manager (system would break)
 *         caution — disabling may remove a feature you use
 *         safe    — commonly disabled without side effects
 */
(function () {
'use strict';

const ENTRIES = [
  // ── Android core (protected) ──────────────────────────────────────────────
  ['android', 'Android System', 'core', 'core', 'The Android framework itself.'],
  ['com.android.systemui', 'System UI', 'core', 'core', 'Status bar, notifications and navigation.'],
  ['com.android.settings', 'Settings', 'core', 'core', 'System settings app.'],
  ['com.android.phone', 'Phone Services', 'core', 'core', 'Telephony stack and SIM handling.'],
  ['com.android.server.telecom', 'Telecom', 'core', 'core', 'Call routing service.'],
  ['com.android.shell', 'Shell', 'core', 'core', 'The ADB shell identity this module runs as.'],
  ['com.android.providers.settings', 'Settings Storage', 'core', 'core', 'Stores system settings.'],
  ['com.android.providers.contacts', 'Contacts Storage', 'core', 'core', 'Contacts database.'],
  ['com.android.providers.telephony', 'Telephony Storage', 'core', 'core', 'SMS / MMS database.'],
  ['com.android.providers.media', 'Media Storage', 'core', 'core', 'Media index used by every gallery and player.'],
  ['com.android.providers.media.module', 'Media Storage', 'core', 'core', 'Media index used by every gallery and player.'],
  ['com.android.providers.downloads', 'Download Manager', 'core', 'core', 'Handles downloads system-wide.'],
  ['com.android.providers.calendar', 'Calendar Storage', 'core', 'core', 'Calendar database.'],
  ['com.android.inputmethod.latin', 'AOSP Keyboard', 'core', 'core', 'Default keyboard on AOSP builds.'],
  ['com.android.packageinstaller', 'Package Installer', 'core', 'core', 'Installs and uninstalls apps.'],
  ['com.google.android.packageinstaller', 'Package Installer', 'core', 'core', 'Installs and uninstalls apps.'],
  ['com.android.permissioncontroller', 'Permission Controller', 'core', 'core', 'Runtime permission dialogs.'],
  ['com.google.android.permissioncontroller', 'Permission Controller', 'core', 'core', 'Runtime permission dialogs.'],
  ['com.android.bluetooth', 'Bluetooth', 'core', 'core', 'Bluetooth stack.'],
  ['com.android.nfc', 'NFC Service', 'core', 'core', 'NFC stack.'],
  ['com.android.location.fused', 'Fused Location', 'core', 'core', 'Location provider.'],
  ['com.android.keychain', 'Key Chain', 'core', 'core', 'Credential storage.'],
  ['com.android.vpndialogs', 'VPN Dialogs', 'core', 'core', 'Consent dialog every VPN app depends on.'],
  ['com.android.carrierconfig', 'Carrier Config', 'core', 'core', 'Carrier settings.'],
  ['com.android.networkstack', 'Network Stack', 'core', 'core', 'Connectivity checks and DHCP.'],
  ['com.google.android.networkstack', 'Network Stack', 'core', 'core', 'Connectivity checks and DHCP.'],
  ['com.android.networkstack.tethering', 'Tethering', 'core', 'core', 'Hotspot and tethering.'],
  ['com.google.android.networkstack.tethering', 'Tethering', 'core', 'core', 'Hotspot and tethering.'],
  ['com.google.android.gms', 'Google Play services', 'google', 'core', 'Push, location, sign-in and most Google-dependent apps.'],
  ['com.google.android.gsf', 'Google Services Framework', 'google', 'core', 'Required by Google Play services.'],
  ['com.google.android.webview', 'Android System WebView', 'core', 'core', 'Renders web content inside apps.'],
  ['com.android.webview', 'Android System WebView', 'core', 'core', 'Renders web content inside apps.'],
  ['com.hamondev.shevery', 'Shevery', 'core', 'core', 'The manager that hosts this module.'],
  ['moe.shizuku.privileged.api', 'Shizuku', 'core', 'core', 'Provides the shell access this module uses.'],

  // ── AOSP components (feature loss possible) ───────────────────────────────
  ['com.android.vending', 'Google Play Store', 'google', 'caution', 'App store and update service.'],
  ['com.android.launcher3', 'Launcher3', 'aosp', 'caution', 'AOSP home screen.'],
  ['com.android.documentsui', 'Files (documents)', 'aosp', 'caution', 'System file picker.'],
  ['com.android.certinstaller', 'Certificate Installer', 'aosp', 'caution', 'Installs certificates and VPN profiles.'],
  ['com.android.captiveportallogin', 'Captive Portal Login', 'aosp', 'caution', 'Sign-in page for public Wi-Fi.'],
  ['com.android.emergency', 'Emergency Info', 'aosp', 'caution', 'Emergency contacts on the lock screen.'],
  ['com.android.cellbroadcastreceiver', 'Emergency Alerts', 'aosp', 'caution', 'Government / carrier alerts.'],
  ['com.android.stk', 'SIM Toolkit', 'aosp', 'caution', 'Carrier SIM menus.'],
  ['com.android.mms.service', 'MMS Service', 'aosp', 'caution', 'Multimedia messaging.'],
  ['com.android.musicfx', 'MusicFX', 'aosp', 'caution', 'Equalizer used by music apps.'],
  ['com.android.soundpicker', 'Sound Picker', 'aosp', 'caution', 'Ringtone and notification picker.'],
  ['com.android.deskclock', 'Clock', 'aosp', 'caution', 'Alarms and timers.'],
  ['com.android.calculator2', 'Calculator', 'aosp', 'caution', 'AOSP calculator.'],
  ['com.android.contacts', 'Contacts', 'aosp', 'caution', 'AOSP contacts app.'],
  ['com.android.dialer', 'Phone', 'aosp', 'caution', 'AOSP dialer.'],
  ['com.android.mms', 'Messaging', 'aosp', 'caution', 'AOSP SMS app.'],
  ['com.android.camera2', 'Camera', 'aosp', 'caution', 'AOSP camera.'],
  ['com.android.gallery3d', 'Gallery', 'aosp', 'caution', 'AOSP gallery.'],
  ['com.android.chrome', 'Chrome', 'google', 'caution', 'Web browser (also a WebView provider on some builds).'],
  ['com.android.printspooler', 'Print Spooler', 'aosp', 'safe', 'Printing framework. Safe if you never print.'],
  ['com.android.bips', 'Default Print Service', 'aosp', 'safe', 'Printing. Safe if you never print.'],
  ['com.android.egg', 'Android Easter Egg', 'aosp', 'safe', 'Hidden mini-game.'],
  ['com.android.wallpaper.livepicker', 'Live Wallpaper Picker', 'aosp', 'safe', 'Only needed to pick live wallpapers.'],
  ['com.android.dreams.basic', 'Basic Screensaver', 'aosp', 'safe', 'Daydream screensaver.'],
  ['com.android.dreams.phototable', 'Photo Screensaver', 'aosp', 'safe', 'Daydream screensaver.'],
  ['com.android.bookmarkprovider', 'Bookmark Provider', 'aosp', 'safe', 'Legacy browser bookmarks.'],
  ['com.android.providers.partnerbookmarks', 'Partner Bookmarks', 'aosp', 'safe', 'Preloaded carrier bookmarks.'],
  ['com.android.htmlviewer', 'HTML Viewer', 'aosp', 'safe', 'Opens local .html files.'],
  ['com.android.traceur', 'System Tracing', 'aosp', 'safe', 'Developer tracing tool.'],

  // ── Google apps ───────────────────────────────────────────────────────────
  ['com.google.android.apps.tachyon', 'Google Meet', 'google', 'safe', 'Video calling.'],
  ['com.google.android.youtube', 'YouTube', 'google', 'safe', 'Video app.'],
  ['com.google.android.apps.youtube.music', 'YouTube Music', 'google', 'safe', 'Music streaming.'],
  ['com.google.android.videos', 'Google TV', 'google', 'safe', 'Movies and TV store.'],
  ['com.google.android.music', 'Play Music (legacy)', 'google', 'safe', 'Discontinued music app.'],
  ['com.google.android.apps.subscriptions.red', 'Google One', 'google', 'safe', 'Storage subscription app.'],
  ['com.google.android.apps.magazines', 'Google News', 'google', 'safe', 'News reader.'],
  ['com.google.android.apps.podcasts', 'Google Podcasts', 'google', 'safe', 'Podcast player.'],
  ['com.google.android.apps.books', 'Play Books', 'google', 'safe', 'E-book reader.'],
  ['com.google.android.keep', 'Google Keep', 'google', 'safe', 'Notes app.'],
  ['com.google.android.apps.docs', 'Google Drive', 'google', 'caution', 'Cloud files.'],
  ['com.google.android.apps.docs.editors.docs', 'Google Docs', 'google', 'safe', 'Document editor.'],
  ['com.google.android.apps.docs.editors.sheets', 'Google Sheets', 'google', 'safe', 'Spreadsheet editor.'],
  ['com.google.android.apps.docs.editors.slides', 'Google Slides', 'google', 'safe', 'Presentation editor.'],
  ['com.google.android.apps.photos', 'Google Photos', 'google', 'caution', 'Photo backup and gallery.'],
  ['com.google.android.gm', 'Gmail', 'google', 'caution', 'Email client.'],
  ['com.google.android.calendar', 'Google Calendar', 'google', 'caution', 'Calendar app.'],
  ['com.google.android.apps.maps', 'Google Maps', 'google', 'caution', 'Maps and navigation.'],
  ['com.google.android.apps.messaging', 'Google Messages', 'google', 'caution', 'SMS / RCS app.'],
  ['com.google.android.dialer', 'Google Phone', 'google', 'caution', 'Dialer.'],
  ['com.google.android.contacts', 'Google Contacts', 'google', 'caution', 'Contacts app.'],
  ['com.google.android.googlequicksearchbox', 'Google app / Assistant', 'google', 'caution', 'Search, Assistant and the default feed.'],
  ['com.google.android.apps.wellbeing', 'Digital Wellbeing', 'google', 'safe', 'Screen-time tracking and focus modes.'],
  ['com.google.android.apps.turbo', 'Device Health Services', 'google', 'caution', 'Battery insights and adaptive battery.'],
  ['com.google.android.feedback', 'Market Feedback Agent', 'google', 'safe', 'Sends install feedback to Google.'],
  ['com.google.android.partnersetup', 'Google Partner Setup', 'google', 'safe', 'First-run partner configuration.'],
  ['com.google.android.onetimeinitializer', 'Google One Time Init', 'google', 'safe', 'First-boot helper.'],
  ['com.google.android.printservice.recommendation', 'Print Service Recommendation', 'google', 'safe', 'Suggests print plugins.'],
  ['com.google.android.syncadapters.contacts', 'Google Contacts Sync', 'google', 'caution', 'Syncs contacts with your account.'],
  ['com.google.android.syncadapters.calendar', 'Google Calendar Sync', 'google', 'caution', 'Syncs calendar with your account.'],
  ['com.google.android.projection.gearhead', 'Android Auto', 'google', 'safe', 'Car integration.'],
  ['com.google.ar.core', 'Google Play Services for AR', 'google', 'safe', 'Needed only by AR apps.'],
  ['com.google.android.marvin.talkback', 'TalkBack', 'google', 'caution', 'Screen reader (accessibility).'],
  ['com.google.android.tts', 'Speech Services by Google', 'google', 'caution', 'Text-to-speech engine.'],
  ['com.google.android.inputmethod.latin', 'Gboard', 'google', 'caution', 'Google keyboard.'],
  ['com.google.android.apps.nbu.files', 'Files by Google', 'google', 'caution', 'File manager.'],
  ['com.google.android.ext.services', 'Android Services Library', 'google', 'caution', 'Notification ranking and other system helpers.'],
  ['com.google.android.ext.shared', 'Android Shared Library', 'google', 'caution', 'Shared library for system helpers.'],
  ['com.google.android.as', 'Android System Intelligence', 'google', 'caution', 'On-device suggestions and smart features.'],
  ['com.google.android.setupwizard', 'Setup Wizard', 'google', 'caution', 'Only used during first setup.'],

  // ── Facebook preinstalled stubs ───────────────────────────────────────────
  ['com.facebook.appmanager', 'Facebook App Manager', 'social', 'safe', 'Preinstalled helper that keeps the Facebook app updated.'],
  ['com.facebook.services', 'Facebook Services', 'social', 'safe', 'Preinstalled background service.'],
  ['com.facebook.system', 'Facebook App Installer', 'social', 'safe', 'Preinstalled installer stub.'],

  // ── Xiaomi / MIUI / HyperOS ───────────────────────────────────────────────
  ['com.miui.analytics', 'Xiaomi Analytics', 'oem', 'safe', 'Usage telemetry.'],
  ['com.miui.msa.global', 'MSA (system ads)', 'oem', 'safe', 'Ad-delivery service.'],
  ['com.miui.systemAdSolution', 'Ad Solution', 'oem', 'safe', 'Ad-delivery service.'],
  ['com.xiaomi.mipicks', 'GetApps', 'oem', 'safe', 'Xiaomi app store with promotions.'],
  ['com.miui.player', 'Mi Music', 'oem', 'safe', 'Xiaomi music player.'],
  ['com.miui.videoplayer', 'Mi Video', 'oem', 'safe', 'Xiaomi video player.'],
  ['com.miui.video', 'Mi Video', 'oem', 'safe', 'Xiaomi video player.'],
  ['com.miui.yellowpage', 'Yellow Pages', 'oem', 'safe', 'Caller ID and business directory.'],
  ['com.miui.bugreport', 'Bug Report', 'oem', 'safe', 'Feedback and bug-report tool.'],
  ['com.miui.hybrid', 'Quick Apps', 'oem', 'safe', 'Instant-app platform.'],
  ['com.miui.hybrid.accessory', 'Quick Apps Service', 'oem', 'safe', 'Quick Apps helper.'],
  ['com.mi.globalbrowser', 'Mi Browser', 'oem', 'safe', 'Xiaomi web browser.'],
  ['com.xiaomi.finddevice', 'Find Device', 'oem', 'caution', 'Locate, lock or wipe the phone remotely.'],
  ['com.miui.securitycenter', 'Security', 'oem', 'caution', 'Core MIUI / HyperOS security and optimisation hub.'],
  ['com.miui.powerkeeper', 'Battery & Performance', 'oem', 'caution', 'Battery management daemon.'],
  ['com.xiaomi.xmsf', 'Xiaomi Service Framework', 'oem', 'caution', 'Push services for Xiaomi apps.'],
  ['com.xiaomi.account', 'Xiaomi Account', 'oem', 'caution', 'Mi account sign-in.'],
  ['com.miui.cloudservice', 'Mi Cloud', 'oem', 'caution', 'Cloud sync and backup.'],
  ['com.miui.home', 'System Launcher', 'oem', 'caution', 'Default Xiaomi home screen.'],
  ['com.miui.gallery', 'Gallery', 'oem', 'caution', 'Xiaomi gallery.'],
  ['com.miui.notes', 'Notes', 'oem', 'caution', 'Xiaomi notes.'],
  ['com.miui.calculator', 'Calculator', 'oem', 'caution', 'Xiaomi calculator.'],
  ['com.miui.weather2', 'Weather', 'oem', 'caution', 'Xiaomi weather.'],

  // ── Samsung ───────────────────────────────────────────────────────────────
  ['com.samsung.android.bixby.agent', 'Bixby', 'oem', 'safe', 'Voice assistant.'],
  ['com.samsung.android.bixby.service', 'Bixby Service', 'oem', 'safe', 'Bixby background service.'],
  ['com.samsung.android.bixbyvision.framework', 'Bixby Vision Framework', 'oem', 'safe', 'Camera-based search.'],
  ['com.samsung.android.visionintelligence', 'Bixby Vision', 'oem', 'safe', 'Camera-based search.'],
  ['com.samsung.android.app.spage', 'Samsung Free', 'oem', 'safe', 'Left-of-home content feed.'],
  ['com.samsung.android.game.gamehome', 'Game Launcher', 'oem', 'safe', 'Game hub with recommendations.'],
  ['com.samsung.android.arzone', 'AR Zone', 'oem', 'safe', 'AR features hub.'],
  ['com.samsung.android.ardrawing', 'AR Doodle', 'oem', 'safe', 'AR drawing.'],
  ['com.samsung.android.aremoji', 'AR Emoji', 'oem', 'safe', 'Avatar emoji.'],
  ['com.samsung.android.app.tips', 'Tips', 'oem', 'safe', 'Onboarding tips.'],
  ['com.samsung.android.stickercenter', 'Sticker Center', 'oem', 'safe', 'Sticker downloads.'],
  ['com.sec.android.app.sbrowser', 'Samsung Internet', 'oem', 'caution', 'Samsung browser.'],
  ['com.samsung.android.messaging', 'Samsung Messages', 'oem', 'caution', 'SMS app.'],
  ['com.samsung.android.dialer', 'Samsung Phone', 'oem', 'caution', 'Dialer.'],
  ['com.sec.android.app.launcher', 'One UI Home', 'oem', 'caution', 'Default Samsung home screen.'],

  // ── Common partner stubs ──────────────────────────────────────────────────
  ['com.microsoft.skydrive', 'OneDrive', 'social', 'safe', 'Microsoft cloud storage.'],
  ['com.netflix.mediaclient', 'Netflix', 'social', 'safe', 'Video streaming.'],
  ['com.spotify.music', 'Spotify', 'social', 'safe', 'Music streaming.'],
  ['com.amazon.mShop.android.shopping', 'Amazon Shopping', 'social', 'safe', 'Shopping app.'],
  ['com.linkedin.android', 'LinkedIn', 'social', 'safe', 'Social network.'],
];

// Debloat presets: only packages with risk "safe" belong here.
const PRESETS = [
  { id: 'google-extras', name: 'Google extras', desc: 'Optional Google apps and background helpers.',
    pkgs: ['com.google.android.apps.tachyon', 'com.google.android.apps.youtube.music', 'com.google.android.videos', 'com.google.android.music',
           'com.google.android.apps.subscriptions.red', 'com.google.android.apps.magazines', 'com.google.android.apps.podcasts',
           'com.google.android.apps.books', 'com.google.android.apps.wellbeing', 'com.google.android.projection.gearhead',
           'com.google.ar.core', 'com.google.android.feedback', 'com.google.android.printservice.recommendation',
           'com.google.android.onetimeinitializer', 'com.google.android.partnersetup'] },
  { id: 'facebook', name: 'Facebook stubs', desc: 'Preinstalled Facebook helper packages.',
    pkgs: ['com.facebook.appmanager', 'com.facebook.services', 'com.facebook.system'] },
  { id: 'telemetry', name: 'Telemetry & ads services', desc: 'Analytics and ad-delivery services shipped by OEMs.',
    pkgs: ['com.miui.analytics', 'com.miui.msa.global', 'com.miui.systemAdSolution', 'com.miui.bugreport', 'com.google.android.feedback'] },
  { id: 'xiaomi', name: 'Xiaomi / HyperOS extras', desc: 'Promotional and duplicate Xiaomi apps.',
    pkgs: ['com.xiaomi.mipicks', 'com.miui.player', 'com.miui.videoplayer', 'com.miui.video', 'com.miui.yellowpage',
           'com.miui.hybrid', 'com.miui.hybrid.accessory', 'com.mi.globalbrowser'] },
  { id: 'samsung', name: 'Samsung extras', desc: 'Bixby, Samsung Free, AR features and other optional One UI apps.',
    pkgs: ['com.samsung.android.bixby.agent', 'com.samsung.android.bixby.service', 'com.samsung.android.bixbyvision.framework',
           'com.samsung.android.visionintelligence', 'com.samsung.android.app.spage', 'com.samsung.android.game.gamehome',
           'com.samsung.android.arzone', 'com.samsung.android.ardrawing', 'com.samsung.android.aremoji',
           'com.samsung.android.app.tips', 'com.samsung.android.stickercenter'] },
  { id: 'aosp-extras', name: 'AOSP leftovers', desc: 'Printing, screensavers, easter egg and other rarely used components.',
    pkgs: ['com.android.printspooler', 'com.android.bips', 'com.android.egg', 'com.android.wallpaper.livepicker',
           'com.android.dreams.basic', 'com.android.dreams.phototable', 'com.android.bookmarkprovider',
           'com.android.providers.partnerbookmarks', 'com.android.htmlviewer', 'com.android.traceur'] },
];

const KNOWN = new Map();
ENTRIES.forEach(function (e) { KNOWN.set(e[0], { name: e[1], cat: e[2], risk: e[3], desc: e[4], known: true }); });

// Heuristics for packages that are not in the table.
const OVERLAY_RE = /(\.auto_generated_rro_|\.overlay(\.|$)|_overlay$|\.rro$|^com\.android\.theme\.|^com\.android\.internal\.|\.resources$)/i;
const VENDOR_RE = /^(com\.qualcomm\.|com\.qti\.|vendor\.qti\.|org\.codeaurora\.|com\.mediatek\.|com\.mtk\.|com\.unisoc\.|com\.sprd\.|com\.spreadtrum\.|com\.nxp\.|com\.st\.)/;
const CORE_PREFIX = /^(com\.android\.providers\.|com\.android\.server\.|com\.android\.wifi|com\.google\.android\.trichromelibrary|com\.android\.trichromelibrary|com\.google\.android\.overlay\.|com\.android\.cts\.)/;
const GENERIC_LAST = new Set(['app', 'android', 'mobile', 'client', 'main', 'free', 'pro', 'lite', 'release', 'phone', 'prod', 'full']);

function prettify(pkg) {
  const parts = pkg.split('.').filter(Boolean);
  while (parts.length > 1 && /^(com|org|net|io|app|android|me|dev|co|ir|de|fr|ru|cn|jp)$/.test(parts[0])) parts.shift();
  let seg = parts.slice(-1);
  if (parts.length > 1 && GENERIC_LAST.has(parts[parts.length - 1])) seg = parts.slice(-2);
  const words = seg.join(' ').replace(/[_-]+/g, ' ').replace(/([a-z0-9])([A-Z])/g, '$1 $2').split(/\s+/).filter(Boolean);
  return words.map(function (w) { return w.charAt(0).toUpperCase() + w.slice(1); }).join(' ') || pkg;
}

/** classify(pkg, isSystem) -> {name, cat, risk, desc, known} */
function classify(pkg, isSystem) {
  const k = KNOWN.get(pkg);
  if (k) return k;
  if (CORE_PREFIX.test(pkg)) return { name: prettify(pkg), cat: 'core', risk: 'core', desc: 'System component.', known: false };
  if (OVERLAY_RE.test(pkg)) return { name: prettify(pkg), cat: 'overlay', risk: 'core', desc: 'Resource overlay (theme / config). Not an app.', known: false };
  if (!isSystem) return { name: prettify(pkg), cat: 'user', risk: 'user', desc: 'Installed by you.', known: false };
  if (VENDOR_RE.test(pkg)) return { name: prettify(pkg), cat: 'vendor', risk: 'caution', desc: 'Chipset / vendor component. Research before touching.', known: false };
  if (/^com\.android\./.test(pkg)) return { name: prettify(pkg), cat: 'aosp', risk: 'caution', desc: 'AOSP component.', known: false };
  if (/^com\.google\./.test(pkg)) return { name: prettify(pkg), cat: 'google', risk: 'caution', desc: 'Google component.', known: false };
  if (/^(com\.miui\.|com\.xiaomi\.|com\.mi\.)/.test(pkg)) return { name: prettify(pkg), cat: 'oem', risk: 'caution', desc: 'Xiaomi / HyperOS component.', known: false };
  if (/^(com\.samsung\.|com\.sec\.)/.test(pkg)) return { name: prettify(pkg), cat: 'oem', risk: 'caution', desc: 'Samsung component.', known: false };
  return { name: prettify(pkg), cat: 'oem', risk: 'caution', desc: 'Unknown system package. Research before freezing.', known: false };
}

window.KB = { build: '1.2.0', classify: classify, prettify: prettify, PRESETS: PRESETS, KNOWN: KNOWN, size: ENTRIES.length };
})();
