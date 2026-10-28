#!/usr/bin/env bash
set -euo pipefail

echo "=== Civa Android Preflight ==="

repository_root="$(git rev-parse --show-toplevel)"
branch="$(git branch --show-current)"

if [[ "$branch" == "main" ]]; then
  echo "Run preflight on a feature branch, not directly on main." >&2
  exit 1
fi

echo "Repository: $repository_root"
echo "Current branch: $branch"

git diff --check

if [[ ! -x "$repository_root/gradlew" ]]; then
  echo "Android scaffold is missing. Add the unified Gradle project before running preflight." >&2
  exit 1
fi

gradle_tasks=(
  "assembleDebug"
  "testDebugUnitTest"
  "lint"
)

for task in "${gradle_tasks[@]}"; do
  echo "Running Gradle task: $task"
  "$repository_root/gradlew" "$task"
done

echo "=== Preflight passed ==="
echo "All configured checks completed successfully."
