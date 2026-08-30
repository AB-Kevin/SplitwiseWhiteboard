#!/usr/bin/env bash
# Cuts a new release: bumps the app version, builds the debug APK, commits
# and tags the bump, pushes, and publishes a GitHub release with the APK
# attached as an asset (which is what UpdateChecker/UpdateInstaller in the
# app look for).
#
# Usage: scripts/release.sh <version>   e.g. scripts/release.sh 0.1.0
set -euo pipefail

VERSION="${1:-}"
if [[ -z "$VERSION" ]]; then
    echo "Usage: scripts/release.sh <version>   e.g. scripts/release.sh 0.1.0" >&2
    exit 1
fi
if [[ ! "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "Version must look like X.Y.Z (got: $VERSION)" >&2
    exit 1
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [[ -n "$(git status --porcelain)" ]]; then
    echo "Working tree isn't clean — commit or stash your changes first." >&2
    exit 1
fi

TAG="v$VERSION"
if git rev-parse "$TAG" >/dev/null 2>&1; then
    echo "Tag $TAG already exists." >&2
    exit 1
fi

# versionCode must strictly increase with every release for Android to treat
# it as an update. Deriving it from the version number keeps that automatic:
# 0.1.0 -> 100, 0.2.3 -> 203, 1.0.0 -> 10000, etc. (up to 99 for minor/patch).
IFS='.' read -r MAJOR MINOR PATCH <<< "$VERSION"
VERSION_CODE=$((MAJOR * 10000 + MINOR * 100 + PATCH))

GRADLE_FILE="app/build.gradle.kts"
sed -i -E "s/versionCode = [0-9]+/versionCode = $VERSION_CODE/" "$GRADLE_FILE"
sed -i -E "s/versionName = \"[^\"]+\"/versionName = \"$VERSION\"/" "$GRADLE_FILE"
echo "Bumped versionCode=$VERSION_CODE versionName=$VERSION in $GRADLE_FILE"

# Prefer an already-set JAVA_HOME; fall back to Android Studio's bundled JDK.
if [[ -z "${JAVA_HOME:-}" ]]; then
    STUDIO_JBR="/c/Program Files/Android/Android Studio/jbr"
    if [[ -d "$STUDIO_JBR" ]]; then
        export JAVA_HOME="$STUDIO_JBR"
    else
        echo "JAVA_HOME is not set and Android Studio's bundled JDK wasn't found — set JAVA_HOME and re-run." >&2
        exit 1
    fi
fi

echo "Building debug APK..."
./gradlew.bat assembleDebug

APK_SRC="app/build/outputs/apk/debug/app-debug.apk"
if [[ ! -f "$APK_SRC" ]]; then
    echo "Build didn't produce $APK_SRC" >&2
    exit 1
fi

APK_NAME="SplitwiseWhiteboard-$TAG.apk"
APK_DEST="app/build/outputs/apk/debug/$APK_NAME"
cp "$APK_SRC" "$APK_DEST"

git add "$GRADLE_FILE"
git commit -q -m "Release $TAG"
git tag "$TAG"
git push
git push origin "$TAG"

# Prefer gh on PATH; fall back to the default winget install location.
GH_BIN="gh"
if ! command -v gh >/dev/null 2>&1; then
    if [[ -x "/c/Program Files/GitHub CLI/gh.exe" ]]; then
        GH_BIN="/c/Program Files/GitHub CLI/gh.exe"
    else
        echo "gh CLI not found on PATH — install it (winget install --id GitHub.cli) or publish the release manually." >&2
        echo "APK ready at: $APK_DEST" >&2
        exit 1
    fi
fi

"$GH_BIN" release create "$TAG" "$APK_DEST" \
    --title "$TAG" \
    --generate-notes

echo "Released $TAG — tablets will pick this up next time they check for updates."
