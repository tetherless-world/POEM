<#
.SYNOPSIS
    One-command setup of the POEM MCP server for LM Studio on this device.

.DESCRIPTION
    Run this on any Windows machine that has LM Studio installed and the POEM
    repo reachable (e.g. the O: network share mapped). It:

      1. Finds a Python >= 3.10 interpreter on this machine.
      2. Creates a device-local venv at %LOCALAPPDATA%\POEM\mcp-venv
         (venvs hard-code the path of their base interpreter, so a venv on the
         shared drive only works on the machine that created it -- each device
         needs its own).
      3. Installs the server dependencies (requirements-mcp.txt) plus the
         shared poem_core package (pip install -e embeddings).
      4. Adds/updates the "poem-search" entry in this user's LM Studio config
         (%USERPROFILE%\.lmstudio\mcp.json), preserving any other servers.
         Paths are written with forward slashes so JSON escaping can't mangle
         them.
      5. Runs an offline smoke test (get_statements, no network needed).

    Afterwards: restart LM Studio, open the Program tab (chip icon), and
    enable "poem-search". First load is slow (~860 .npy files + RDF graph).
    The `search` tool additionally needs the RPI network/VPN (it embeds
    queries via idea-llm-01.idea.rpi.edu:11435); `get_statements` is offline.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File O:\POEM\embeddings\MCP\setup_lmstudio.ps1
#>

$ErrorActionPreference = "Stop"

# Paths are derived from this script's location, so the share can be mapped
# to any drive letter.
$McpDir   = $PSScriptRoot
$EmbRoot  = Split-Path $McpDir -Parent
$ServerPy = Join-Path $McpDir "mcp_server.py"
$Reqs     = Join-Path $McpDir "requirements-mcp.txt"
$VenvDir  = Join-Path $env:LOCALAPPDATA "POEM\mcp-venv"
$VenvPy   = Join-Path $VenvDir "Scripts\python.exe"
$CfgDir   = Join-Path $env:USERPROFILE ".lmstudio"
$CfgPath  = Join-Path $CfgDir "mcp.json"

foreach ($p in @($ServerPy, $Reqs)) {
    if (-not (Test-Path $p)) {
        throw "Cannot find $p -- is the POEM share mapped and this script run from embeddings\MCP\?"
    }
}

Write-Host "== POEM MCP server setup for LM Studio ==" -ForegroundColor Cyan
Write-Host "Repo:   $EmbRoot"
Write-Host "Venv:   $VenvDir"
Write-Host "Config: $CfgPath"
Write-Host ""

# --------------------------------------------------------------------------
# 1. Find a Python >= 3.10
# --------------------------------------------------------------------------
function Get-PythonVersionOk {
    param([string]$Exe, [string[]]$ExtraArgs)
    # Probe quietly; a missing launcher/interpreter must not abort the script.
    $ErrorActionPreference = "Continue"
    try {
        $ver = & $Exe @ExtraArgs -c "import sys; print('%d.%d' % sys.version_info[:2]); sys.exit(0 if sys.version_info >= (3, 10) else 1)" 2>$null
        if ($LASTEXITCODE -eq 0 -and $ver) { return ($ver | Select-Object -First 1) }
    } catch { }
    return $null
}

$candidates = @(
    @{ Exe = "py";      Args = @("-3.12") },
    @{ Exe = "py";      Args = @("-3.11") },
    @{ Exe = "py";      Args = @("-3.10") },
    @{ Exe = "py";      Args = @("-3") },
    @{ Exe = "python";  Args = @() },
    @{ Exe = "python3"; Args = @() }
)

$basePy = $null
foreach ($c in $candidates) {
    if (-not (Get-Command $c.Exe -ErrorAction SilentlyContinue)) { continue }
    $ver = Get-PythonVersionOk -Exe $c.Exe -ExtraArgs $c.Args
    if ($ver) { $basePy = $c; $basePyVer = $ver; break }
}

if (-not $basePy) {
    throw "No Python >= 3.10 found on this machine. Install one from https://www.python.org/downloads/ (check 'Add to PATH'), then re-run this script."
}
Write-Host ("[1/5] Using Python {0} ({1} {2})" -f $basePyVer, $basePy.Exe, ($basePy.Args -join " "))

# --------------------------------------------------------------------------
# 2. Create the device-local venv (reuse it if it already works)
# --------------------------------------------------------------------------
$venvOk = $false
if (Test-Path $VenvPy) {
    $ErrorActionPreference = "Continue"
    & $VenvPy -c "import sys; sys.exit(0 if sys.version_info >= (3, 10) else 1)" 2>$null
    if ($LASTEXITCODE -eq 0) { $venvOk = $true }
    $ErrorActionPreference = "Stop"
}

if ($venvOk) {
    Write-Host "[2/5] Reusing existing venv at $VenvDir"
} else {
    Write-Host "[2/5] Creating venv at $VenvDir ..."
    New-Item -ItemType Directory -Force (Split-Path $VenvDir -Parent) | Out-Null
    & $basePy.Exe @($basePy.Args) -m venv $VenvDir
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $VenvPy)) { throw "venv creation failed." }
}

# --------------------------------------------------------------------------
# 3. Install dependencies (retry with --trusted-host on cert failures,
#    a known issue on this network -- see MCP.md)
# --------------------------------------------------------------------------
$trusted = @("--trusted-host", "pypi.org", "--trusted-host", "files.pythonhosted.org", "--trusted-host", "pypi.python.org")

function Invoke-Pip {
    param([string[]]$PipArgs, [string]$What)
    $ErrorActionPreference = "Continue"
    & $VenvPy -m pip install @PipArgs
    if ($LASTEXITCODE -ne 0) {
        Write-Host "      pip failed; retrying with --trusted-host flags ..." -ForegroundColor Yellow
        & $VenvPy -m pip install @trusted @PipArgs
    }
    $ErrorActionPreference = "Stop"
    if ($LASTEXITCODE -ne 0) { throw "pip install failed for $What." }
}

Write-Host "[3/5] Installing server dependencies (this can take a few minutes) ..."
Invoke-Pip -PipArgs @("-r", $Reqs) -What "requirements-mcp.txt"
Invoke-Pip -PipArgs @("-e", $EmbRoot) -What "poem_core (editable install)"

# --------------------------------------------------------------------------
# 4. Add/update the poem-search entry in LM Studio's mcp.json
# --------------------------------------------------------------------------
Write-Host "[4/5] Updating $CfgPath ..."
New-Item -ItemType Directory -Force $CfgDir | Out-Null

$cfg = $null
if (Test-Path $CfgPath) {
    $raw = Get-Content $CfgPath -Raw -ErrorAction SilentlyContinue
    if ($raw -and $raw.Trim()) {
        try { $cfg = $raw | ConvertFrom-Json }
        catch { throw "$CfgPath exists but is not valid JSON -- fix or delete it, then re-run. Not overwriting it." }
    }
}
if (-not $cfg) { $cfg = [pscustomobject]@{ mcpServers = [pscustomobject]@{} } }
if (-not $cfg.PSObject.Properties["mcpServers"]) {
    $cfg | Add-Member -MemberType NoteProperty -Name "mcpServers" -Value ([pscustomobject]@{})
}

# Forward slashes: valid on Windows and immune to JSON backslash-escaping bugs.
$entry = [pscustomobject]@{
    command = ($VenvPy -replace "\\", "/")
    args    = [string[]]@(($ServerPy -replace "\\", "/"))
}
if ($cfg.mcpServers.PSObject.Properties["poem-search"]) {
    $cfg.mcpServers."poem-search" = $entry
} else {
    $cfg.mcpServers | Add-Member -MemberType NoteProperty -Name "poem-search" -Value $entry
}

$json = $cfg | ConvertTo-Json -Depth 10
[System.IO.File]::WriteAllText($CfgPath, $json, (New-Object System.Text.UTF8Encoding($false)))

# --------------------------------------------------------------------------
# 5. Offline smoke test: get_statements needs no network, but does load the
#    full corpus + graph, so this also proves the server can start here.
# --------------------------------------------------------------------------
Write-Host "[5/5] Offline smoke test (loads corpus + graph; takes a minute) ..."
$ErrorActionPreference = "Continue"
& $VenvPy (Join-Path $McpDir "try_search.py") "RCADS-25-CG-EN"
$smokeExit = $LASTEXITCODE
$ErrorActionPreference = "Stop"

Write-Host ""
if ($smokeExit -eq 0) {
    Write-Host "== Setup complete ==" -ForegroundColor Green
} else {
    Write-Host "== Setup finished, but the smoke test FAILED (exit $smokeExit) ==" -ForegroundColor Yellow
    Write-Host "   Check the error above. Common causes: embeddings/TTLs missing on the share, or a dependency that failed to install."
}
Write-Host "Venv:   $VenvPy"
Write-Host "Config: $CfgPath  (server name: poem-search)"
Write-Host ""
Write-Host "Next: restart LM Studio -> Program tab (chip icon) -> enable 'poem-search'."
Write-Host "First load is slow (~860 embedding files + RDF graph)."
Write-Host "'search' needs the RPI network/VPN; 'get_statements' works offline."
