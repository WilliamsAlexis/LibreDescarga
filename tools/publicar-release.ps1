<#
.SYNOPSIS
    Prepara una versión de LibreDescarga lista para subir a GitHub Releases.

.DESCRIPTION
    1. Comprueba que tengas tu llave de firma (keystore.properties) y que AppConfig.kt apunte a tu repositorio.
    2. Compila los APK optimizados y firmados con tu llave.
    3. Verifica la firma de cada APK.
    4. Crea update.json (con la huella SHA-256 de cada APK) para que la app avise de versiones nuevas.
    Al final deja todo en la carpeta release\v<versión> y te dice qué subir a GitHub.

.EXAMPLE
    .\tools\publicar-release.ps1 -Repositorio "TuUsuario/LibreDescarga" -NotasEs "Primera versión" -NotasEn "First release"

.NOTES
    La versión sale de app\build.gradle.kts (appVersionCode y appVersionName). Súbela ahí antes de cada publicación.
    Si OneDrive bloquea archivos al compilar, usa -Limpiar o mueve el proyecto fuera de OneDrive.
#>
param(
    [Parameter(Mandatory = $true)][string]$Repositorio,
    [string]$NotasEs = "",
    [string]$NotasEn = "",
    [switch]$Limpiar
)

$ErrorActionPreference = "Stop"
$raiz = Split-Path -Parent $PSScriptRoot
Set-Location $raiz

function Fallar($mensaje) { Write-Host ""; Write-Host "ERROR: $mensaje" -ForegroundColor Red; exit 1 }

# --- 1. Comprobaciones ---------------------------------------------------------------------------------
if (-not (Test-Path "keystore.properties")) {
    Fallar "Falta keystore.properties (tu llave de firma). Mira docs\GUIA.md, paso 3."
}
$props = @{}
Get-Content "keystore.properties" | ForEach-Object { if ($_ -match '^\s*([^#=]+?)\s*=\s*(.*)$') { $props[$Matches[1]] = $Matches[2] } }
if (-not (Test-Path $props["storeFile"])) { Fallar "No encuentro el archivo de la llave: $($props['storeFile'])" }

$gradle = Get-Content "app\build.gradle.kts" -Raw
if ($gradle -notmatch 'val appVersionCode = (\d+)') { Fallar "No pude leer appVersionCode en app\build.gradle.kts" }
$versionCode = [int]$Matches[1]
if ($gradle -notmatch 'val appVersionName = "([^"]+)"') { Fallar "No pude leer appVersionName en app\build.gradle.kts" }
$versionName = $Matches[1]

$config = Get-Content "core\config\src\main\kotlin\com\libredescarga\core\config\AppConfig.kt" -Raw
$duenio = ([regex]::Match($config, 'GITHUB_OWNER = "([^"]+)"')).Groups[1].Value
$repo = ([regex]::Match($config, 'GITHUB_REPO = "([^"]+)"')).Groups[1].Value
if ("$duenio/$repo" -ne $Repositorio) {
    Fallar ("AppConfig.kt dice '$duenio/$repo' pero pediste '$Repositorio'. La app lleva dentro la dirección de GitHub: " +
        "cambia GITHUB_OWNER y GITHUB_REPO en core\config\...\AppConfig.kt y vuelve a ejecutar.")
}
if ($config -match 'legal@libredescarga\.example') {
    Write-Host "AVISO: LEGAL_CONTACT_EMAIL sigue con el correo de ejemplo. Cámbialo en AppConfig.kt antes de publicar." -ForegroundColor Yellow
}

if (-not $env:JAVA_HOME) { $env:JAVA_HOME = Join-Path $env:ProgramFiles "Android\Android Studio\jbr" }
$buildTools = Get-ChildItem (Join-Path $env:LOCALAPPDATA "Android\Sdk\build-tools") -Directory | Sort-Object Name | Select-Object -Last 1
$apksigner = Join-Path $buildTools.FullName "apksigner.bat"

Write-Host "Versión $versionName (código $versionCode) para $Repositorio" -ForegroundColor Cyan

# --- 2. Compilar -------------------------------------------------------------------------------------------
if ($Limpiar) {
    Write-Host "Limpiando carpetas temporales de compilación..."
    & .\gradlew.bat --stop | Out-Null
    Get-ChildItem -Recurse -Directory -Filter build -Depth 3 -ErrorAction SilentlyContinue |
        Where-Object { (Test-Path (Join-Path $_.Parent.FullName 'build.gradle.kts')) -or $_.FullName -match 'build-logic\\convention\\build$' } |
        ForEach-Object { Remove-Item -Recurse -Force $_.FullName -ErrorAction SilentlyContinue }
}
Write-Host "Compilando (tarda unos minutos)..."
# Gradle y apksigner escriben avisos normales por la salida de errores: no deben detener el proceso.
$ErrorActionPreference = "Continue"
& .\gradlew.bat :app:assembleRelease
$ErrorActionPreference = "Stop"
if ($LASTEXITCODE -ne 0) { Fallar "La compilación falló. Si ves 'Failed to delete' o 'Cannot snapshot' (OneDrive bloquea archivos), ejecuta de nuevo con -Limpiar." }

# --- 3. Copiar y verificar --------------------------------------------------------------------------------
$destino = Join-Path $raiz "release\v$versionName"
if (Test-Path $destino) { Remove-Item -Recurse -Force $destino }
New-Item -ItemType Directory -Force $destino | Out-Null
$apks = Get-ChildItem "app\build\outputs\apk\release" -Filter "app-*-release.apk"
if ($apks.Count -eq 0) { Fallar "No se generaron APK firmados. Revisa keystore.properties." }
foreach ($apk in $apks) {
    $nombre = $apk.Name.Replace("app-", "LibreDescarga-$versionName-").Replace("-release.apk", ".apk")
    Copy-Item $apk.FullName (Join-Path $destino $nombre)
}
Get-ChildItem $destino -Filter *.apk | ForEach-Object {
    # Se ejecuta con cmd para que los avisos de apksigner no se confundan con errores.
    $resultado = cmd /c "`"$apksigner`" verify --verbose `"$($_.FullName)`" 2>&1" | Out-String
    if ($resultado -notmatch "Verifies") { Fallar "La firma de $($_.Name) no es válida:`n$resultado" }
    Write-Host ("  firmado OK  {0}  ({1:N1} MB)" -f $_.Name, ($_.Length / 1MB))
}

# --- 4. update.json ---------------------------------------------------------------------------------------
& (Join-Path $PSScriptRoot "crear-update-json.ps1") -Carpeta $destino -VersionCode $versionCode -VersionName $versionName `
    -NotasEs $NotasEs -NotasEn $NotasEn -Repositorio $Repositorio

Write-Host ""
Write-Host "LISTO. Sube a GitHub (Releases -> Draft a new release):" -ForegroundColor Green
Write-Host "  Etiqueta (tag): v$versionName"
Write-Host "  Archivos: todo lo que hay en $destino  (los .apk y update.json)"
Write-Host "  Luego pulsa Publish release."
