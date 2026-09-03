# Política de Privacidad — Structural Analysis FEA 3D

**Última actualización:** 2 de Septiembre de 2026  
**Nombre del Paquete:** `com.diamon.civil`  
**Desarrollador Responsable:** Daniel Diamon  
**Licencia:** GNU General Public License v3.0 (GPLv3)  
**Repositorio Oficial:** [https://github.com/Danielk10/Calculo_Estructural](https://github.com/Danielk10/Calculo_Estructural)

---

## 🔒 1. Compromiso de Privacidad y Procesamiento Local (100% Offline)

La aplicación **Structural Analysis FEA 3D** procesa todas las simulaciones, modelos 3D y cálculos de ingeniería por elementos finitos (FEA) de forma **100% local** en el procesador del dispositivo móvil (arquitectura nativa ARM64-v8a). 

- **Sin servidores externos:** Sus archivos de diseño geométrico (`.step`, `.stp`, `.iges`, `.igs`, `.brep`, `.geo`), mallas (`.inp`, `.msh`) y memorias de cálculo en formato PDF nunca se cargan ni se transfieren a servidores externos o nubes privadas.
- **Sin recopilación de datos personales:** No recopilamos, almacenamos ni compartimos información de identificación personal (PII) como nombres, correos electrónicos, números de teléfono, contactos ni ubicación geográfica.

---

## ⚙️ 2. Motores Numéricos de Alto Rendimiento Integrados

La aplicación ejecuta directamente en el dispositivo móvil los siguientes motores industriales de código abierto:
- **CalculiX (CCX 2.23):** Solver implícito de elementos finitos no lineales para vigas Timoshenko (B31/B32), losas bidireccionales (S4R), membranas/muros (CPS4) y sólidos 3D (C3D4, C3D10).
- **OpenCASCADE Technology (OCCT 8.0.0.p1):** Modelado geométrico CAD paramétrico, costura de superficies NURBS y primitivas volumétricas.
- **Gmsh (5.0.0):** Generador de mallas tetraédricas de segundo orden.

---

## 🛡️ 3. Permisos del Sistema Android y Justificación Técnica

Para garantizar un rendimiento óptimo y compatibilidad completa desde **Android 7.0 (API Level 24)** hasta **Android 17 (API Level 37)**, la aplicación solicita exclusivamente los siguientes permisos técnicos:

| Permiso | Nivel de API | Justificación y Propósito Técnico |
| :--- | :--- | :--- |
| `android.permission.WAKE_LOCK` | API 24 – API 37 | **Prevención de Inactividad durante el Cálculo:** Mantiene activo el procesador (CPU) y la pantalla encendida mientras se ejecutan análisis matriciales densos o mallados volumétricos pesados de CalculiX y Gmsh, impidiendo que el sistema operativo suspenda o congele el cálculo a mitad de ejecución. |
| `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | API 24 – API 37 | **Exclusión Opcional del Modo Doze:** Permite al usuario solicitar que la app no sea ralentizada por los perfiles agresivos de ahorro de batería durante simulaciones estructurales de larga duración. |
| `android.permission.INTERNET` | API 24 – API 37 | **Servicios de Red:** Permite visualizar esta política de privacidad en línea, acceder a la documentación técnica oficial, y gestionar compras dentro de la app (Google Play In-App Billing) y anuncios contextuales de Google AdMob. |
| `android.permission.ACCESS_NETWORK_STATE` | API 24 – API 37 | **Estado de Conexión:** Comprueba la disponibilidad de conexión a Internet para optimizar la carga de manuales y recursos web. |
| `Storage Access Framework (SAF)` | API 24 – API 37 | **Importación / Exportación Segura:** Permite al usuario seleccionar archivos CAD (`.step`, `.iges`, `.brep`, `.inp`) desde cualquier carpeta del dispositivo y guardar reportes de cálculo en PDF en la carpeta pública `Download/` mediante el selector nativo del sistema sin requerir permisos invasivos de almacenamiento global. |

---

## 📊 4. Servicios de Terceros

Para financiar el mantenimiento del proyecto y ofrecer funcionalidades comerciales opcionales, se utilizan servicios de terceros auditados por Google:
- **Google Play In-App Billing:** Gestión segura de compras de licencias o donaciones.
- **Google AdMob:** Visualización de publicidad no invasiva. AdMob puede utilizar identificadores de publicidad anónimos conforme a las [Políticas de Privacidad de Google](https://policies.google.com/privacy).
- **Google Play Console:** Métricas anónimas de estabilidad y reportes de cierres inesperados (ANR / Crash reports).

---

## 👶 5. Privacidad de Menores (COPPA)

La aplicación está diseñada para estudiantes, ingenieros y profesionales de la construcción y no está dirigida a niños menores de 13 años. No recopilamos deliberadamente ninguna información de menores de edad.

---

## 📜 6. Seguridad y Derechos del Usuario (GDPR / CCPA)

- **Aislamiento en Sandbox:** Todos los archivos de trabajo temporales se almacenan en el directorio privado de la aplicación (`filesDir/3d_solid_analysis`, `filesDir/terminal`) con cifrado a nivel de sistema.
- **Control Total:** El usuario puede revocar permisos o desinstalar la aplicación en cualquier momento, lo que eliminará de forma irreversible cualquier dato o caché almacenado en el dispositivo.

---

## 📬 7. Contacto

Para consultas o comentarios sobre esta política de privacidad:
- **Correo Electrónico:** [danieldiamon@gmail.com](mailto:danieldiamon@gmail.com)
- **Sitio Web Oficial:** [https://todoandroid.42web.io](https://todoandroid.42web.io)
