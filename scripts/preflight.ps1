$ErrorActionPreference = "Stop"

Write-Host "=== Civa Preflight ==="

$repositoryRoot = git rev-parse --show-toplevel
if ($LASTEXITCODE -ne 0) {
    throw "Not inside a Git repository."
}

$branch = git branch --show-current
if ($LASTEXITCODE -ne 0) {
    throw "Unable to determine current branch."
}
if ($branch -eq "main") {
    throw "Run preflight on a feature branch, not directly on main."
}

Write-Host "Repository: $repositoryRoot"
Write-Host "Current branch: $branch"

git diff --check
if ($LASTEXITCODE -ne 0) {
    throw "git diff --check failed."
}

# Contract tooling requires Python 3.11+ and PyYAML (PROJECT_DOC.md section 3); do not silently swap to py.
& python -c "import sys; raise SystemExit(0 if sys.version_info >= (3, 11) else 1)"
if ($LASTEXITCODE -ne 0) {
    throw "Python 3.11+ is required."
}
& python -c "import yaml"
if ($LASTEXITCODE -ne 0) {
    throw "PyYAML is required; run: python -m pip install -r scripts/requirements.txt"
}

$gradlew = Join-Path $repositoryRoot "gradlew.bat"
if (-not (Test-Path -LiteralPath $gradlew)) {
    throw "Android scaffold is missing. Add the unified Gradle project before running preflight."
}

$rootTasks = @(
    "validateContract",
    "generateClient",
    "generateServerApi",
    "checkContracts",
    "assembleDebug",
    "testDebugUnitTest",
    "lint"
)

foreach ($task in $rootTasks) {
    Write-Host "Running Gradle task: $task"
    & $gradlew $task --no-daemon
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle task failed: $task"
    }
}

Write-Host "Running contract tool self-tests"
& python -m unittest discover -s (Join-Path $repositoryRoot "scripts") -p "test_*.py"
if ($LASTEXITCODE -ne 0) {
    throw "Contract tool self-tests failed."
}

Write-Host "Building the server jar (run server tests separately against a _test database)"
Push-Location -LiteralPath (Join-Path $repositoryRoot "server")
try {
    & ".\gradlew.bat" bootJar --no-daemon
    if ($LASTEXITCODE -ne 0) {
        throw "Server bootJar failed."
    }
} finally {
    Pop-Location
}

Write-Host "=== Preflight passed ==="
Write-Host "All configured checks completed successfully."
