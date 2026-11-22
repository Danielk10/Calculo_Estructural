# Version Alfa 0.4.5 (versionCode 10)

Fecha: 22 de noviembre de 2026

Esta versión alfa (`v0.4.5`, código de versión `10`) añade el anuncio nativo de AdMob en las pantallas de Cálculo Estructural y Sólidos 3D, iguala el banner inferior al del resto de las apps, activa la pantalla completa inmersiva y reduce el espacio vacío que quedaba encima del título.

---

## 🛠️ Mejoras y Cambios

• **Anuncio nativo en Cálculo Estructural y Sólidos 3D:**
  • Nueva unidad nativa `NativvoFEA` (`ca-app-pub-5141499161332805/4375469982`).
  • En Cálculo Estructural se muestra en formato compacto dentro de la pestaña «Parámetros», justo encima del botón de cálculo.
  • En Sólidos 3D usa el formato grande de PIC K150 (icono, titular, anunciante, cuerpo, imagen/vídeo y botón), ocupando el espacio libre de la pantalla.
  • El hueco permanece oculto hasta que AdMob confirma el relleno: nunca queda un espacio vacío.
  • Cada pantalla hace su propia petición y destruye su anuncio al salir, como exigen las políticas de AdMob.
  • Reintentos automáticos (45/90/135 s) cuando no hay relleno y espera al consentimiento UMP.
• **Banner inferior igual al resto de las apps:**
  • Se eliminó el fondo blanco del contenedor que dejaba franjas en modo oscuro.
  • El banner adaptable se recalcula al girar la pantalla, ya que la actividad no se recrea.
• **Pantalla completa y modo inmersivo:**
  • Se ocultan las barras de estado y navegación con `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`.
  • Se vuelven a ocultar al recuperar el foco y al volver a la app.
• **Barra superior con la altura correcta:**
  • El relleno superior ya no suma el recorte de cámara (`displayCutout`) cuando las barras están ocultas, que era lo que dejaba una franja azul vacía encima del título.
  • La barra queda con los mismos 56 dp del resto de las apps.
• **Sin cambios en el motor:**
  • CalculiX 2.23, OpenCASCADE, el intersticial con límite de frecuencia y la arquitectura `arm64-v8a` se mantienen intactos.

---

## ✅ Validación

• `:app:bundleRelease` correcto con `compileSdk 37`, `buildTools 37.0.0`, `NDK 30.0.14904198` y `targetSdk 37`.
• AAB firmado con `firma_structural_analysis_fea_3d.jks` (alias `civil`).
• `versionCode 10` y `versionName 0.4.5`.
• `minSdk 24` (Android 7.0), `applicationId com.diamon.civil`.

---

## 🔗 Enlaces

• Términos del Servicio: https://todoandroid.42web.io/terminos-servicio-calculo-estructural.html
• Política de privacidad: https://todoandroid.42web.io/politica_privacidad_structural_analysis_fea_3d.html

---

## 📜 Licencia

Cálculo Estructural se distribuye bajo GNU GPL v3.0 por integrar CalculiX 2.23. El código fuente está disponible en este repositorio.
