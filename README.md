# Structural Analysis FEA Advanced

[![Android](https://img.shields.io/badge/Android-7.0%2B%20(API%2024--37)-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Architecture](https://img.shields.io/badge/Architecture-ARM64--v8a-blue?logo=arm&logoColor=white)](https://developer.android.com/ndk)
[![CalculiX](https://img.shields.io/badge/CalculiX%20FEA-v2.23%20MT-darkgreen)](https://www.calculix.de)
[![OpenCASCADE](https://img.shields.io/badge/OpenCASCADE-v8.0.0.p1-orange)](https://www.opencascade.com)
[![Gmsh](https://img.shields.io/badge/Gmsh-v5.0.0-red)](https://gmsh.info)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Gradle](https://img.shields.io/badge/Gradle-9.6.0-02303A?logo=gradle&logoColor=white)](https://gradle.org)
[![AGP](https://img.shields.io/badge/AGP-9.2.1-green)](https://developer.android.com/build)

**Structural Analysis FEA Advanced** (`com.diamon.civil`) es una plataforma profesional de cálculo estructural y análisis por elementos finitos (FEA 3D) para Android. Integra de forma nativa los motores computacionales industriales **CalculiX (`ccx 2.23`)**, **OpenCASCADE Technology (`OCCT 8.0.0.p1`)** y **Gmsh** a través del **Android NDK (ARM64-v8a)** con enlaces de alto rendimiento en **Java, Kotlin y C++ (JNI)**.

---

## 📜 Declaración de Licencia y Cumplimiento GPL v3.0

### Proyecto basado en CalculiX 2.23 - Cumplimiento Copyleft GPL
Este repositorio contiene el código Android de la aplicación y el núcleo integrado de **CalculiX Version 2.23** como solucionador por elementos finitos (FEA). Todo el proyecto conjunto está licenciado bajo **GNU General Public License v3.0 (GPLv3)** para cumplir estrictamente con las obligaciones copyleft de CalculiX.

Este proyecto está destinado a su publicación en **GitHub** y **Google Play Store**, con **monetización mediante anuncios y compras internas (In-App Purchases)** para funcionalidades avanzadas.

---

### ⚠️ Declaración Formal de Cumplimiento con la Licencia GPL de CalculiX 2.23

Se declara explícitamente que:

1. **CalculiX Version 2.23 está distribuido bajo GNU General Public License (GPL)**.
2. El ejecutable y librerías nativas fueron compilados el **Sun Oct 19 18:23:34 CEST 2025**.
3. **Copyright:** Guido Dhondt, 1998-2025.
4. Todo el proyecto conjunto se licencia bajo **GNU GPL v3.0**.
5. **Se publican versiones y APKs en GitHub y Google Play** (con monetización por anuncios y compras internas).
6. **Se cumplen todas las obligaciones de distribución GPL**:
   - ✅ Incluir el **texto completo de la licencia GPL v3.0**.
   - ✅ Incluir los **avisos de copyright de CalculiX 2.23**.
   - ✅ **Proporcionar el código fuente completo** del proyecto conjunto a cualquier usuario.
   - ✅ Permitir que los usuarios **modifiquen, estudien y redistribuyan** el proyecto bajo GPL.
   - ✅ No aplicar restricciones adicionales que violen las libertades de la GPL.

*Fundamento legal GPL:*
- La GPL **permite el uso comercial, venta y monetización**.
- La GPL **permite la redistribución** de binarios y paquetes (.apk, .aab) siempre que el código fuente esté disponible.
- La GPL **no prohíbe anuncios ni compras integradas**, exigiendo la disponibilidad del código fuente y la preservación de las 4 libertades del software libre.

**Fuente oficial de CalculiX:** [https://www.calculix.de](https://www.calculix.de)

---

## 📄 Licencias de los Componentes y Dependencias de Ingeniería

El proyecto integra las siguientes librerías científicas de código abierto:

| Componente / Librería | Versión | Propósito en la Aplicación | Licencia | Página Oficial |
| :--- | :--- | :--- | :--- | :--- |
| **CalculiX (CCX)** | **2.23** | Motor físico y solucionador FEA estructural/volumétrico | **GNU GPL** | [https://www.calculix.de](https://www.calculix.de) |
| **CalculiX CCX Manual** | 2.21 | Manual de referencia del solver CCX | **GPL v2.0** | [http://www.dhondt.de/ccx_2.21.pdf](http://www.dhondt.de/ccx_2.21.pdf) |
| **CalculiX CGX Manual** | 2.19 | Manual de pre/post-procesamiento CGX | **GPL v2.0** | [http://www.dhondt.de/cgx_2.19.pdf](http://www.dhondt.de/cgx_2.19.pdf) |
| **OpenCASCADE (OCCT)** | **8.0.0.p1** | Núcleo CAD 3D, operaciones BRep, STEP/IGES y DRAWEXE | **GNU LGPL v2.1** | [https://www.opencascade.com](https://www.opencascade.com) |
| **Gmsh** | **5.0.0** | Generador de mallas 3D (C3D10, C3D8, C3D4, etc.) | **GNU GPL v2.0+** | [https://gmsh.info](https://gmsh.info) |
| **OpenSees (OpenSeesPy)** | **3.5+** | Validador independiente de análisis estructural | **BSD-like** | [https://openseespydoc.readthedocs.io](https://openseespydoc.readthedocs.io) |
| **SPOOLES** | **2.2 MT** | Solución matricial dispersa multihilo (Multi-Threaded) | **Dominio Público** | [https://netlib.org/linalg/spooles/spooles.2.2.html](https://netlib.org/linalg/spooles/spooles.2.2.html) |
| **ARPACK** | - | Solucionador de autovalores y frecuencias modales | **Permisiva (BSD-like)** | [https://www.netlib.org/arpack/](https://www.netlib.org/arpack/) |
| **OpenBLAS / LAPACK** | - | Álgebra lineal optimizada de alto rendimiento | **BSD 3-Clause** | [https://www.openblas.net](https://www.openblas.net) |
| **BLAS (Netlib)** | - | Álgebra lineal básica | **Reference BLAS** | [https://www.netlib.org/blas/](https://www.netlib.org/blas/) |
| **Tcl / Tk** | **8.6** | Intérprete y entorno de scripting para DRAWEXE | **BSD-like** | [https://www.tcl.tk](https://www.tcl.tk) |
| **FreeType / FreeImage** | - | Renderizado de fuentes y texturas CAD | **FTL / GPL / FIPL** | [https://freetype.org](https://freetype.org) |
| **oneTBB / Draco / RapidJSON** | - | Paralelismo, compresión geométrica y serialización JSON | **Apache 2.0 / MIT** | [https://github.com](https://github.com) |
| **SceneView / Filament** | **4.18.0** | Visor 3D PBR/Unlit con control táctil interactivo | **Apache 2.0** | [https://github.com/SceneView/sceneview-android](https://github.com/SceneView/sceneview-android) |
| **iText 7 Core** | **7.2.5** | Generación de memorias de cálculo en PDF | **AGPL v3.0 / Commercial** | [https://itextpdf.com](https://itextpdf.com) |
| **GCC Fortran Runtime** | - | Runtime de Fortran (`libgfortran5`) | **GNU GPL v3.0+** | [https://gcc.gnu.org](https://gcc.gnu.org) |
| **Código Android** | **v0.2.0** | Aplicación Android (Java, Kotlin, C++ JNI) | **GNU GPL v3.0** | [https://www.gnu.org/licenses/gpl-3.0.en.html](https://www.gnu.org/licenses/gpl-3.0.en.html) |

---

## 🔒 Términos de Licencia GPL v3.0

Todo el proyecto (código de la aplicación + CalculiX 2.23) se distribuye bajo la licencia **GNU General Public License v3.0**:

> **CalculiX comes with ABSOLUTELY NO WARRANTY. This is free software, and you are welcome to redistribute it under certain conditions, see gpl.htm**
>
> **GNU GENERAL PUBLIC LICENSE**  
> Version 3, 29 June 2007  
>
> **Libertades Fundamentales:**
> - ✅ Libertad de **ejecutar el programa** para cualquier propósito (académico, comercial o profesional).
> - ✅ Libertad de **estudiar y modificar** el código fuente.
> - ✅ Libertad de **redistribuir copias** (incluyendo paquetes APK y App Bundles).
> - ✅ Libertad de **distribuir versiones modificadas** bajo la misma licencia copyleft.
>
> **Obligaciones de Distribución:**
> - ⚠️ Suministrar el **código fuente completo** a los receptores del software.
> - ⚠️ Incluir el **texto íntegro de la licencia GPL v3.0** y los **avisos de copyright**.
> - ⚠️ Permitir a los usuarios **modificar y redistribuir** bajo la GPL.
> - ⚠️ No imponer **restricciones adicionales** que limiten las libertades de la GPL.

---

## 📌 Aviso de Copyright de CalculiX 2.23

```
Copyright © Guido Dhondt, 1998-2025.
All rights reserved.

CalculiX Version 2.23 - Three-Dimensional Structural Finite Element Program
Developed by Guido Dhondt (MTU Munich)
Website: https://www.calculix.de
Executable made on: Sun Oct 19 18:23:34 CEST 2025

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; see gpl.htm.
```

---

## 📌 Aclaraciones Técnicas y de Licencia

- **Todo el proyecto (código Android + CalculiX 2.23)** se licencia bajo **GNU GPL v3.0** para satisfacer las condiciones copyleft de CalculiX.
- **Se utiliza el código fuente oficial de CalculiX 2.23** ([calculix.de](https://www.calculix.de)).
- **Se incluye el parche oficial de SPOOLES 2.2 MT de Netlib** con soporte multihilo OpenMP.
- **Se incluye el parche de Android para OpenCASCADE (`DRAWEXE`)** evitando bloqueos de `fdsan` en Android 11+ mediante `libfdsan_bypass.so`.

---

## 🌟 Módulos y Capacidades del Sistema

### 1. 🏗️ Módulo de Cálculo Estructural (Pórticos, Vigas, Cerchas, Losas y Muros)
- **Motor de Cálculo Real Integrado:** **CalculiX (`ccx 2.23`)** resolviendo formulaciones de vigas elásticas Timoshenko (`B31`, `B32`), losas cáscara (`S4R`) y muros de cortante en tensión plana (`CPS4`).
- **Lienzo Gráfico 2D Interactivo (`GridEditorView`):**
  - Trazado de nudos, barras y paneles con ajuste a cuadrícula (*Snap-to-Grid* a $0.5\text{ m}$).
  - Asignación rápida de apoyos: Empotrado (*Fixed*), Articulado (*Pinned*), Rodillo móvil (*Roller*) y Libre (*Free*).
  - Cargas puntuales nodales ($F_x, F_y, F_z$), momentos y cargas distribuidas ($w$).
- **12 Plantillas Predefinidas de Ingeniería:**
  1. *Pórtico Simple 2D (4x3m con carga lateral 10 kN).*
  2. *Pórtico Industrial de 2 Crujías (8x3m).*
  3. *Viga Continua Bi-tramo con apoyos múltiples.*
  4. *Cercha a Dos Aguas (Pitched Roof Truss con tirante L100x10).*
  5. *Viga con Voladizo.*
  6. *Edificio de 3 Pisos x 2 Crujías (Patrón sísmico triangular).*
  7. *Puente de Celosía Warren 12m (15 barras).*
  8. *Viga Continua de Concreto 25 MPa (4m + 3m + 2m).*
  9. *Cercha Pratt de 10m de luz.*
  10. *Ménsula en Voladizo.*
  11. *Losa Bidireccional de Concreto (S4R Shell 4x4m).*
  12. *Muro de Cortante de Concreto Armado (CPS4 Plane Stress).*
- **Diagramas de Acciones Internas y Deformadas:**
  - Deformada elástica 3D amplificable ($1\times$ a $1000\times$).
  - Diagrama de Momento Flector ($M_{33}$) con relleno cromático y detección exacta de puntos de inflexión (*Zero-Crossing*).
  - Diagrama de Fuerza Cortante ($V_{22}$) y Diagrama de Fuerza Axial ($N$).
  - Comprobación de derivas sísmicas de entrepiso según normativas internacionales (**NSR-10** $\le 1.0\%$ y **ASCE 7-22** $\le 1.5\%$).
- **Validador Independiente:** Contrastación cruzada y certificación mediante **OpenSees (`openseespy`)** en Python 3.11.

---

### 2. 🧊 Módulo de Sólidos 3D y Modelado CAD (OpenCASCADE & Gmsh)
- **Modelador Paramétrico CAD (OCCT 8.0.0.p1):**
  - Primitivas volumétricas continuas: Bloque (`Box`), Cilindro (`Cylinder`), Esfera (`Sphere`).
  - Operaciones de modificación: Redondeos de aristas (`Fillet`), Achaflanado (`Chamfer`), Extrusión (`Extrude`).
  - Operaciones Booleanas BRep: Unión (`BRepAlgoAPI_Fuse`), Corte / Perforación (`BRepAlgoAPI_Cut`) e Intersección (`BRepAlgoAPI_Common`).
- **Importador CAD Universal:**
  - Compatibilidad completa con **STEP (`.step`, `.stp`)**, **IGES (`.iges`, `.igs`)**, **BREP (`.brep`)**, **STL (`.stl`)** y scripts de Gmsh **(`.geo`)**.
- **Generador de Mallas Tetraédricas Cuadráticas (Gmsh 5.0.0):**
  - Elementos de alto orden **`C3D10` (Tetraedro Cuadrático de 10 nodos)** sin bloqueo por cortante (*shear locking*).
  - Elementos estructurados `C3D8`/`C3D20` (Hexaedros) y `C3D6`/`C3D15` (Prismas).
- **Condiciones de Borde y Cargas en Superficies:**
  - Empotramientos en caras base ($X^-, X^+, Y^-, Y^+, Z^-, Z^+$).
  - Cargas distribuidas nodales equivalentes.
- **Resolución FEA y Renderizado SceneView:**
  - Cálculo de tensiones de Von Mises con CalculiX `ccx`.
  - Conversión a GLB unlit (`KHR_materials_unlit`) mediante `frd_converter` nativo C++.

---

### 3. 🖥️ Módulo Terminal Integrada para Ingeniería
- **Consola NDK Interactiva:**
  - Ejecución directa de binarios nativos: `DRAWEXE` (OpenCASCADE), `gmsh` (Mallador 3D) y `ccx` (CalculiX 2.23 multihilo).
  - Comandos integrados de diagnóstico: `test-gmsh`, `test-draw`, `test-occt`, `ls`, `pwd`, `cat`.
  - Historial de comandos persistente y copia de registros al portapapeles con un toque.

---

### 4. 📄 Memorias de Cálculo en PDF y Visor 3D
- **Exportación de Reportes PDF:**
  - Memorias de cálculo completas generadas con iText 7 y Android PdfDocument.
  - Incluyen datos del proyecto, nudos, barras, materiales, propiedades de sección, reacciones basales, envolventes de solicitaciones internas, derivas de entrepiso y certificación de equilibrio estático.
  - Almacenamiento directo en la carpeta pública `Download/` compatible con Scoped Storage (Android 11 a 16+).

---

## 🛠️ Especificaciones Técnicas del Proyecto

| Parámetro | Configuración / Versión |
| :--- | :--- |
| **Nombre de la Aplicación** | Structural Analysis FEA Advanced |
| **ID de Paquete (Package Name)** | `com.diamon.civil` |
| **Versión de la Aplicación** | `v0.2.0` (VersionCode: `2`) |
| **SDK de Compilación (Compile SDK)** | **API 37** (Android 15 / 16 Preview) |
| **SDK Objetivo (Target SDK)** | **API 37** |
| **SDK Mínimo (Min SDK)** | **API 24** (Android 7.0 Nougat o superior) |
| **Arquitectura de Procesador (ABI)** | **`arm64-v8a`** (ARM 64-bit exclusivo) |
| **Versión del NDK** | `30.0.14904198` (Android NDK r30) |
| **Versión de CMake** | `4.1.2` |
| **Versión de Gradle** | `9.6.0` (con Android Gradle Plugin `9.2.1`) |
| **Compatibilidad Java / Kotlin** | **Java 11** (`JavaVersion.VERSION_11`), Kotlin `2.2.10`, Compose BOM `2025.05.00` |
| **Solucionador FEA Principal** | **CalculiX CCX 2.23** (SPOOLES 2.2 MT + OpenMP) |
| **Modelador CAD** | **OpenCASCADE Technology (OCCT 8.0.0.p1)** |
| **Generador de Malla 3D** | **Gmsh 5.0.0** |
| **Validador Independiente** | **OpenSeesPy** (Python 3.11 en entorno aislado `~/opensees-env`) |
| **Motor de Renderizado 3D** | **SceneView 4.18.0** / Google Filament PBR &amp; Unlit |
| **Generador de Reportes PDF** | **iText 7 Core 7.2.5** / Android Graphics PdfDocument |

---

## 📂 Estructura Principal del Repositorio

```
Calculo_Estructural/
├── app/                                    # Módulo principal de la aplicación Android
│   ├── src/main/java/com/diamon/civil/
│   │   ├── core/                           # UI principal, exportadores y utilidades JNI
│   │   ├── solids/                         # Módulo 3D Sólidos (CAD OCCT, Gmsh, visor)
│   │   ├── structural/                     # Módulo de Cálculo Estructural (ccx, diagramas, PDF)
│   │   └── terminal/                       # Módulo Terminal NDK interactivo
│   ├── src/main/cpp/                       # Código fuente nativo C++ (JNI, frd_converter)
│   └── src/main/assets/data/data/.../files/# Assets de ingeniería (OpenCASCADE, Tcl/Tk, etc.)
├── sample_models/                          # Modelos de prueba (STEP, IGES, BREP, GEO, INP)
├── setup-sdk.sh                            # Script de instalación y configuración de Android SDK/NDK
├── preparar_calculix.sh                    # Script de configuración de dependencias de CalculiX
├── preparar_opensees.sh                    # Script de entorno virtual aislado OpenSees (Python 3.11)
├── validate_with_opensees.py               # Suite de validación estructural independiente con OpenSees
├── validate_all_presets_calculix_opensees.py # Validación comparativa completa de los 12 presets (CalculiX vs OpenSees)
├── simulate_structural_ui_and_physics.py   # Simulación local y comprobación física de presets con ccx
├── test_all_sample_models.py               # Batería de integración DRAWEXE + Gmsh + CalculiX
├── run_calculix_tests.sh                   # Benchmark de paralelismo multihilo de CalculiX
├── ANALISIS_VALIDACION_ESTRUCTURAL_OPENSEES_CALCULIX.md # Informe científico completo de validación y contrastación física
├── GUIA_VALIDACION_Y_PRUEBAS_LOCALES.md    # Protocolo unificado de validación y pruebas locales
├── MANUAL_USUARIO.md                       # Manual completo de usuario
├── RELEASE_NOTES.md                        # Historial detallado de cambios y versiones
├── GEMINI.md                               # Instrucciones de compilación y publicación
```

---

## 🚀 Guía de Compilación y Construcción

### 1. Configuración Automatizada del SDK de Android
El script `setup-sdk.sh` descarga y prepara el SDK, NDK r30, CMake y genera `local.properties`:
```bash
chmod +x setup-sdk.sh
./setup-sdk.sh
```

### 2. Ejecución de Pruebas Unitarias de Gradle
```bash
./gradlew testDebugUnitTest
```

### 3. Compilación de APK Debug
```bash
./gradlew assembleDebug
```
*Ubicación del APK:* `/tmp/calculoestructural_build/outputs/apk/debug/app-debug.apk`

### 4. Compilación de Producción (Release APK y App Bundle AAB)
```bash
# Compilar APK Release firmado
./gradlew assembleRelease

# Compilar App Bundle (.aab) para Google Play Store
./gradlew bundleRelease
```
*Ubicación de artefactos de producción:*
- **APK Release:** `/tmp/calculoestructural_build/outputs/apk/release/app-release.apk`
- **AAB Bundle:** `/tmp/calculoestructural_build/outputs/bundle/release/app-release.aab`

---

## 📚 Documentación Técnica Adicional

- **[ANALISIS_VALIDACION_ESTRUCTURAL_OPENSEES_CALCULIX.md](ANALISIS_VALIDACION_ESTRUCTURAL_OPENSEES_CALCULIX.md)**: Informe científico detallado con fundamentos físico-matemáticos (Euler-Bernoulli vs Timoshenko), por qué los resultados son correctos y comparativa de los 12 presets.
- **[GUIA_VALIDACION_Y_PRUEBAS_LOCALES.md](GUIA_VALIDACION_Y_PRUEBAS_LOCALES.md)**: Guía de pruebas locales con CalculiX real y verificación independiente con OpenSees (Módulos de Cálculo Estructural, Sólidos 3D y Terminal).
- **[MANUAL_USUARIO.md](MANUAL_USUARIO.md)**: Manual de uso completo y guía funcional paso a paso.
- **[RELEASE_NOTES.md](RELEASE_NOTES.md)**: Registro histórico de versiones y características añadidas.
- **[DOCUMENTACION_FDSAN_DRAWEXE.md](DOCUMENTACION_FDSAN_DRAWEXE.md)**: Documentación de compatibilidad con fdsan para DRAWEXE en Android 11+.
- **[DOCUMENTACION_RENOMBRADO_BINARIOS.md](DOCUMENTACION_RENOMBRADO_BINARIOS.md)**: Especificación de librerías dinámicas compartidas y nombres normalizados.

---

**Versión de CalculiX:** 2.23  
**Versión de OpenCASCADE:** 8.0.0.p1  
**Versión de Gmsh:** 5.0.0  
**Copyright de CalculiX:** Guido Dhondt, 1998-2025  

## 👤 Autor

**Daniel Diamon**  
Tinaquillo, Cojedes, Venezuela  
Desarrollador independiente