# Informe Detallado: Estructura de Componentes Auxiliares

Este documento analiza los archivos y scripts auxiliares del repositorio, clasificándolos según los planes de implementación maestros (`IMPLEMENTATION_PLAN.md`, `plan_implementacion_fea.md`).

## 1. Fase de Compilación e Infraestructura Nativa (Core NDK)

Estos componentes son críticos para el **Fase 1: Shared NDK Core** del plan de implementación. Automatizan la construcción de dependencias complejas (C++/CMake/Android NDK).

- **`build_calculix_fixed.sh`**: Script para compilar el solver CalculiX (`ccx`). Crucial para la Fase 1.1 (CalculixRunner).
- **`build_gmsh.sh`**: Compila Gmsh. Esencial para la Fase 1.2 (Pipeline CAD).
- **`build_opencascade.sh`**: Compila OpenCASCADE para operaciones geométricas. Requerido para la **Fase 2** (CAD Primitives, Boolean Engine).
- **`build_spooles_spooles.sh`**: Compila librerías SPOOLES necesarias para el álgebra lineal en CalculiX.
- **`cbuild_occt_android.sh`**: Script de compilación cruzada especializado para integrar OCCT en Android. Fase 2.
- **`setup-sdk.sh`**: Configura el entorno necesario para que los scripts anteriores funcionen (Fase de Preparación).
- **`omp_lib.h`**: Cabecera de OpenMP para paralelización (Fase 4: Performance).

## 2. Fase de Pruebas y Validación (QA)

Estos archivos soportan la **Estrategia de Testing & Validación** descrita en el plan maestro.

- **`tests/`**: Código fuente C++ para testing unitario.
  - `test_analysis_model.cpp`: Verifica la integridad de la estructura de datos `AnalysisModel` (Fase 1.1).
  - `test_calculix_runner.cpp`: Verifica la ejecución del solver nativo.
  - `test_project_store.cpp`: Verifica la persistencia de datos del modelo.
- **`validation/`**: Contiene escenarios de prueba pre-construidos (.frd, .inp) para validación rápida en dispositivo o emulador.
- **`test_*` (binarios en raíz)**: Binarios ejecutables de las pruebas de `tests/` para verificar la lógica nativa localmente en Linux.

## 3. Fase de Prototipado (I+D)

- **`converter_prototype/`**: Contiene la implementación original en C++ del convertidor FRD a GLB (Fase 1.1). Es la base del código que actualmente corre dentro de la aplicación vía JNI.

## 4. Documentación y Gestión de Proyecto

Estos archivos mantienen el control sobre las fases descritas en `IMPLEMENTATION_PLAN.md` y `plan_implementacion_fea.md`.

- **Planes**: `IMPLEMENTATION_PLAN.md` (Visión global), `plan_implementacion_fea.md` (Estado detallado).
- **Auditoría**: `REPORTE_AUDITORIA.md`, `CALCULIX_BUILD_REPORT.md`, `INFORME_COMPILACION_CALCULIX.md` (Seguimiento de Fase 1).
- **Guías**: `FIREBASE_TESTING_PROCEDURE.md`, `DEVELOPER_GUIDE.md`, `guia_uso_sdk.md`.
