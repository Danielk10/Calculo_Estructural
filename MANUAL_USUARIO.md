# Manual de Usuario - Cálculo Estructural (FEA Suite)

Bienvenido a la suite de **Cálculo Estructural y Análisis de Elementos Finitos (FEA)** para Android. Esta aplicación está diseñada para ofrecer capacidades de ingeniería profesional (al estilo SAP2000, Abaqus y Salome) directamente desde un dispositivo móvil.

El sistema se divide en dos módulos principales: **Módulo Civil** (para estructuras reticulares y pórticos) y **Módulo de Sólidos** (para análisis 3D continuo, CAD y mallado).

---

## 1. Módulo Civil (Análisis de Estructuras y Pórticos)

Este módulo está optimizado para calcular vigas, pórticos y cerchas usando elementos tipo viga (B32) en CalculiX.

### 1.1 Ingreso de Datos (GridEditorView)
Ya no necesitas escribir coordenadas manualmente. En la pestaña de datos encontrarás la **Rejilla Interactiva (Grid Editor)**:
- **Crear un Nodo:** Simplemente toca cualquier punto de la rejilla. El nodo se ajustará automáticamente a las coordenadas de la cuadrícula.
- **Crear un Elemento (Viga):** Mantén presionado un nodo y arrastra el dedo hacia otro nodo para conectarlos.
- **Limpiar:** Si te equivocas, puedes usar el botón `CLEAR GRID` para reiniciar el lienzo.

### 1.2 Ejecución y Visualización
Una vez dibujada la estructura:
1. Selecciona el tipo de estructura en el menú desplegable (ej. Pórtico 2D, Cercha, etc.).
2. Presiona **"RUN SOLVER"**.
3. Cambia a la pestaña **Visor 3D**.
4. Usa los botones inferiores para alternar entre:
   - **Wireframe:** Malla original.
   - **Deformed:** Muestra la estructura con las deformaciones exageradas aplicadas.
   - **Diagrams:** Muestra los diagramas de Momentos Flectores y Fuerzas Cortantes a lo largo de las vigas.

### 1.3 Exportación de Reportes
- Presiona **"EXPORT PDF"** para generar un reporte profesional (Estilo SAP2000) que incluye un resumen de las fuerzas máximas (Axial, Cortante, Momento Flector) extraídas del archivo `.dat`. El PDF se guardará en tu carpeta de Descargas.

---

## 2. Módulo de Sólidos (Ingeniería Mecánica y CAD 3D)

Este módulo es un entorno completo de CAD y CAE que utiliza **OpenCASCADE (OCCT)** para la geometría, **Gmsh** para el mallado de elementos finitos y **CalculiX** para la resolución de esfuerzos (Tetraedros C3D4, C3D10 o Hexaedros).

### 2.1 Creación de Geometría CAD (OCCT)
En la pestaña de **Geometría**, puedes crear primitivas tridimensionales exactas:
- **Box (Caja):** Crea un cubo de dimensiones predefinidas.
- **Cylinder (Cilindro):** Crea un cilindro.
- **Sphere (Esfera):** Crea una esfera sólida.

> **⚠️ Importante sobre Operaciones Topológicas:** 
> - **Fillet / Chamfer:** Requieren aristas (edges). Si tu geometría activa es una esfera pura, fallarán porque no hay aristas para biselar o redondear. Aplícalas preferiblemente sobre Cajas o Cilindros recién creados.
> - **Extrude:** La extrusión matemática (`MakePrism`) requiere una cara 2D plana. Si intentas extruir un sólido cerrado, la operación será rechazada.

### 2.2 Asignación Dinámica de Cargas (Ray-Casting)
Una de las funciones más potentes es la asignación visual de Condiciones de Contorno:
1. Asegúrate de estar viendo tu modelo CAD o Malla en el visor 3D (Filament).
2. **Toca con el dedo** (Tap) sobre la cara o el nodo que deseas restringir.
3. Se abrirá un menú en la parte inferior (BottomSheet).
4. Selecciona **"Apply Fixed"** para aplicar un empotramiento perfecto (grados de libertad bloqueados).
5. Toca otra cara/nodo y selecciona **"Apply Load"**, ingresando el valor de la fuerza (ej. -1000 N).
6. Estos IDs dinámicos se mapearán automáticamente a NSETs (`*NSET`) en el archivo `.inp`.

### 2.3 Mallado y Simulación
- **Mesh Model:** Llama a Gmsh en segundo plano para convertir la geometría `.brep` a una malla de elementos finitos `.msh`.
- **Run Analysis:** Llama a CalculiX (`ccx`) en segundo plano para resolver la matriz de rigidez usando los parámetros fijados por Ray-Casting. Puedes monitorear el progreso en la pestaña **Terminal**.

### 2.4 Resultados y Reportes
- Una vez finalizado el análisis, los esfuerzos de Von Mises se mapean en colores sobre el modelo 3D (Rojo = Esfuerzo Máximo, Azul = Esfuerzo Mínimo).
- Presiona **"EXPORT PDF"** para generar un informe de Sólidos estilo Abaqus/Salome. Este reporte nativo en Android utilizará el generador para exportar las estadísticas de la malla y los esfuerzos críticos.

---

## 3. Pestaña de Terminal Avanzada (Consola de la App)

La aplicación cuenta con una **Terminal Avanzada** (Advanced Terminal) integrada que funciona como una consola tipo Unix dentro del directorio de trabajo de la app (sandbox). 

### 3.1 Comandos Básicos Disponibles
Puedes escribir comandos directamente en el input inferior:
- `ls`: Muestra los archivos y directorios (ej. tus modelos `.inp`, `.dat`, `.frd`).
- `pwd`: Muestra el directorio de trabajo actual.
- `cd <ruta>`: Navega por los directorios (ej. `cd ..` o `cd mi_carpeta`).
- `mkdir <nombre>`: Crea un nuevo directorio.
- `rm <archivo>` o `rm -rf <carpeta>`: Elimina archivos o carpetas.
- `clear`: Limpia la pantalla de la terminal.
- `help`: Muestra la lista de comandos disponibles.

### 3.2 Comandos de Simulación y Mallado
- `test-gmsh`: Este comando genera automáticamente un archivo script (`prueba_booleana.geo`) que modela un cilindro hueco (con una esfera restada en el centro usando operaciones booleanas de OpenCASCADE). Luego te indica cómo mallarlo.
- `gmsh <archivo.geo> [opciones]`: Llama directamente al motor de Gmsh compilado en Android. Por ejemplo: `gmsh prueba_booleana.geo -3 -format inp -o cilindro_hueco.inp`.
- `run-sim-test`: Ejecuta un pipeline completo de prueba (SimulationTestManager) interno de la aplicación.
- `<nombre_trabajo>` (ej. `job_solid`): Si escribes el nombre de un archivo de entrada `.inp` (sin la extensión), la terminal llamará directamente a **CalculiX** (ccx) para resolver ese archivo de manera manual.

### 3.3 Utilidades de la Terminal
- **Copiar al Portapapeles:** Tocar cualquier parte del texto de log copiará todo el contenido, útil para debugear, ver estadísticas de CalculiX o analizar matrices de rigidez.

---

## 4. Pruebas y Simulación (Testing)

Para garantizar la estabilidad numérica de la aplicación sin tener que modelar estructuras desde cero, el código incluye archivos de pruebas y simulación que puedes utilizar.

### 4.1 Uso de las Pruebas Unitarias/Locales
Si eres desarrollador, puedes ejecutar los casos de prueba (Test Cases) directamente desde tu IDE o terminal local:
1. Navega a `app/src/main/java/com/diamon/civil/test/simulation/`.
2. Las clases de test automatizan la ejecución del pipeline: **InpAssembler -> CalculixExecutor -> DatParser -> FrdParser**.
3. **Validación de Resultados:** Los parsers leen los archivos `.dat` (desplazamientos y fuerzas) y `.frd` (esfuerzos nodales 3D) verificando que los resultados numéricos de Von Mises y las Fuerzas Cortantes no tengan desviación de los esperados.

### 4.2 Simulando Casos Clásicos en la UI
En lugar de dibujar en el `GridEditorView`, la aplicación puede cargar configuraciones clásicas:
- Al seleccionar una estructura del menú desplegable (Spinner), la aplicación generará modelos matemáticos perfectos.
- **Voladizo 3D:** Un caso ideal para verificar la flexión pura en sólidos. Aplica una carga puntual en el extremo usando el **Ray-Casting** y observa cómo las fibras superiores se traccionan (Rojo) y las inferiores se comprimen (Azul) en los diagramas de esfuerzo de Filament.

---
**Nota:** *El entorno simula cálculos con rigor profesional, pero debido a las limitaciones de RAM de los dispositivos móviles, se recomienda no generar mallas excesivamente finas (más de 50,000 elementos) para evitar cierres inesperados por falta de memoria.*
