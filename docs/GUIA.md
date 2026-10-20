# Guía: publicar LibreDescarga en GitHub (paso a paso)

Escrita para no programadores. Sigue los pasos en orden.

## Lo que ya está listo

- **Tu llave de firma definitiva** está creada en `C:\Users\w_ale\LibreDescarga-claves\`:
  - `libredescarga.jks` → la llave.
  - `LEEME-contrasenas.txt` → su contraseña.
- **El proyecto ya sabe usarla** (archivo `keystore.properties` en la carpeta del proyecto; no se sube a GitHub).
- **Un solo comando** compila, firma, verifica y prepara todo: `tools\publicar-release.ps1`.

> ### Lo más importante: respalda la llave
> Copia **la carpeta `LibreDescarga-claves` completa** a un USB y a un lugar privado de tu nube (Drive personal, por ejemplo).
> **Si la pierdes, nunca más podrás publicar actualizaciones** que se instalen sobre la app que ya tienen tus usuarios.
> Nunca la subas a GitHub ni la compartas.

---

## Paso 1: crear tu cuenta y tu repositorio en GitHub (una sola vez)

1. Entra a github.com y crea una cuenta (o inicia sesión).
2. Pulsa **New repository**.
3. Nombre: `LibreDescarga` (o el que prefieras). Déjalo **Public**.
4. Anota dos datos: tu **usuario** de GitHub y el **nombre del repositorio**. Los usarás ahora.

## Paso 2: poner tus datos en la app (una sola vez)

Abre el archivo `core/config/src/main/kotlin/com/libredescarga/core/config/AppConfig.kt`
(en Android Studio: **Ctrl + Shift + N**, escribe `AppConfig`) y cambia:

- `GITHUB_OWNER` → tu usuario de GitHub.
- `GITHUB_REPO` → el nombre del repositorio.
- `LEGAL_CONTACT_EMAIL` → un correo para avisos legales (no uses el de PayPal).

Estos datos quedan dentro de la app: de ahí saca las actualizaciones.

## Paso 3: subir el código a GitHub (una sola vez)

La app es de código abierto (licencia GPL-3.0, por la librería que descarga los videos), así que el código debe ser público.
Sube la carpeta del proyecto con **GitHub Desktop** (File → Add local repository → Publish repository).
El archivo `.gitignore` ya evita subir tu llave y tus contraseñas.

## Paso 4: generar los instaladores de la versión

En una terminal, dentro de la carpeta del proyecto:

```
.\tools\publicar-release.ps1 -Repositorio "TuUsuario/LibreDescarga" -NotasEs "Primera versión" -NotasEn "First release"
```

- Tarda unos minutos. Si sale un error con "Failed to delete" (OneDrive bloquea archivos), agrega `-Limpiar` al final y repite.
- Al terminar deja todo en `release\v1.0.0\`:
  - `LibreDescarga-1.0.0-arm64-v8a.apk` → para casi todos los celulares.
  - Otros APK para celulares antiguos y uno universal.
  - `update.json` → el archivo con el que la app descubre versiones nuevas.
- Si el comando dice que `AppConfig.kt` no coincide con tu repositorio, vuelve al Paso 2.

## Paso 5: publicar la versión en GitHub

1. En tu repositorio: **Releases → Draft a new release**.
2. En **Tag** escribe `v1.0.0` (la letra v + el número de versión). El comando te lo recuerda al final.
3. Arrastra **todo lo que hay en la carpeta `release\v1.0.0`**: los `.apk` y `update.json`.
4. Pulsa **Publish release**.

Desde ese momento tus amigos pueden descargar el APK desde la página de la versión.

## Paso 6: publicar una versión nueva más adelante

1. Abre `app/build.gradle.kts` y sube los dos números:
   - `appVersionCode` → siempre **mayor** que el anterior (2, 3, 4…).
   - `appVersionName` → el nombre visible (por ejemplo `1.0.1`).
2. Ejecuta de nuevo el comando del Paso 4 (con las notas de la nueva versión).
3. Crea una Release nueva con el tag `v1.0.1` y sube los archivos.

La app de tus usuarios la ofrecerá en **Ajustes → Actualizaciones → Buscar**, la descargará, verificará que no fue alterada
(huella SHA-256 y firma) y pedirá instalarla.

## Paso 7: probar que las actualizaciones funcionan

1. Instala la versión 1 en un teléfono.
2. Publica la versión 2 (Paso 6).
3. En el teléfono: **Ajustes → Actualizaciones → Buscar**. Debe ofrecer la nueva.

---

## Cosas que debes saber

- **Versiones de prueba:** los instaladores que mandaste antes a tus amigos estaban firmados con otra llave (la de depuración).
  Para pasar a la versión oficial deben **desinstalar** esa e instalar la nueva. De ahí en adelante se actualizará sola.
- **Licencia:** si alguien te pide el código fuente, debes dárselo (ya está público en GitHub).
- **F-Droid:** esta tienda no permite que una app se actualice por su cuenta. Para estar allí habría que hacer una variante sin el actualizador.
- **Restricción de edad:** las canciones o videos con restricción de edad de YouTube no se pueden descargar sin iniciar sesión, y la app no usa cuentas.
- **Lo que falta confirmar:** la app se ha probado sobre todo en un solo celular. Pide a tus amigos que anoten el modelo de su teléfono y qué hacían cuando algo falle.
