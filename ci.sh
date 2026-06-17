#!/bin/bash
set -e

echo "=== CI: Building Web frontend ==="
cd web && npm run build && cd ..

echo "=== CI: Type checking Vue ==="
cd web && npx vue-tsc --noEmit && cd ..

echo "=== CI: Building Android APK ==="
./gradlew :app:assembleDebug

echo "=== CI: All checks passed ==="
