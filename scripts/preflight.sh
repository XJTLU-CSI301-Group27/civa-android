#!/usr/bin/env bash
set -euo pipefail

echo "=== Civa Preflight ==="

repository_root="$(git rev-parse --show-toplevel)"
branch="$(git branch --show-current)"

if [[ "$branch" == "main" ]]; then
  echo "Run preflight on a feature branch, not directly on main." >&2
  exit 1
fi

echo "Repository: $repository_root"
echo "Current branch: $branch"

git diff --check

# Contract tooling requires Python 3.11+ and PyYAML (PROJECT_DOC.md section 3); do not silently swap to py.
python -c 'import sys; raise SystemExit(0 if sys.version_info >= (3, 11) else 1)'
python -c 'import yaml' || { echo "PyYAML is required; run: python -m pip install -r scripts/requirements.txt" >&2; exit 1; }

if [[ ! -x "$repository_root/gradlew" ]]; then
  echo "Android scaffold is missing. Add the unified Gradle project before running preflight." >&2
  exit 1
fi

root_tasks=(
  "validateContract"
  "generateClient"
  "generateServerApi"
  "checkContracts"
  "assembleDebug"
  "testDebugUnitTest"
  "lint"
)

for task in "${root_tasks[@]}"; do
  echo "Running Gradle task: $task"
  "$repository_root/gradlew" "$task" --no-daemon
done

echo "Running contract tool self-tests"
python -m unittest discover -s "$repository_root/scripts" -p 'test_*.py'

echo "Building the server jar (run server tests separately against a _test database)"
(cd "$repository_root/server" && ./gradlew bootJar --no-daemon)

echo "=== Preflight passed ==="
echo "All configured checks completed successfully."
