<#
.SYNOPSIS
    Crea el archivo update.json que LibreDescarga lee para avisar de versiones nuevas.

.DESCRIPTION
    Lee los APK de una carpeta, calcula la "huella" (SHA-256) de cada uno y escribe update.json.
    Ese archivo se sube a GitHub Releases junto con los APK.

.EXAMPLE
    .\tools\crear-update-json.ps1 -Carpeta .\salida -VersionCode 2 -VersionName 1.0.1 `
        -NotasEs "Corrección de errores" -NotasEn "Bug fixes" -Repositorio "TuUsuario/LibreDescarga"
#>
param(
    [Parameter(Mandatory = $true)][string]$Carpeta,
    [Parameter(Mandatory = $true)][int]$VersionCode,
    [Parameter(Mandatory = $true)][string]$VersionName,
    [string]$NotasEs = "",
    [string]$NotasEn = "",
    [int]$MinSdk = 24,
    # Usuario/repositorio de GitHub. Debe ser el mismo que pusiste en AppConfig.kt.
    [string]$Repositorio = "LibreDescarga/LibreDescarga"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $Carpeta)) { throw "No existe la carpeta: $Carpeta" }
$archivos = Get-ChildItem -Path $Carpeta -Filter *.apk
if ($archivos.Count -eq 0) { throw "No hay archivos .apk en $Carpeta" }

$etiqueta = "v$VersionName"
$apks = @()

foreach ($apk in $archivos) {
    # El nombre del APK dice para qué tipo de teléfono es (por ejemplo: app-arm64-v8a-release.apk).
    if ($apk.Name -notmatch '-(arm64-v8a|armeabi-v7a|x86_64|x86|universal)(-|\.apk)') {
        Write-Warning "Se omite $($apk.Name): no se reconoce su arquitectura en el nombre."
        continue
    }
    $tipo = $Matches[1]
    $huella = (Get-FileHash -Path $apk.FullName -Algorithm SHA256).Hash.ToLower()
    $entrada = [ordered]@{}
    if ($tipo -ne "universal") { $entrada["abi"] = $tipo }
    $entrada["url"] = "https://github.com/$Repositorio/releases/download/$etiqueta/$($apk.Name)"
    $entrada["sha256"] = $huella
    $apks += $entrada
    Write-Host ("  {0,-14} {1}" -f $tipo, $huella)
}

if ($apks.Count -eq 0) { throw "Ningún APK tiene un nombre reconocible." }

$notas = [ordered]@{}
if ($NotasEs) { $notas["es"] = $NotasEs }
if ($NotasEn) { $notas["en"] = $NotasEn }

$manifiesto = [ordered]@{
    versionCode = $VersionCode
    versionName = $VersionName
    minSdk      = $MinSdk
    notes       = $notas
    apks        = $apks
}

$json = $manifiesto | ConvertTo-Json -Depth 6
$destino = Join-Path $Carpeta "update.json"
# UTF-8 sin BOM, como espera la app.
[System.IO.File]::WriteAllText($destino, $json, (New-Object System.Text.UTF8Encoding $false))

Write-Host ""
Write-Host "Listo: $destino"
Write-Host "Sube a la versión '$etiqueta' de GitHub Releases: los APK y este update.json."
