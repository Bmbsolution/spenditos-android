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

## Building needs GitHub Packages access

The Paykit SDK (`com.paykit:paykit-android`) is private. Every build, debug included, needs a token with `read:packages`: put `gpr.user` and
`gpr.key` in `~/.gradle/gradle.properties`, or export `GITHUB_ACTOR` and `GITHUB_TOKEN`. Never put it in the project. CI uses the workflow token,
and the package has to grant this repository Actions access once it has a remote.

## Owner inputs that switch features on (the code is built, inert until you supply them)

- **Push notifications:** create a Firebase project, add Android apps for the ids you ship (`com.asaklex.spenditos`,
  `com.asaklex.spenditos.staging`; local/harness need none), and put `google-services.json` in `app/`. The Google Services
  plugin is applied automatically when the file exists. The app then registers its FCM token (Settings > Notifications)
  with `POST /user/device-token` (platform android). A backend sender does not exist yet: nothing will arrive until
  one is built (tap targets already map the iOS types to Home add / Progress).
- **Pro purchases:** register the Play subscriptions as `android` plans in the Paykit tenant config (`spenditos/docs/features/paykit.md`), then set the
  stage's Paykit URL as `PAYKIT_BASE_URL` in `config/prod.properties` / `config/staging.properties` (not a secret). The tenant's Google
  `packageName` must match the app id of that build (`com.asaklex.spenditos`, `.staging` on staging). Without the URL the Pro screen says
  subscriptions aren't available and no upsell button is shown. Deploy Paykit and the backend's `/payments/*` first.

## Not done (needs the owner)

- Play Console app, store listing, privacy/data-safety forms, signing enrollment.
- CI: `.github/workflows/android.yml` is a template (verify on every push/PR; a signed prod bundle on `v*` tags from
  secrets). It has never run because this repo has no remote; treat the first run as a test.
