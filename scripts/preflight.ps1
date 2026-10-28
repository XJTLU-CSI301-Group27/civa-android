$ErrorActionPreference = "Stop"

Write-Host "=== Civa Android Preflight ==="

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

$gradlew = Join-Path $repositoryRoot "gradlew.bat"
if (-not (Test-Path -LiteralPath $gradlew)) {
    throw "Android scaffold is missing. Add the unified Gradle project before running preflight."
}

$gradleTasks = @(
    "assembleDebug",
    "testDebugUnitTest",
    "lint"
)

foreach ($task in $gradleTasks) {
    Write-Host "Running Gradle task: $task"
    & $gradlew $task
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle task failed: $task"
    }
}

Write-Host "=== Preflight passed ==="
Write-Host "All configured checks completed successfully."
