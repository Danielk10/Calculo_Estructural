# Resumen del Proyecto Structural Analysis FEA Advanced

Este documento resume la estructura y configuración actual del proyecto Android con soporte nativo C++ **Structural Analysis FEA Advanced**.

## Ficha Técnica del Proyecto

| Parámetro | Valor |
| :--- | :--- |
| **Nombre del Proyecto** | Structural Analysis FEA Advanced |
| **Package Name / Application ID** | `com.diamon.civil` |
| **Núcleo de Cálculo** | CalculiX 2.23 (compilado como `libccx.so`) |
| **Núcleo Geométrico** | OpenCASCADE 8.0.0 (27 librerías `libTK*.so`) |
| **Motor de Mallado** | Gmsh 5.0.0 (`libgmsh.so`) |
| **Compile SDK** | 37 (Android 15+) |
| **Alineación 16KB** | Verificada (Soporte Android 15+) |

## Componentes Principales del Proyecto

- **[app/src/main/java/com.diamon.civil/ui/MainActivity.java]**: Actividad principal con terminal integrado.
- **[app/src/main/java/com.diamon.civil/util/AssetHelper.java]**: Motor dinámico de despliegue de binarios y enlaces simbólicos.
- **[jniLibs/arm64-v8a/]**: Binarios nativos alineados a 16KB con RPATH removido.

## Configuración y Construcción Rápida

- **Script de Instalación SDK**: [setup-sdk.sh](file:///home/danielpdiamon/CalculoEstructural/setup-sdk.sh)
- **Guía de Uso del SDK**: [guia_uso_sdk.md](file:///home/danielpdiamon/CalculoEstructural/guia_uso_sdk.md)
- **Guía del Proyecto**: [guia_desarrollo_calculoestructural.md](file:///home/danielpdiamon/CalculoEstructural/guia_desarrollo_calculoestructural.md)

---
*Configurado y Adaptado para Structural Analysis FEA Advanced*
