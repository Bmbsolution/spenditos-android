# Release (Play Store) - what exists and what the owner must do

Nothing here publishes anything. The build produces an **unsigned** prod bundle unless you provide a keystore.

## Build the prod release

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
./gradlew :app:bundleProdRelease           # app/build/outputs/bundle/prodRelease/app-prod-release.aab
./gradlew :app:assembleProdRelease         # unsigned APK for local inspection
```

`prodDebug` does not exist and the `harness` / `local` flavors are debug-only, so a release can only be a prod
(or staging) build. Prod host config: `config/prod.properties` (host only, validated at build time).

## Signing (owner-provided secrets, never committed)

Create an upload key once (keep it and its passwords out of git):

```sh
keytool -genkeypair -v -keystore ~/spenditos-upload.jks -alias spenditos-upload -keyalg RSA -keysize 4096 -validity 10000
```

Then sign at build time through the environment only:

```sh
export SPENDITOS_KEYSTORE_PATH=~/spenditos-upload.jks
export SPENDITOS_KEYSTORE_PASSWORD=...   # your values
export SPENDITOS_KEY_ALIAS=spenditos-upload
export SPENDITOS_KEY_PASSWORD=...
./gradlew :app:bundleProdRelease
```

Use Play App Signing (upload the AAB signed with the upload key; Google holds the app signing key).

## Before each upload

1. Bump `versionCode` (and `versionName`) in `app/build.gradle.kts`.
2. Run `scripts/verify.sh`, then build the bundle as above.
3. Keep `app/build/outputs/mapping/prodRelease/mapping.txt` for crash de-obfuscation (upload it to Play Console).
4. Check the prod config is current: `config/prod.properties` must match the iOS Production config (`CLIENT_CONFIG_VERSION`);
   backend deploys dispatch iOS stage-config sync, and **no Android equivalent exists yet** (needs a new PAT in the backend
   `deploy.yml`; a task for whoever owns that workflow).
5. Deploy the backend support first for anything new the app requires; Android never relies on undeployed server behavior.

## Not done (needs the owner)

- Play Console app, store listing, privacy/data-safety forms, signing enrollment.
- Push notifications: needs a Firebase project (`google-services.json`) and a backend sender; `POST /user/device-token`
  already accepts `platform: "android"`.
- Pro purchases: Play Billing plus a backend webhook change (DECISIONS D12 keeps purchases out of v1).
- CI: `.github/workflows/android.yml` is a template (verify on every push/PR; a signed prod bundle on `v*` tags from
  secrets). It has never run because this repo has no remote; treat the first run as a test.
