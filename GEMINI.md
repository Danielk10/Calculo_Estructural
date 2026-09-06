# Instrucciones de Compilación, SDK y Automatización

Este documento describe la configuración del SDK de Android, compilación, firma y publicación del proyecto **Structural Analysis FEA 3D** (`com.diamon.civil`).

---

## 1. Instalación del SDK y NDK (Requisito Previo Obligatorio)

Antes de compilar, si el SDK no está configurado en el entorno o no existe `/tmp/android-sdk` / `local.properties`, es **obligatorio** ejecutar el script de configuración:

```bash
bash setup-sdk.sh
```

Este script descarga e instala automáticamente el Android SDK, NDK, CMake, build-tools y genera el archivo `local.properties`.

- **Ubicación del SDK:** `/tmp/android-sdk`
- **Ubicación de Build Outputs:** `/tmp/calculoestructural_build`

---

## 2. Comandos de Compilación

```bash
# Compilar APK Debug
./gradlew assembleDebug

# Compilar APK Release (firmado automáticamente con keystore.properties o variables SIGNING_*)
./gradlew assembleRelease

# Compilar App Bundle (.aab) Release para Google Play Store
./gradlew bundleRelease
```

---

## 3. Ubicación de Artefactos Generados (Outputs)

Los archivos compilados se generan fuera del espacio de trabajo para mantenerlo limpio:

- **APK Debug:** `/tmp/calculoestructural_build/outputs/apk/debug/app-debug.apk`
- **APK Release:** `/tmp/calculoestructural_build/outputs/apk/release/app-release.apk`
- **AAB Release (Google Play):** `/tmp/calculoestructural_build/outputs/bundle/release/app-release.aab`

---

## 4. Firma para Producción (Release Signing)

La configuración de firma en `app/build.gradle` carga automáticamente las credenciales desde `keystore.properties` en la raíz del proyecto (o variables de entorno `SIGNING_*` si no existe el archivo).

### Archivo `keystore.properties` (Ignorado por Git):
```properties
storeFile=/ruta/a/tu/firma.jks
storePassword=TU_PASSWORD_AQUI
keyAlias=tu_alias
keyPassword=TU_PASSWORD_AQUI
```

---

## 5. Publicación Automática en Google Play Store 🚀

El script `upload_play_store.py` permite subir automáticamente el bundle `.aab` a Google Play Console.

```bash
python upload_play_store.py \
  --package_name com.diamon.civil \
  --aab_path /tmp/calculoestructural_build/outputs/bundle/release/app-release.aab \
  --service_account_json /ruta/a/tu/credentials.json \
  --track production \
  --release_notes "- Resumen de cambios en español." \
  --release_notes_en "- Summary of changes in English."
```

### Limpieza Post-Subida (Obligatorio)
```bash
rm -rf /tmp/calculoestructural_build/outputs/bundle/release/
rm -rf /tmp/calculoestructural_build/outputs/apk/release/
```

---

## 📦 Reglas para hacer un Pre-lanzamiento (Pre-release)

Cuando se solicite hacer un pre-lanzamiento, **DEBES** seguir estrictamente estas instrucciones claras:

1. **Escribir un mensaje claro de lo que se hizo:** Debes redactar un resumen claro de todas las funcionalidades, arreglos o cambios que incluye esta nueva versión.
2. **Aumentar la versión:** Debes definir y proponer el nuevo número de versión incrementado (por ejemplo, `v0.2.0`).
3. **Preguntar al desarrollador:** Antes de lanzar, debes preguntarle al desarrollador si desea enviar la release usando el comando `gh release create`.
4. **Comando de creación de release:** Si el desarrollador te da el "Ok", debes ejecutar el siguiente comando (sustituyendo la versión correspondiente y las notas reales):

```bash
gh release create v0.1.0 /tmp/calculoestructural_build/outputs/apk/release/app-release.apk --title "Version Alfa 0.1.0" --notes "Mensaje claro de lo que se hizo" --prerelease
```

**⚠️ REGLAS CRÍTICAS DEL COMANDO:**
- La ruta del APK **siempre** es el APK de lanzamiento (Release): `/tmp/calculoestructural_build/outputs/apk/release/app-release.apk`.
- Debes incluir el flag `--prerelease`.
