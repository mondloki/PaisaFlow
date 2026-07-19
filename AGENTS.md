# PaisaFlow workspace rules

## Physical-phone data safety

Before any phone connection, deployment, debugging, or installation, read and follow:

`C:\Users\mondr\.codex\skills\secure-phone-app-access\SKILL.md`

The existing PaisaFlow installation and its app-owned user data must be preserved.

- Never run `connectedAndroidTest`, `connectedDebugAndroidTest`, or an Android Studio instrumentation-test cleanup workflow on a user's physical phone.
- Never run `adb uninstall`, `pm uninstall`, `pm clear`, clear app storage/cache, downgrade, factory reset, or delete PaisaFlow's database/files on a user's phone.
- Never uninstall the production package `com.paisaflow.app` as a troubleshooting or cleanup step.
- For approved updates, use only a signature-compatible `adb install -r <reviewed-apk>` so existing data is retained. If it fails, stop and ask; do not uninstall as a workaround.
- Run device instrumentation tests only with the Gradle managed emulator task `paisaFlowApi35DebugAndroidTest` or another explicitly isolated disposable emulator.
- Before any migration or action with uncertain data consequences, ask the user to create a full backup through PaisaFlow Settings and obtain explicit approval.
- Restrict all device actions to `com.paisaflow.app`. Do not inspect unrelated apps, files, logs, accounts, sensors, or device data.

## Cleanup workflow

1. Do not remove, clear, or alter PaisaFlow's app-owned user data.
2. On a physical phone, cleanup is limited to stopping/relaunching PaisaFlow when approved; do not delete packages or files.
3. Test packages and test data may be removed only from a disposable emulator or isolated test device.
4. Confirm the reviewed PaisaFlow APK remains installed and launchable without using a reinstall/uninstall cycle as verification.
5. Report every phone-side action, whether app data was accessed, and any device setting the user may want to revert.

