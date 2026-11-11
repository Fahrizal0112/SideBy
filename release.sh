#!/usr/bin/env bash
# Rilis versi baru Sideby: build APK, timpa Sideby-Phone.apk, push source, upload ke GitHub Releases.
# Pemakaian:  ./release.sh "catatan perubahan singkat"
# Sebelumnya naikkan versionCode & versionName di mobile/build.gradle.kts.
set -euo pipefail
cd "$(dirname "$0")"

NOTES="${1:-Pembaruan Sideby}"
VERSION=$(grep -E 'versionName *=' mobile/build.gradle.kts | head -1 | sed -E 's/.*"([^"]+)".*/\1/')
TAG="v$VERSION"

if gh release view "$TAG" >/dev/null 2>&1; then
  echo "Rilis $TAG sudah ada. Naikkan versionName/versionCode dulu." >&2
  exit 1
fi

./gradlew :mobile:assembleDebug -q
cp mobile/build/outputs/apk/debug/mobile-debug.apk Sideby-Phone.apk

git add -A
git commit -m "Rilis $TAG: $NOTES" || true
git push origin main

gh release create "$TAG" Sideby-Phone.apk --title "Sideby $VERSION" --notes "$NOTES"
echo "Selesai: $TAG terbit. Aplikasi di HP akan menawarkan update saat dibuka."
