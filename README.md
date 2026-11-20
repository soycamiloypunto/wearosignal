# WearOSignal

Cliente independiente (no oficial) de Signal para **Wear OS**, compatible desde **Samsung Galaxy Watch 4** en adelante (Wear OS 3+ / API 30+, arquitecturas `armeabi-v7a` y `arm64-v8a`).

**Developed by Cristian Camilo**

## Características y seguridad

- **Conexión directa y oficial**: se comunica únicamente con los servidores oficiales de Signal (`*.signal.org`) mediante `libsignal` y `libsignal-service` (sin servidores intermedios, telemetría ni rastreadores).
- **Cifrado local**: credenciales y claves de identidad protegidas en reposo mediante **Android Keystore (AES-256-GCM)**.
- **Preparado para Google Play Store**:
  - Sin `USE_EXACT_ALARM` ni `SCHEDULE_EXACT_ALARM` (usa `setAndAllowWhileIdle` + `WorkManager`).
  - Soporte `armeabi-v7a` + `arm64-v8a` en `release`.
  - Minificación/R8 configurada (`proguard-rules.pro`) y opciones de debug ocultas en `release`.
- **Experiencia Wear OS**:
  - Interfaz en **español** e **inglés**.
  - Soporte de bisel giratorio / corona, respuestas rápidas, reacciones, imágenes recibidas, Tile y Complication.
  - Botón **Desvincular reloj** en Ajustes con borrado completo de datos locales.

## Compilar e instalar por ADB inalámbrico

```powershell
# Compilar APK debug
.\gradlew.bat :app:assembleDebug

# Emparejar e instalar en Samsung Galaxy Watch
adb pair <IP>:<PUERTO_EMPAREJAMIENTO>
adb connect <IP>:<PUERTO>
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## Licencia

Distribuido bajo licencia **GNU Affero General Public License v3.0 (AGPLv3)** — ver [LICENSE](LICENSE).
