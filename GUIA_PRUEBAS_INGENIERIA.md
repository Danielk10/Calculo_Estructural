# Guía de Pruebas de Ingeniería (Gmsh + OpenCASCADE + CalculiX)

Este documento describe cómo realizar las pruebas de verificación para asegurar que el motor geométrico industrial (OpenCASCADE), el mallador (Gmsh) y el resolvedor (CalculiX) están operando correctamente de forma integrada dentro de la aplicación.

## 1. Prueba de Operaciones Booleanas y Mallado (Gmsh + OpenCASCADE)

Esta prueba verifica que Gmsh puede utilizar OpenCASCADE para realizar operaciones booleanas complejas en 3D (sustracción volumétrica) y generar la malla resultante.

### Pasos en la Aplicación:
1. **Abrir el Terminal:** Ve a la pestaña "Advanced Terminal" en la app.
2. **Ejecutar el Test de Mallado Booleano:**
   * Escribe el comando: `test-gmsh`
   * **Resultado esperado:** 
     * Se creará automáticamente el script `prueba_booleana.geo` (que define un cilindro con una esfera sustraída en el centro mediante OpenCASCADE).
     * Se ejecutará automáticamente el proceso de mallado en 3D.
     * La consola mostrará logs detallados del mallado y finalizará con `Exit Code: 0`.
3. **Verificar el Archivo Generado:**
   * Escribe el comando: `ls`
   * **Resultado esperado:** Deberá aparecer el archivo `cilindro_hueco.inp` en el listado.

## 2. Prueba del Modelador CAD (OpenCASCADE Headless DRAWEXE)

Esta prueba verifica la ejecución del intérprete de comandos de OpenCASCADE (`DRAWEXE`) en segundo plano para realizar operaciones geométricas directas.

### Pasos en la Aplicación:
1. **Abrir el Terminal:** Dirígete a la pestaña "Advanced Terminal".
2. **Ejecutar el Test de OCCT:**
   * Escribe el comando: `test-draw` (o `test-occt`)
   * **Resultado esperado:**
     * Se iniciará `DRAWEXE` en modo headless.
     * Se generará un cubo sólido 3D de 10x10x10 y se guardará como `test_box.brep`.
     * La terminal mostrará en el log el mensaje: `BOX CREATED SUCCESSFULLY` seguido de `Exit Code: 0`.
3. **Verificar el Archivo Generado:**
   * Escribe el comando: `ls`
   * **Resultado esperado:** El archivo `test_box.brep` estará presente en el directorio.

## 3. Prueba de Importación STEP (Capacidad Industrial)

Si dispones de un modelo geométrico profesional en formato `.step` o `.stp`, puedes probar su lectura utilizando el kernel industrial.

### Pasos:
1. **Importar Geometría:** Utiliza el botón de importación CAD o el comando de importación de la interfaz para cargar un archivo `.step` a la carpeta `3d_solid_analysis`.
2. **Generar Malla:**
   * Escribe en la terminal: `gmsh 3d_solid_analysis/tu_archivo.step -3 -format inp`
   * **Resultado esperado:** OpenCASCADE decodificará el archivo STEP (cargando las dependencias como `libTKDESTEP.so`) y Gmsh generará la malla de elementos finitos para CalculiX sin problemas.

## 4. Ejecución del Solver de Análisis de Tensiones (CalculiX)

Una vez obtenida una malla en formato `.inp` (como `cilindro_hueco.inp`), puedes correr el análisis mecánico estructural lineal de elementos finitos.

### Pasos:
1. **Correr CalculiX ccx:**
   * Escribe en la terminal: `ccx cilindro_hueco` (sin indicar la extensión `.inp`)
   * **Resultado esperado:** El resolvedor CalculiX procesará la malla y creará los archivos de resultados `.dat` y `.frd`.

## 5. Prueba de Automatización de Simulación Completa (E2E)

Esta prueba realiza la simulación completa de forma automática (Geometría $\rightarrow$ Malla $\rightarrow$ Solución CalculiX) mediante un caso de estudio integrado (Viga Cantilever).

### Pasos en la Aplicación:
1. **Abrir el Terminal:** Ve a la pestaña "Advanced Terminal".
2. **Ejecutar la Simulación Standalone:**
   * Escribe el comando: `run-sim-test`
   * **Resultado esperado:**
     * La terminal mostrará logs indicando `=== Iniciando Simulacion Standalone ===`.
     * Se generarán secuencialmente la geometría, la malla y se resolverá la viga mediante CalculiX.
     * Terminará con mensajes de éxito y la simulación se completará al 100%.

---
**Nota Técnica:** Todas las dependencias nativas (`libgmsh.so`, `libccx.so`, `libTK*`, etc.) están completamente vinculadas y accesibles dinámicamente gracias al gestor de inicialización del entorno en la aplicación.
