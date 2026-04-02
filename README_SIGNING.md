README_SIGNING.md

Purpose
-------
This file documents the local signing flow used for generating an APK locally for testing without committing secrets into source control.

Important: Do NOT commit any keystore binary or files containing passwords to the repository. Keep `keystore.properties` listed in `.gitignore`.

Example `keystore.properties` (place this file at the project root, do NOT commit):
```
storeFile=test-keystore.jks
storePassword=testpass
keyAlias=testkey
keyPassword=testpass
```

Recommended local steps
-----------------------
1) Generate a test keystore locally (only on your machine) using `keytool` (part of the JDK):

   Example (PowerShell):

   keytool -genkeypair -v -keystore test-keystore.jks -storepass testpass -alias testkey -keyalg RSA -keysize 2048 -validity 3650 -dname "CN=Test, OU=Dev, O=Example, L=City, S=State, C=BR"

   Note: If `keytool` is not in PATH, run it from your JDK's bin directory.

2) Create `keystore.properties` in the project root with the four properties shown above. Use absolute paths for `storeFile` if you prefer the keystore somewhere else.

3) Ensure `keystore.properties` is added to `.gitignore` so it is never committed. Example `.gitignore` entry:

   keystore.properties

4) Run a signed assemble (Gradle wrapper):

```
.\gradlew.bat :app:assembleRelease
```

Quick helper scripts (recommended):

1) Generate a test keystore and a matching `keystore.properties` automatically (PowerShell):

```powershell
.\scripts\generate-test-keystore.ps1
```

2) Perform a safe clean and assemble debug APK with refreshed dependencies:

```powershell
.\scripts\clean-build.ps1
```

Notes about the project `build.gradle.kts` behavior
---------------------------------------------------
- The `app/build.gradle.kts` file attempts to load `keystore.properties` from the project root. If the `storeFile` path resolves to a missing file, the build will continue and produce an unsigned release APK, and a warning will be printed during configuration.
- This design avoids failing the build on machines without a keystore, while enabling local signed builds when the keystore and properties are available.

Security reminder: never commit `keystore.properties` with real credentials. Use `keystore.properties.sample` as example and add `keystore.properties` to `.gitignore`.

Security
--------
- Never check real signing keys or passwords into source control.
- Use CI secrets or a secure key management system for production release signing.

