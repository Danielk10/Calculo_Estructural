# Version Alfa 0.4.4 (versionCode 9)

Fecha: 15 de noviembre de 2026

Esta versión alfa (`v0.4.4`, código de versión `9`) corrige el anuncio intersticial para que esté listo cuando el usuario pulsa «Calcular» y no aparezca por sorpresa sobre la pantalla de resultados.

---

## 🛠️ Mejoras y Cambios

• **Precarga del intersticial:**
  • El anuncio se solicita al resolver el consentimiento UMP, al volver a la app y después de cerrar un intersticial, en lugar de pedirse solo al pulsar «Calcular».
  • Si el usuario calcula en cuanto abre la app, el anuncio suele estar ya cargado y se muestra de inmediato en la pausa natural.
• **Espera máxima de 3 segundos:**
  • Si al pulsar «Calcular» el anuncio aún no ha llegado, solo se muestra dentro de los 3 segundos siguientes.
  • Pasado ese plazo se descarta, en lugar de aparecer hasta 20 segundos después encima de los resultados o mientras el usuario hace otra cosa.
• **Recarga automática:**
  • Al cerrar o fallar un intersticial se pide el siguiente, de modo que cada cálculo no vuelve a empezar la descarga desde cero.
• **Sin cambios en el motor:**
  • CalculiX 2.23, OpenCASCADE, el banner adaptativo, sus reintentos progresivos (60/120/180 s), el consentimiento UMP y la arquitectura `arm64-v8a` se mantienen intactos.

---

## ✅ Validación

• `:app:bundleRelease` y `:app:assembleRelease` correctos con `compileSdk 37`, `buildTools 37.0.0`, `NDK 30.0.14904198` y `targetSdk 37`.
• APK y AAB firmados con `firma_structural_analysis_fea_3d.jks` (alias `civil`).
• `versionCode 9` y `versionName 0.4.4`.
• `minSdk 24` (Android 7.0), `applicationId com.diamon.civil`.

---

## 📦 Artefactos

• `app-release.apk` — 219164338 bytes
  • SHA-256: `457b11c2249bde11b98a62dd76b0a2e170e4e4414695acd341acc2886207cff1`
• `app-release.aab` — 218053543 bytes
  • SHA-256: `51409e61a375ce1a83bb217865a0300febb41540f7f15533fc342230d9f68f51`

---

## 🔗 Enlaces

• Términos del Servicio: https://todoandroid.42web.io/terminos-servicio-calculo-estructural.html
• Política de privacidad: https://todoandroid.42web.io/politica_privacidad_structural_analysis_fea_3d.html

---

## 📜 Licencia

Cálculo Estructural se distribuye bajo GNU GPL v3.0 por integrar CalculiX 2.23. El código fuente está disponible en este repositorio.
