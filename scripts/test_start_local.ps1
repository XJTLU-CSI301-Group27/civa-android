$ErrorActionPreference = 'Stop'
$source = Get-Content -LiteralPath "$PSScriptRoot\start-local.ps1" -Raw
$tokens = $null
$parseErrors = $null
$syntax = [Management.Automation.Language.Parser]::ParseFile("$PSScriptRoot\start-local.ps1", [ref]$tokens, [ref]$parseErrors)
if ($parseErrors.Count) { throw ($parseErrors | Out-String) }
foreach ($definition in $syntax.FindAll({ param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] }, $false)) {
    . ([scriptblock]::Create($definition.Extent.Text))
}
$start = $source.IndexOf('    # Keep the IDE')
$end = $source.IndexOf("    Write-Host '[1/4]", $start)
if ($start -lt 0 -or $end -le $start) { throw 'Launcher configuration block not found.' }
$configure = [scriptblock]::Create($source.Substring($start, $end - $start))
$temporary = Join-Path ([IO.Path]::GetTempPath()) ('civa-config-check-' + [Guid]::NewGuid().ToString('N'))
$originalJava = $env:JAVA_HOME
$originalSdk = $env:ANDROID_HOME
New-Item -ItemType Directory -Path $temporary | Out-Null
Push-Location -LiteralPath $temporary
try {
    $aliasPath = $temporary
    $NoStudio = $false
    $BuildApp = $false
    $env:JAVA_HOME = 'C:\Example\jdk17'
    $env:ANDROID_HOME = 'C:\Example\sdk'
    Set-Content -LiteralPath local.properties -Value 'other.setting=preserve' -Encoding ASCII
    & $configure
    & $configure
    $jdk = @(Get-Content -LiteralPath .gradle\config.properties)
    $sdk = @(Get-Content -LiteralPath local.properties)
    if ($jdk.Count -ne 1 -or $jdk[0] -cne 'java.home=C\:/Example/jdk17') { throw 'JDK setting is not escaped or idempotent.' }
    if ($sdk.Count -ne 2 -or $sdk[0] -cne 'other.setting=preserve' -or $sdk[1] -cne 'sdk.dir=C\:/Example/sdk') { throw 'SDK setting damaged another property or is not escaped/idempotent.' }
    if ([IO.File]::ReadAllBytes((Join-Path $temporary '.gradle\config.properties'))[0] -ne 106) { throw 'Unexpected BOM before java.home.' }
    if ((Read-LocalProperty 'local.properties' 'sdk.dir') -cne 'C:/Example/sdk') { throw 'Java properties path decoding failed.' }
    Set-Content -LiteralPath local.properties -Value 'sdk.dir=C\:\\SDK Space\\sdk' -Encoding ASCII
    if ((Read-LocalProperty 'local.properties' 'sdk.dir') -cne 'C:\SDK Space\sdk') { throw 'Windows path decoding failed.' }
    $env:JAVA_HOME = 'C:\Example\jdk' + [char]0x4e2d
    & $configure
    if ((Read-LocalProperty '.gradle\config.properties' 'java.home') -cne $env:JAVA_HOME.Replace('\', '/')) { throw 'Unicode property round trip failed.' }
    $NoStudio = $true
    Set-Content -LiteralPath local.properties -Value 'sdk.dir=C\:/PreserveThisSdk' -Encoding ASCII
    & $configure
    if ((Read-LocalProperty 'local.properties' 'sdk.dir') -cne 'C:/PreserveThisSdk') { throw 'Backend-only setup changed the Android SDK.' }
    foreach ($version in @('17', '25')) {
        New-Item -ItemType Directory -Path "jdk$version\bin" -Force | Out-Null
        Set-Content -LiteralPath "jdk$version\bin\java.exe" -Value '' -Encoding ASCII
        Set-Content -LiteralPath "jdk$version\release" -Value ('JAVA_VERSION="' + $version + '.0.1"') -Encoding ASCII
    }
    if (-not (Test-Jdk17 'jdk17') -or (Test-Jdk17 'jdk25') -or (Test-Jdk17 'missing')) { throw 'JDK selection accepted an incompatible/missing JDK.' }
    $self = Get-Process -Id $PID
    $stateFile = Join-Path $temporary 'server-state.json'
    @{ processId = $PID; startTicks = $self.StartTime.ToUniversalTime().Ticks.ToString(); project = $temporary } |
        ConvertTo-Json | Set-Content -LiteralPath $stateFile -Encoding UTF8
    if ((Get-LocalBackend $stateFile $temporary).Id -ne $PID) { throw 'Valid backend identity was not recognized.' }
    $rejected = $false
    try { Get-LocalBackend $stateFile 'another-project' | Out-Null } catch { $rejected = $true }
    if (-not $rejected) { throw 'Cross-project backend state was accepted.' }
    @{ processId = $PID; startTicks = '0'; project = $temporary } |
        ConvertTo-Json | Set-Content -LiteralPath $stateFile -Encoding UTF8
    $rejected = $false
    try { Get-LocalBackend $stateFile $temporary | Out-Null } catch { $rejected = $true }
    if (-not $rejected) { throw 'Reused PID was accepted.' }
    Write-Host 'PASS: properties, Unicode, backend-only setup, JDK 17 selection and backend ownership/PID-reuse checks.'
} finally {
    $env:JAVA_HOME = $originalJava
    $env:ANDROID_HOME = $originalSdk
    Pop-Location
    foreach ($relative in @('.gradle\config.properties', 'local.properties', 'server-state.json',
        'jdk17\bin\java.exe', 'jdk17\release', 'jdk17\bin', 'jdk17',
        'jdk25\bin\java.exe', 'jdk25\release', 'jdk25\bin', 'jdk25', '.gradle', '')) {
        $target = if ($relative) { Join-Path $temporary $relative } else { $temporary }
        if (Test-Path -LiteralPath $target) { Remove-Item -LiteralPath $target }
    }
}
