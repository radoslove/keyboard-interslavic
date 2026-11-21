#Requires -Version 5.1
<#
    uninstall.ps1 - remove the "Glagolitic (Interslavic)" keyboard layout (KBDISVGL).
    Run in an ELEVATED PowerShell (Run as administrator).
#>
$ErrorActionPreference = 'Stop'
$DllName = 'KBDISVGL.DLL'

$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
           ).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) { throw "Run this in an ELEVATED PowerShell (Run as administrator)." }

$root = 'HKLM:\SYSTEM\CurrentControlSet\Control\Keyboard Layouts'
$hits = Get-ChildItem $root -ErrorAction SilentlyContinue | Where-Object {
    (Get-ItemProperty -LiteralPath $_.PSPath -Name 'Layout File' -ErrorAction SilentlyContinue).'Layout File' -eq $DllName
}
if (-not $hits) {
    Write-Host "No registry entry found for $DllName (not installed?)."
} else {
    foreach ($h in $hits) {
        Remove-Item -LiteralPath $h.PSPath -Recurse -Force
        Write-Host "Removed registry key $($h.PSChildName)"
    }
}

foreach ($p in @((Join-Path $env:SystemRoot 'System32\KBDISVGL.DLL'),
                 (Join-Path $env:SystemRoot 'SysWOW64\KBDISVGL.DLL'))) {
    if (Test-Path $p) {
        try { Remove-Item -LiteralPath $p -Force; Write-Host "Deleted $p" }
        catch { Write-Host "Could not delete $p (in use?) - remove after a sign-out." }
    }
}
Write-Host "If it still shows in the language list, remove it under Settings -> Language -> Polish -> keyboards."
