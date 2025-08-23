# Publishing Guide

## Prerequisites

1. **Gradle Plugin Portal**: Register at https://plugins.gradle.org, get API keys
2. **Local Setup**: Add keys to `~/.gradle/gradle.properties`:
   ```properties
   gradle.publish.key=YOUR_KEY
   gradle.publish.secret=YOUR_SECRET
   ```
3. **GitHub Secrets**: Add `GRADLE_PUBLISH_KEY` and `GRADLE_PUBLISH_SECRET` to repository

## Local Development

```bash
# Build native libraries
cd font-subsetting/font-subsetting-plugin
./build-in-docker.sh

# Test locally
./gradlew :font-subsetting:publishToMavenLocal
./gradlew :app:assembleDebug
```

## Manual Publishing

```bash
# Set version
export PLUGIN_VERSION=1.0.0

# Test and validate
./gradlew test
./gradlew :font-subsetting:font-subsetting-plugin:validatePlugin

# Publish
./gradlew :font-subsetting:font-subsetting-plugin:publishPlugins \
  -Pgradle.publish.key=$GRADLE_PUBLISH_KEY \
  -Pgradle.publish.secret=$GRADLE_PUBLISH_SECRET
```

## Automated Publishing

```bash
# Create tag
git tag v1.0.0
git push origin v1.0.0
```

GitHub Actions will automatically build, test, and publish.

## Verification

1. Check https://plugins.gradle.org/plugin/com.davidmedenjak.fontsubsetting
2. Test installation:
   ```kotlin
   plugins {
       id("com.davidmedenjak.fontsubsetting") version "1.0.0"
   }
   ```

## Troubleshooting

- **Shadow JAR**: Check `build/libs/` if task appears stuck
- **Native Libraries**: Verify with `jar tf build/libs/*.jar | grep -E "\.(dll|so|dylib)$"`
- **Publishing**: Versions are immutable once published
- **Credentials**: Never commit to repo, use environment variables