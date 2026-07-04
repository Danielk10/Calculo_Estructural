# Guía de Pruebas de Ingeniería (Gmsh + OpenCASCADE + CalculiX)

Este documento describe cómo realizar la "Prueba de Fuego" para verificar que el motor geométrico industrial (OpenCASCADE) y el mallador (Gmsh) están funcionando correctamente dentro de la aplicación.

## 1. Prueba de Operaciones Booleanas (Sustracción 3D)

Esta prueba verifica que Gmsh puede usar el "cerebro" de OpenCASCADE para realizar operaciones complejas que no son posibles con el motor básico.

### Pasos en la Aplicación:

1.  **Abrir el Terminal:** Dirígete a la pestaña de terminal/consola en la app.
2.  **Generar el Script Geométrico:**
    *   Escribe el comando: `test-gmsh`
    *   **Resultado esperado:** La app dirá `Script 'prueba_booleana.geo' created.`
    *   *¿Qué hace esto?* Crea un archivo que define un cilindro de 5 unidades de alto y le resta una esfera en el centro para crear un hueco perfecto.

3.  **Ejecutar el Mallado 3D:**
    *   Escribe el comando: `gmsh prueba_booleana.geo -3 -format inp -o cilindro_hueco.inp`
    *   **Resultado esperado:** 
        *   La consola mostrará log de `Meshing 3D...`.
        *   Al final dirá `Exit Code: 0`.
        *   Se habrá generado el archivo `cilindro_hueco.inp` en el almacenamiento interno.

4.  **Verificar el Resultado:**
    *   Escribe `ls` para ver si el archivo `cilindro_hueco.inp` existe.

## 2. Prueba de Importación STEP (Capacidad Industrial)

Si tienes un archivo `.step` o `.stp` de cualquier software CAD (AutoCAD, SolidWorks, FreeCAD), puedes probar la importación industrial.

### Pasos:
1.  **Importar el archivo:** Usa el botón de "Importar" en la app para subir tu archivo `.step`.
2.  **Generar Malla desde Terminal:**
    *   Escribe: `gmsh nombre_de_tu_archivo.step -3 -format inp`
    *   **Resultado esperado:** Gmsh utilizará `libTKDESTEP.so` para leer la geometría profesional y convertirla en una malla de elementos finitos para CalculiX.

## 3. Ejecución de Cálculo Estructural (CalculiX)

Una vez generada la malla (.inp), puedes correr el análisis de tensiones.

### Pasos:
1.  **Correr CalculiX:**
    *   Escribe: `ccx cilindro_hueco` (sin el .inp) o simplemente selecciona el archivo si la interfaz lo permite.
    *   **Resultado esperado:** CalculiX procesará la matriz de rigidez de la geometría con el hueco esférico.

---
**Nota Técnica:** Todas las librerías (`libgmp`, `libfreetype`, `libTK*`, etc.) se cargan automáticamente gracias al sistema de enlaces simbólicos dinámicos implementado en `AssetHelper.java`.
