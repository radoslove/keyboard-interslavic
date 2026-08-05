#Requires -Version 5.1
<#
    install.ps1 - install the "Medzuslovjansky (standard)" keyboard layout (KBDMSSTD).

    Standard-orthography Interslavic Latin layout. AltGr carries c-caron / s-caron /
    z-caron / e-caron (c s z e) plus the punctuation our texts use. No dead keys.
    Appears under Polish in the Windows language list (no phantom Slovenian).

    USAGE
      Right-click PowerShell -> "Run as administrator", then:
        .\install.ps1
      Preview without changing anything (no admin needed):
        .\install.ps1 -DryRun

    Uninstall with .\uninstall.ps1 (also elevated).
#>
param([switch]$DryRun)

$ErrorActionPreference = 'Stop'
$here       = Split-Path -Parent $MyInvocation.MyCommand.Path
$DllName    = 'KBDMSSTD.DLL'
$LayoutText = 'Medzuslovjansky (standard)'
$BaseLang   = '0415'   # pl-PL

# --- admin check (skipped for -DryRun) ---
$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
           ).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $DryRun -and -not $isAdmin) {
    throw "Run this in an ELEVATED PowerShell (Run as administrator), or use -DryRun to preview."
}

# --- pick the DLLs to copy, by OS architecture ---
$sys32  = Join-Path $env:SystemRoot 'System32'
$syswow = Join-Path $env:SystemRoot 'SysWOW64'
$copies = @()
if ([Environment]::Is64BitOperatingSystem) {
    $copies += @{ Src = (Join-Path $here 'amd64\KBDMSSTD.dll'); Dst = (Join-Path $sys32  $DllName) }
    $copies += @{ Src = (Join-Path $here 'wow64\KBDMSSTD.dll'); Dst = (Join-Path $syswow $DllName) }
} else {
    $copies += @{ Src = (Join-Path $here 'i386\KBDMSSTD.dll');  Dst = (Join-Path $sys32  $DllName) }
}
foreach ($c in $copies) {
    if (-not (Test-Path $c.Src)) { throw "Missing build artifact: $($c.Src)" }
}

# --- registry: reuse if already installed, else pick a free KLID + Layout Id ---
$root     = 'HKLM:\SYSTEM\CurrentControlSet\Control\Keyboard Layouts'
$existing = Get-ChildItem $root -ErrorAction SilentlyContinue
$usedKlid = @($existing | ForEach-Object { $_.PSChildName.ToLower() })
$usedLid  = @()
foreach ($k in $existing) {
    $lid = (Get-ItemProperty -LiteralPath $k.PSPath -Name 'Layout Id' -ErrorAction SilentlyContinue).'Layout Id'
    if ($lid) { $usedLid += [Convert]::ToInt32($lid, 16) }
}

$already = $existing | Where-Object {
    (Get-ItemProperty -LiteralPath $_.PSPath -Name 'Layout File' -ErrorAction SilentlyContinue).'Layout File' -eq $DllName
} | Select-Object -First 1

if ($already) {
    $klid = $already.PSChildName
    $lidHex = (Get-ItemProperty -LiteralPath $already.PSPath -Name 'Layout Id' -ErrorAction SilentlyContinue).'Layout Id'
    if (-not $lidHex) { $lidHex = '00d1' }
    $reuse = $true
} else {
    $reuse = $false
    # KLID: custom layouts based on pl-PL are a000<0415>, a001<0415>, ...
    $klid = $null
    for ($i = 0; $i -lt 256; $i++) {
        $cand = ('a{0:x2}0{1}' -f $i, $BaseLang)   # a00 + 0415 = a0000415, a0010415, ...
        if ($usedKlid -notcontains $cand) { $klid = $cand; break }
    }
    if (-not $klid) { throw "No free KLID in the a##0$BaseLang range." }
    # Layout Id: first free 4-hex value at/after 00d0
    $lid = 0x00d0
    while ($usedLid -contains $lid) { $lid++ }
    $lidHex = ('{0:x4}' -f $lid)
}

# --- report ---
$mode = if ($DryRun) { 'DRY RUN' } else { 'INSTALL' }
Write-Host ""
Write-Host "Medzuslovjansky (standard) keyboard - $mode" -ForegroundColor Cyan
Write-Host ("  KLID       : {0}{1}" -f $klid, $(if($reuse){' (already registered - reusing)'}else{''}))
Write-Host ("  Layout Id  : {0}" -f $lidHex)
Write-Host ("  Layout Text: {0}" -f $LayoutText)
Write-Host  "  DLL copies :"
foreach ($c in $copies) { Write-Host ("    {0}  ->  {1}" -f $c.Src, $c.Dst) }

if ($DryRun) {
    Write-Host ""
    Write-Host "Dry run - nothing changed. Re-run elevated without -DryRun to install." -ForegroundColor Yellow
    return
}

# --- do it ---
foreach ($c in $copies) { Copy-Item -LiteralPath $c.Src -Destination $c.Dst -Force }
$key = Join-Path $root $klid
New-Item -Path $key -Force | Out-Null
Set-ItemProperty -Path $key -Name 'Layout File' -Value $DllName
Set-ItemProperty -Path $key -Name 'Layout Text' -Value $LayoutText
Set-ItemProperty -Path $key -Name 'Layout Id'   -Value $lidHex

Write-Host ""
Write-Host "Installed." -ForegroundColor Green
Write-Host "Add it to your input methods:"
Write-Host "  Settings -> Time & language -> Language & region -> Polish -> ... -> Language options"
Write-Host "  -> Add a keyboard -> 'Medzuslovjansky (standard)'."
Write-Host "Then switch with Win+Space. (A sign-out/in helps if it does not appear immediately.)"
