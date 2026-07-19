# Phone data preservation

PaisaFlow contains user-entered financial records. A physical-phone workflow must preserve the existing installation's app-owned data.

## Safe update workflow

1. Review source, manifest permissions, runtime dependencies, signing identity, package identifier, version, and APK hash.
2. Run unit tests, lint, and APK assembly locally.
3. Run instrumentation tests only on the disposable Gradle managed emulator:

   ```powershell
   .\gradlew.bat paisaFlowApi35DebugAndroidTest
   ```

4. Ask the user to create a full JSON backup from **Settings → Create full backup** before a migration or any operation with uncertain data impact.
5. Obtain explicit approval for the exact reviewed APK and target phone.
6. Update the physical phone only with:

   ```powershell
   adb install -r path\to\reviewed.apk
   ```

7. If the update fails, stop. Do not uninstall, clear data, downgrade, or bypass signature/version protections.

## Forbidden physical-phone operations

- `connectedAndroidTest` or `connectedDebugAndroidTest`
- `adb uninstall com.paisaflow.app`
- `adb shell pm uninstall ... com.paisaflow.app`
- `adb shell pm clear com.paisaflow.app`
- Clearing storage/cache through automation
- Installing with downgrade or data-reset flags
- Deleting PaisaFlow databases, preferences, backups, or app files

The Gradle build blocks connected Android-test tasks and provides a managed-emulator replacement. Cleanup must never remove the physical phone's PaisaFlow package or user data.
