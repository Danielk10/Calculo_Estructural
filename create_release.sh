#!/bin/bash
set -e

echo "=== Iniciando Proceso de Prelanzamiento v0.4.0 ==="

# 1. Compilación del APK para asegurar que contenga los últimos cambios
echo "Paso 1: Compilando APK debug..."
./gradlew assembleDebug

# 2. Crear el pre-release en GitHub utilizando el CLI local 'gh'
echo "Paso 2: Creando el prerelease v0.4.0 en GitHub y subiendo el APK..."
gh release create v0.4.0 /tmp/calculoestructural_build/outputs/apk/debug/app-debug.apk \
  --title "v0.4.0-alpha" \
  --notes "Unificación de la arquitectura de ejecución de binarios en Java/Kotlin (ProcessBuilder) y remoción de CalculixRunner en C++ para cumplimiento con las políticas de SELinux (Android 10+)" \
  --prerelease

echo "=== Prelanzamiento v0.4.0 Creado Exitosamente ==="
