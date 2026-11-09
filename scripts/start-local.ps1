param([switch]$Server, [switch]$CheckOnly, [switch]$Stop,
    [switch]$NoStudio, [switch]$BuildServer, [switch]$BuildApp)

$ErrorActionPreference = 'Stop'

function Read-LocalProperty($File, $Key) {
    if (-not (Test-Path -LiteralPath $File)) { return $null }
    $line = Get-Content -LiteralPath $File -Encoding UTF8 |
        Where-Object { $_ -match ('^\s*' + [regex]::Escape($Key) + '\s*=') } | Select-Object -Last 1
    if ($line) { return [regex]::Unescape(($line -split '=', 2)[1].Trim()) }
}

function Test-Jdk17($Directory) {
    if (-not $Directory -or -not (Test-Path -LiteralPath "$Directory\bin\java.exe") -or
        -not (Test-Path -LiteralPath "$Directory\release")) { return $false }
    return [bool](Select-String -LiteralPath "$Directory\release" -Pattern '^JAVA_VERSION="17[.\-"]' -Quiet)
}

function Get-LocalBackend($StateFile, $Project) {
    if (-not (Test-Path -LiteralPath $StateFile)) { return $null }
    $state = Get-Content -LiteralPath $StateFile -Raw | ConvertFrom-Json
    if ($state.project -ne $Project) { throw 'Backend state belongs to another project; refusing to use it.' }
    $process = Get-Process -Id $state.processId -ErrorAction SilentlyContinue
    if (-not $process) { return $null }
    if ($process.ProcessName -notin @('powershell', 'pwsh') -or
        $process.StartTime.ToUniversalTime().Ticks.ToString() -ne $state.startTicks) {
        throw 'Backend PID was reused; refusing to stop or reuse an unrelated process.'
    }
    return $process
}

function Stop-LocalBackend($StateFile, $Project) {
    $process = Get-LocalBackend $StateFile $Project
    if ($process) {
        & taskkill.exe /PID $process.Id /T /F
        if ($LASTEXITCODE -ne 0) { throw 'Could not stop the backend process tree.' }
    } else { Write-Host 'No backend started by this launcher is running.' }
    if (Test-Path -LiteralPath $StateFile) { Remove-Item -LiteralPath $StateFile }
}

try {
    $project = Split-Path $PSScriptRoot -Parent
    $projectItem = Get-Item -LiteralPath $project
    if ($projectItem.LinkType -eq 'Junction') { $project = [string]$projectItem.Target }
    $project = [IO.Path]::GetFullPath($project)
    $stateFile = Join-Path $project 'build\server-local.json'
    if ($Stop) { Stop-LocalBackend $stateFile $project; exit 0 }
    # ponytail: only create a junction when the Windows checkout path requires it.
    $aliasPath = $project
    if ($project -match '[^\x00-\x7F]') {
        $aliasPath = Join-Path $env:LOCALAPPDATA 'CivaLocal'
        if (-not (Test-Path -LiteralPath $aliasPath)) {
            New-Item -ItemType Junction -Path $aliasPath -Target $project | Out-Null
        }
        $link = Get-Item -LiteralPath $aliasPath
        if ($link.LinkType -ne 'Junction' -or [IO.Path]::GetFullPath([string]$link.Target) -ne $project) {
            throw "The local alias belongs to another checkout: $aliasPath. Use an ASCII checkout path."
        }
    }
    Set-Location -LiteralPath $aliasPath

    New-Item -ItemType Directory -Force -Path build | Out-Null
    $studioCommand = Get-Command studio64.exe -ErrorAction SilentlyContinue
    $studioCandidates = @($env:CIVA_STUDIO_PATH, $studioCommand.Source,
        "$env:ProgramFiles\Android\Android Studio\bin\studio64.exe",
        "$env:LOCALAPPDATA\Programs\Android Studio\bin\studio64.exe")
    $studioInstalls = Get-ItemProperty 'HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\*',
        'HKCU:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\*' -ErrorAction SilentlyContinue |
        Where-Object { $_.DisplayName -like '*Android Studio*' }
    foreach ($install in $studioInstalls) {
        if ($install.InstallLocation) { $studioCandidates += Join-Path $install.InstallLocation 'bin\studio64.exe' }
        if ($install.DisplayIcon) {
            $iconPath = ($install.DisplayIcon -replace ',\d+$', '').Trim('"')
            $studioCandidates += Join-Path (Split-Path $iconPath -Parent) 'studio64.exe'
        }
    }
    $studio = $studioCandidates | Where-Object { $_ -and (Test-Path -LiteralPath $_ -PathType Leaf) } | Select-Object -First 1
    $javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
    $jdkCandidates = @($env:JAVA_HOME, (Read-LocalProperty '.gradle\config.properties' 'java.home'))
    if ($javaCommand) { $jdkCandidates += Split-Path (Split-Path $javaCommand.Source -Parent) -Parent }
    if ($studio) { $jdkCandidates += Join-Path (Split-Path (Split-Path $studio -Parent) -Parent) 'jbr' }
    $jdkCandidates += @(Get-ChildItem -LiteralPath 'build\jdk17' -Directory -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName)
    foreach ($vendor in @('Microsoft', 'Eclipse Adoptium', 'Java')) {
        $jdkCandidates += @(Get-ChildItem -Path "$env:ProgramFiles\$vendor\*17*" -Directory -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName)
    }
    $env:JAVA_HOME = $jdkCandidates | Where-Object { Test-Jdk17 $_ } | Select-Object -First 1
    if (-not $env:JAVA_HOME) { throw 'JDK 17 not found. Install JDK 17 and set JAVA_HOME to its directory (not bin). Java 25 cannot run this Gradle version.' }
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
    if (-not $env:GRADLE_USER_HOME) {
        $env:GRADLE_USER_HOME = if (Test-Path -LiteralPath 'build\gradle-user-home') { "$aliasPath\build\gradle-user-home" } else { Join-Path $env:USERPROFILE '.gradle' }
    }
    Write-Host "JDK 17: $env:JAVA_HOME"

    if ($Server) {
        Set-Location server
        if (-not (Test-Path -LiteralPath 'build\libs\civa-server.jar')) { throw 'Backend JAR missing. Run the launcher with -BuildServer.' }
        & "$env:JAVA_HOME\bin\java.exe" -jar 'build\libs\civa-server.jar'
        exit $LASTEXITCODE
    }

    if (-not $NoStudio -or $BuildApp) {
        $sdkCandidates = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT,
            (Read-LocalProperty 'local.properties' 'sdk.dir'), "$env:LOCALAPPDATA\Android\Sdk", "$aliasPath\build\android-sdk")
        $env:ANDROID_HOME = $sdkCandidates | Where-Object { $_ -and
            (Test-Path -LiteralPath "$_\platforms\android-36\android.jar") -and
            (Test-Path -LiteralPath "$_\build-tools\35.0.0\aapt2.exe") } | Select-Object -First 1
        if (-not $env:ANDROID_HOME) { throw 'Android SDK not found. Install Platform 36 and Build Tools 35.0.0 in SDK Manager; set ANDROID_HOME to the SDK directory.' }
        if ($env:ANDROID_HOME -match '[^\x00-\x7F]' -or
            ($project -match '[^\x00-\x7F]' -and $env:ANDROID_HOME.StartsWith($aliasPath, [StringComparison]::OrdinalIgnoreCase))) {
            throw 'Use a physical ASCII SDK directory, for example C:\Android\Sdk. An SDK inside a Unicode checkout junction can crash the emulator.'
        }
        $env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
        Write-Host "Android SDK: $env:ANDROID_HOME"
    }
    if (-not $NoStudio -and -not $studio) { throw 'Android Studio not found. Set CIVA_STUDIO_PATH to studio64.exe, or use -NoStudio for backend-only testing.' }
    if ($studio -and -not $NoStudio) { Write-Host "Android Studio: $studio" }
    Get-Command docker -ErrorAction Stop | Out-Null
    & docker compose version --short
    if ($LASTEXITCODE -ne 0) { throw 'Docker Compose is unavailable. Install Docker Desktop with Compose.' }

    # Keep the IDE on the same JDK and SDK as this launcher.
    New-Item -ItemType Directory -Force -Path '.gradle' | Out-Null
    $properties = @(@{ Path = '.gradle\config.properties'; Key = 'java.home'; Value = $env:JAVA_HOME })
    if ($env:ANDROID_HOME -and (-not $NoStudio -or $BuildApp)) {
        $properties += @{ Path = 'local.properties'; Key = 'sdk.dir'; Value = $env:ANDROID_HOME }
    }
    foreach ($configurationFile in $properties) {
        $lines = if (Test-Path -LiteralPath $configurationFile.Path) { Get-Content -LiteralPath $configurationFile.Path -Encoding UTF8 } else { @() }
        $lines = @($lines | Where-Object { $_ -notmatch ('^\s*' + [regex]::Escape($configurationFile.Key) + '\s*=') })
        $value = $configurationFile.Value.Replace('\', '/').Replace(':', '\:')
        $value = [regex]::Replace($value, '[^\x00-\x7F]', { param($match) '\u{0:x4}' -f [int][char]$match.Value })
        $lines += $configurationFile.Key + '=' + $value
        [IO.File]::WriteAllLines((Join-Path $aliasPath $configurationFile.Path), [string[]]$lines, (New-Object Text.UTF8Encoding($false)))
    }

    Write-Host '[1/4] Checking Docker engine...'
    # Bound Docker's own connection timeout as well as the retry loop.
    $dockerReady = $false
    $timer = [Diagnostics.Stopwatch]::StartNew()
    $dockerTimeout = if ($CheckOnly) { 5 } else { 60 }
    while ($timer.Elapsed.TotalSeconds -lt $dockerTimeout) {
        $probe = Start-Process -FilePath (Get-Command docker).Source -ArgumentList 'info' `
            -WindowStyle Hidden -PassThru -RedirectStandardOutput 'build\docker-info.log' `
            -RedirectStandardError 'build\docker-info-error.log'
        $null = $probe.Handle # Cache the process handle so Windows PowerShell retains ExitCode.
        if (-not $probe.WaitForExit(5000)) { $probe.Kill(); $probe.WaitForExit() }
        elseif ($probe.ExitCode -eq 0) { $dockerReady = $true }
        $probe.Dispose()
        if ($dockerReady) { break }
        if (-not $CheckOnly) { Start-Sleep -Seconds 2 }
    }
    if (-not $dockerReady) { throw 'Docker is not ready. Start Docker Desktop and try again.' }
    if ($CheckOnly) { Write-Host 'PASS: required tools and Docker engine are ready; local IDE paths configured.'; exit 0 }

    if (-not (Test-Path -LiteralPath '.env')) {
        $password = [Guid]::NewGuid().ToString('N')
        $template = Get-Content -LiteralPath '.env.example' -Raw
        $template.Replace('POSTGRES_PASSWORD=', "POSTGRES_PASSWORD=$password") |
            Set-Content -LiteralPath '.env' -Encoding ASCII
        Write-Host 'Created .env with a random local database password.'
    }
    $compose = @('compose', '--env-file', '.env', '-f', 'infra/compose.yaml')
    $configuration = & docker @compose config --format json
    if ($LASTEXITCODE -ne 0) { throw 'Invalid Compose/.env configuration.' }
    $database = ($configuration | Out-String | ConvertFrom-Json).services.db.environment
    $env:DB_URL = "jdbc:postgresql://localhost:5432/$($database.POSTGRES_DB)"
    $env:DB_USERNAME = $database.POSTGRES_USER
    $env:DB_PASSWORD = $database.POSTGRES_PASSWORD
    $env:CIVA_MEDIA_DIR = "$aliasPath\build\media"
    $env:SERVER_PORT = '8080'
    New-Item -ItemType Directory -Force -Path $env:CIVA_MEDIA_DIR | Out-Null

    Write-Host '[2/4] Starting PostgreSQL...'
    & docker @compose up -d --wait --wait-timeout 90
    if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL failed to start. Check Docker and port 5432.' }

    $serverProcess = Get-LocalBackend $stateFile $project
    if ($serverProcess -and $BuildServer) { Stop-LocalBackend $stateFile $project; $serverProcess = $null }
    if (-not $serverProcess) {
        $running = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
        if ($running) { throw "Port 8080 belongs to another process (PID $($running.OwningProcess)). Stop that backend first; no unrelated process was killed." }
        if ($BuildServer -or -not (Test-Path -LiteralPath 'server\build\libs\civa-server.jar')) {
            Write-Host 'Building backend from source (first run may download dependencies)...'
            & .\server\gradlew.bat -p server bootJar --console=plain --no-daemon
            if ($LASTEXITCODE -ne 0) { throw 'Backend build failed. Fix the Gradle error above; startup was cancelled.' }
        }
        Write-Host '[3/4] Starting backend...'
        $serverScript = Join-Path $aliasPath 'scripts\start-local.ps1'
        $serverProcess = Start-Process powershell.exe -WindowStyle Hidden -PassThru `
            -ArgumentList "-NoProfile -ExecutionPolicy Bypass -File `"$serverScript`" -Server" `
            -RedirectStandardOutput 'build\server-local.log' -RedirectStandardError 'build\server-local-error.log'
        $null = $serverProcess.Handle
        @{ processId = $serverProcess.Id; startTicks = $serverProcess.StartTime.ToUniversalTime().Ticks.ToString(); project = $project } |
            ConvertTo-Json | Set-Content -LiteralPath $stateFile -Encoding UTF8
    } else { Write-Host '[3/4] Reusing the backend already started by this launcher.' }
    $timer.Restart()
    while ($true) {
        $serverProcess.Refresh()
        if ($serverProcess.HasExited) { throw 'Backend stopped. See code\civa\build\server-local*.log.' }
        if ((Select-String -LiteralPath 'build\server-local.log' -Pattern 'Started CivaServer' -Quiet) -and
            (Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue)) { break }
        if ($timer.Elapsed.TotalMinutes -gt 2) {
            throw 'Backend is still starting. Check code\civa\build\server-local.log before retrying.'
        }
        Start-Sleep -Seconds 2
    }

    if ($BuildApp) {
        Write-Host 'Building Android debug APK...'
        & .\gradlew.bat :app:assembleDebug --console=plain --no-daemon
        if ($LASTEXITCODE -ne 0) { throw 'Android build failed. Backend remains running; use -Stop when finished.' }
        Write-Host 'APK ready: app\build\outputs\apk\debug\app-debug.apk'
    }
    if (-not $NoStudio) {
        Write-Host '[4/4] Opening Android Studio...'
        Start-Process -FilePath $studio -ArgumentList "`"$aliasPath`"" -WindowStyle Normal | Out-Null
        Write-Host 'In Android Studio: wait for your emulator to boot, select app, then Run.'
    }
    Write-Host 'Backend ready: http://localhost:8080/'
    Write-Host 'Backend logs: code\civa\build\server-local.log'
    Write-Host 'Stop later: .\start-civa.bat -Stop (database and its data are preserved).'
    Write-Host 'After backend source changes: .\start-civa.bat -BuildServer'
} catch {
    Write-Host "ERROR: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
