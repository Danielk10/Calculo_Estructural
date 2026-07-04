# Structural Analysis FEA Advanced

**Structural Analysis FEA Advanced** es una aplicación de cálculo estructural para Android, basada en el potente motor de simulación **CalculiX (ccx)** utilizando la interfaz de desarrollo nativo de Android (**NDK**) con integración **Java y C++ (JNI)**.

---

# README / DECLARACIÓN DE LICENCIA GPL v3.0

## 🏛️ Proyecto basado en CalculiX 2.23 - Declaración de Licencia y Cumplimiento GPL

Este repositorio contiene mi **código Android independiente** y el núcleo de **CalculiX Version 2.23** como motor de cálculo estructural (FEA). Estoy licenciando **todo el proyecto conjunto bajo GNU GPL v3.0** para cumplir con las obligaciones de la licencia de CalculiX.

Este proyecto está destinado a **lanzar en GitHub y Google Play**, con **monetización por anuncios y compras internas** para funciones avanzadas.

***

## ⚠️ Declaración de Cumplimiento con la Licencia GPL de CalculiX 2.23

Declaro explícitamente que:

1. **CalculiX Version 2.23 está distribuido bajo GNU General Public License (GPL)**.
2. El executable fue compilado el **Sun Oct 19 18:23:34 CEST 2025**.
3. **Copyright:** Guido Dhondt, 1998-2025.
4. Estoy licenciando **todo el proyecto conjunto bajo GPL v3.0** para cumplir con la obligación de copyleft.
5. **Voy a publicar APKs en GitHub y Google Play** (con monetización por anuncios y compras internas).
6. **Voy a cumplir con todas las obligaciones de GPL al distribuir**:
   - ✅ Incluir el **texto completo de la licencia GPL v3.0**.
   - ✅ Incluir **avisos de copyright de CalculiX 2.23**.
   - ✅ **Proporcionar el código fuente completo** del proyecto conjunto (mi código + CalculiX 2.23 modificado) a cualquier usuario.
   - ✅ Permitir que los usuarios **modifiquen y redistribuyan** el proyecto bajo GPL.
   - ✅ No usar restricciones adicionales que violen las libertades GPL.

Esto cumple con la GPL porque:
- La GPL **permite uso comercial, venta y monetización**.
- La GPL **permite redistribución** (incluyendo APKs) si se cumplen las obligaciones.
- La GPL **no prohíbe anuncios ni compras internas**, solo exige que el código fuente sea disponible y las libertades se mantengan.

**Fuente oficial (página web):** [https://www.calculix.de](https://www.calculix.de)

***

## 📄 Licencias de los Componentes y Dependencias

Este proyecto integra las siguientes librerías de código abierto. Todas tienen licencias **permisivas (BSD/MIT/Public Domain) o GPL-compatible**, excepto CalculiX que es GPL:

| Componente | Versión | Propósito | Licencia | Página Oficial |
|------------|---------|-----------|----------|----------------|
| **CalculiX (CCX)** | **2.23** | Núcleo de análisis estructural (FEA) | **GNU GPL** | [https://www.calculix.de](https://www.calculix.de) |
| **CalculiX CCX Manual** | 2.21 | Manual oficial del solver CCX | **GPL v2.0** | [http://www.dhondt.de/ccx_2.21.pdf](http://www.dhondt.de/ccx_2.21.pdf) |
| **CalculiX CGX Manual** | 2.19 | Manual completo de pre/post-processor CGX | **GPL v2.0** | [http://www.dhondt.de/cgx_2.19.pdf](http://www.dhondt.de/cgx_2.19.pdf) |
| **SPOOLES** | **2.2** | Solución de sistemas lineales dispersos | **Dominio Público** | [https://netlib.org/linalg/spooles/spooles.2.2.html](https://netlib.org/linalg/spooles/spooles.2.2.html) |
| **ARPACK** | - | Problemas de autovalores | **Permisiva** (BSD-like) | [https://www.netlib.org/arpack/](https://www.netlib.org/arpack/) |
| **OpenBLAS** | - | Álgebra lineal optimizada (BLAS) | **BSD 3-Clause** | [https://www.openblas.net](https://www.openblas.net) |
| **BLAS (Netlib)** | - | Álgebra lineal básica | **Reference BLAS** | [https://www.netlib.org/blas/](https://www.netlib.org/blas/) |
| **LAPACK** | - | Rutinas de álgebra lineal avanzadas | **BSD 3-Clause** | [https://www.lapack.org](https://www.lapack.org) |
| **GCC Fortran Runtime** | - | Runtime de Fortran (libgfortran) | **GPL v3.0 o posterior** | [https://gcc.gnu.org](https://gcc.gnu.org) |
| **Mi código Android** | - | Aplicación Android NDK (Java/C++) | **GNU GPL v3.0** | [https://www.gnu.org/licenses/gpl-3.0.en.html](https://www.gnu.org/licenses/gpl-3.0.en.html) |

***

## 🔒 Licencia GPL

Todo el proyecto (mi código + CalculiX 2.23) se licencia bajo **GNU General Public License**:

> **CalculiX comes with ABSOLUTELY NO WARRANTY. This is free software, and you are welcome to redistribute it under certain conditions, see gpl.htm**
>
> **GNU GENERAL PUBLIC LICENSE**  
> Version 3, 29 June 2007  
>
> **Key freedoms:**
> - ✅ You may **run the program** for any purpose.
> - ✅ You may **study and modify** the source code.
> - ✅ You may **redistribute** copies (including APKs).
> - ✅ You may **distribute modified versions** under the same license.
>
> **Obligations when distributing:**
> - ⚠️ Must provide **complete source code** to recipients.
> - ⚠️ Must include **this license text** and **copyright notices**.
> - ⚠️ Must allow recipients to **modify and redistribute** under GPL.
> - ⚠️ Cannot add **restrictions additional** to GPL.

***

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

***

## 📌 Aclaración de Licencia

- **Todo el proyecto (mi código Android + CalculiX 2.23)** se licencia bajo **GNU GPL v3.0** para cumplir con la obligación de copyleft de CalculiX.
- **Se usa el código oficial de la página web de CalculiX (versión 2.23)**, no el de GitHub.
- **Se usa el parche oficial de SPOOLES 2.2 de NetLib**.

***

## ✅ Lo que VOY a hacer

- ✅ **Publicar APKs en GitHub y Google Play**.
- ✅ **Incluir anuncio claro de "Contains ads"** en Google Play Console.
- ✅ **Incluir texto completo de GPL v3.0** en el repositorio.
- ✅ **Mantener avisos de copyright de CalculiX 2.23** en el código.
- ✅ **Proporcionar código fuente completo** en el repositorio GitHub público.
- ✅ **Permitir que usuarios modifiquen y redistribuyan** el código.
- ✅ **Incluir Privacy Policy** accesible en la app y en Google Play.

***

## ⚠️ Lo que NO haré

Para no violar GPL, **NO**:

- ❌ Mantendré el código cerrado bajo licencia propietaria (el conjunto debe ser GPL).
- ❌ Usaré DRM o verificación de licencia que prohíba la modificación.
- ❌ Prohibiré a usuarios redistribuir APKs modificados.
- ❌ Prohibiré el uso gratuito de la app.
- ❌ Agregaré restricciones adicionales a las de GPL.
- ❌ Ocultaré el código fuente (debe ser accesible a cualquier usuario).

***

## 🛠️ Especificaciones Técnicas y Versiones

*   **SDK de Compilación (Compile SDK):** API 37 (Android 15+)
*   **SDK Objetivo (Target SDK):** API 37
*   **SDK Mínimo (Min SDK):** API 23 (Android 6.0 Marshmallow+)
*   **Versión de NDK:** `30.0.14904198`
*   **Versión de CMake:** `4.1.2`
*   **Versión de Gradle:** `9.5.1` (con Android Gradle Plugin 9.2.1)
*   **Nombre de Paquete (Package Name):** `com.diamon.civil`
*   **Nombre de la Librería Nativa:** `libcalculix.so`

---

## 📂 Estructura Principal del Proyecto

*   **[app/](app)**: Código fuente de la aplicación Android.
*   **[fake_root/](fake_root)**: Directorio original simulado de almacenamiento interno.

---

## 🚀 Configuración y Construcción

Para configurar el entorno de compilación, sigue estos pasos:

### 1. Configurar el SDK de Android
```bash
chmod +x setup-sdk.sh
./setup-sdk.sh
```

### 2. Compilar
```bash
./gradlew assembleDebug
```

---

## 📚 Documentación Adicional
- **[guia_desarrollo_calculoestructural.md](guia_desarrollo_calculoestructural.md)**
- **[resumen_proyecto_calculoestructural.md](resumen_proyecto_calculoestructural.md)**
- **[guia_uso_sdk.md](guia_uso_sdk.md)**

---

**Versión de CalculiX usada:** 2.23
**Copyright de CalculiX:** Guido Dhondt, 1998-2025
**Executable compilado:** Sun Oct 19 18:23:34 CEST 2025


## Autor

**Daniel Diamon**
Tinaquillo, Cojedes, Venezuela
Desarrollador independiente