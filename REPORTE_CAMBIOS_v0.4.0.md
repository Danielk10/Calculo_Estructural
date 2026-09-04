# 📋 Reporte Técnico Exhaustivo de Cambios y Resolución de Fallas — Versión v0.4.0

**Proyecto:** Structural Analysis FEA 3D (`com.diamon.civil`)  
**Versión:** `v0.4.0` (Build 4)  
**Fecha:** Septiembre 2026  
**Autor:** Antigravity (Google DeepMind Pair Programming Assistant)

---

## 1. Diagnóstico y Resolución de Fallas en el Módulo de Sólidos 3D

### 🚨 Problema Reportado por el Usuario
> *"Tengo un problema con el módulo de sólidos 3D, cuando hago una corrida los cálculos son correctos, cuando hago otra en el mismo modelo con el mismo elemento fino los datos son incoherentes y luego vuelvo y son correctos y de nuevo son correctos, varía a veces en cada corrida pero otras no, al parecer son archivos temporales que quedan en cada corrida... no tocar el glb que se reemplaza automáticamente"*

Adicionalmente, se detectó el fallo al simular geometrías curvas (esferas y cilindros primitivos):
> `[ERROR] Pipeline Failure: Boundary condition assignment failed: fixed or loaded nodes could not be determined.`

---

### 🔍 Análisis de Causas Raíz (Root Causes)

#### Causa Raíz 1: Fuga de Estado en el Solver SPOOLES (`spooles.out`) por Apertura en Modo Append
- **Mecanismo del Fallo:**
  El solver lineal directo SPOOLES integrado en CalculiX CCX abre el archivo de registro `spooles.out` en modo adición (`fopen("spooles.out", "a")`).
- **Consecuencia:**
  Al ejecutar varias simulaciones consecutivas en el mismo directorio de trabajo, `spooles.out` no se truncaba ni se eliminaba. Si una corrida previa generaba matrices descompuestas o advertencias de pivoteo, el solver leía o acumulaba estados intermedios y logs de cálculos pasados.
- **Archivos residuales acumulados:**
  `spooles.out`, `spooles.log`, `intpoints.out`, `slavintmortar.out`, `temporaryrestartfile`, `sew_iges.tcl`, `*.geo_unrolled`, `*.opt`, `.gmsh-options`.
- **Solución implementada:**
  1. En [`CalculixExecutor.java`](file:///home/danielpdiamon/Calculo_Estructural/app/src/main/java/com/diamon/civil/structural/engine/CalculixExecutor.java), se agregó la eliminación proactiva de `spooles.out` y `spooles.log` antes de cada invocación a `ccx`.
  2. En [`SolidFragment.java`](file:///home/danielpdiamon/Calculo_Estructural/app/src/main/java/com/diamon/civil/solids/ui/fragments/SolidFragment.java), se reemplazó la lista negra frágil de limpieza por una política estricta de lista blanca que preserva exclusivamente:
     - Archivos de modelo CAD originales del usuario (`.step`, `.stp`, `.geo`, `.iges`, `.igs`, `.brep`, `.inp` importados).
     - Reportes PDF exportados (`.pdf`).
     - El archivo `.glb` actualmente visualizado en `SceneView` (cumpliendo estrictamente la restricción de **no tocar el glb activo**).
  3. Se diseñó el método `deleteFileThoroughly(File f)` con truncamiento a 0 bytes en caso de bloqueo en el sistema de archivos de Android.

---

#### Causa Raíz 2: Colinealidad de Nodos de Borde en Mallas Finas (`SolidInpAssembler`)
- **Mecanismo del Fallo:**
  En mallas finas generadas por Gmsh (densidades de malla 3 a 5, elementos cuadráticos C3D10 o C3D20), Gmsh numera secuencialmente las curvas 1D de las aristas del contorno geométrico antes de numerar los nodos interiores de la superficie 2D.
- **Consecuencia:**
  El método `areNodesKinematicallySufficient` tomaba los primeros 20 nodos consecutivos de un `TreeSet`. Al ser todos nodos de una sola arista recta:
  $$\vec{AB} \times \vec{AC} \approx \vec{0}$$
  El algoritmo determinaba erróneamente que los nodos no eran cinemáticamente suficientes (colineales), provocando que el bucle adaptativo de tolerancia aumentara hasta el 10% de la longitud total del modelo. Al ensanchar la tolerancia al 10%, capturaba capas completas de nodos interiores de la pieza, transformando el apoyo empotrado de superficie en un empotramiento de volumen que sobre-restringía drásticamente el modelo, produciendo deformaciones incoherentes o artificialmente reducidas.
- **Solución implementada:**
  En [`SolidInpAssembler.java`](file:///home/danielpdiamon/Calculo_Estructural/app/src/main/java/com/diamon/civil/solids/engine/SolidInpAssembler.java):
  1. Se implementó un muestreo estratificado (strided sampling) con tamaño de muestra representativo a lo largo de todo el conjunto de nodos.
  2. Se localiza primero el par $(A, B)$ con la **máxima distancia euclidiana**, maximizando la base del triángulo de apoyo.
  3. Se busca cualquier tercer punto $C$ que satisfaga:
     $$\sin(\theta) = \frac{\|\vec{AB} \times \vec{AC}\|}{\|\vec{AB}\| \cdot \|\vec{AC}\|} > 0.01$$
  4. La tolerancia adaptativa ahora se detiene inmediatamente en el factor más estrecho ($0.001$), restringiendo exactamente la cara exterior sin invadir el interior del sólido.

---

#### Causa Raíz 3: Extracción en Geometrías Curvas y Mallas Unidimensionales
- **Mecanismo del Fallo:**
  En geometrías como `sphere.brep`, el punto extremo en $Z_{min}$ o $Z_{max}$ es un único vértice ápice. Con tolerancias muy ajustadas, solo se capturaba 1 nodo, provocando que no se alcanzaran los 3 nodos requeridos. En el fallback previo, una expresión errónea `Math.min(Math.max(4, sorted.size()), sorted.size())` evaluaba a `sorted.size()`, capturando todos los nodos de la malla como apoyos fijos, lo que dejaba el conjunto de nodos cargados vacío tras `loadedNodes.removeAll(fixedNodes)`.
- **Solución implementada:**
  1. Corrección del cálculo de nodos de soporte curvilíneo: `Math.min(4, Math.max(1, sorted.size() / 2))`.
  2. Salvaguarda en [`SolidInpAssembler.java`](file:///home/danielpdiamon/Calculo_Estructural/app/src/main/java/com/diamon/civil/solids/engine/SolidInpAssembler.java): si por coincidencia extrema los apoyos fijos cubren todos los nodos disponibles, se libera automáticamente el nodo más alejado para la aplicación de la carga.

---

## 2. Nuevo Editor de Texto de Consola `featext` en la Terminal

Se creó una suite completa de edición de scripts y código de ingeniería para la consola técnica de la app:

### Componentes Arquitectónicos de `featext`
1. [`FeaTextTokenType.java`](file:///home/danielpdiamon/Calculo_Estructural/app/src/main/java/com/diamon/civil/terminal/editor/FeaTextTokenType.java): Definición de tipos de token léxicos (`KEYWORD`, `PARAMETER`, `CAD_COMMAND`, `COMMENT`, `STRING`, `VARIABLE`, `NUMBER`, `TEXT`).
2. [`FeaTextToken.java`](file:///home/danielpdiamon/Calculo_Estructural/app/src/main/java/com/diamon/civil/terminal/editor/FeaTextToken.java): Representación inmutable de rangos y tipos de token.
3. [`FeaTextTokenizer.java`](file:///home/danielpdiamon/Calculo_Estructural/app/src/main/java/com/diamon/civil/terminal/editor/FeaTextTokenizer.java): Motor de análisis léxico y detección automática de sintaxis:
   - **CalculiX / Abaqus (`.inp`):** Palabras clave (`*NODE`, `*ELEMENT`, `*STEP`), parámetros (`TYPE=`, `NSET=`), comentarios (`**`), números.
   - **OpenCASCADE DRAWEXE & Tcl (`.tcl`):** Comandos CAD (`box`, `cylinder`, `bcut`, `writebrep`, `pload`), palabras clave de control (`proc`, `set`, `if`), variables (`$var`), cadenas y comentarios (`#`).
   - **Gmsh CAD Scripts (`.geo`):** Primitivas geométricas (`Point`, `Line`, `Volume`, `SetFactory`), opciones métricas y comentarios (`//`, `/* */`).
4. [`FeaTextDocument.java`](file:///home/danielpdiamon/Calculo_Estructural/app/src/main/java/com/diamon/civil/terminal/editor/FeaTextDocument.java): Modelo de documento puro Java (independiente de Android SDK para pruebas unitarias), gestionando carga y guardado en UTF-8, conteo de líneas, cálculo de posición fila/columna (`Ln X, Col Y`), inserciones, borrado de caracteres e indentación.
5. [`FeaTextSyntaxHighlighter.java`](file:///home/danielpdiamon/Calculo_Estructural/app/src/main/java/com/diamon/civil/terminal/editor/FeaTextSyntaxHighlighter.java): Aplicador reactivo de spans (`ForegroundColorSpan`, `StyleSpan(Typeface.BOLD)`) calibrado para la paleta de alto contraste sobre fondo negro `#0A0E17`.

---

## 3. Rediseño de la Barra de Herramientas de la Terminal

- **Desplazamiento Horizontal (`HorizontalScrollView`):**
  Siguiendo el estilo de diseño del editor 2D de pórticos de `fragment_structural.xml`, la barra de herramientas ahora soporta scroll horizontal sin barras visibles (`scrollbars="none"`, `overScrollMode="never"`).
- **Dimensiones y Proporciones Uniformes:**
  Todos los botones de acción tienen exactamente el mismo tamaño: `38dp` x `38dp`, padding de `7dp`, fondo `?attr/selectableItemBackgroundBorderless` y tintado `@color/terminal_green`.
- **Distribución Coherente de Acciones:**
  1. `btnEditor`: Abrir/Alternar editor `featext`.
  2. `btnSave`: Guardar archivo en `featext` / Exportar PDF en la terminal.
  3. `btnCopyLog`: Copiar transcript o bloque de código seleccionado.
  4. `btnPaste`: Pegar texto del portapapeles en el cursor.
  5. `btnDelete`: Borrar carácter anterior (Backspace).
  6. `btnTab`: Insertar indentación de 4 espacios.
  7. `btnArrowLeft`: Mover cursor a la izquierda.
  8. `btnArrowRight`: Mover cursor a la derecha.
  9. `btnHistoryPrev`: Flecha Arriba (Historial anterior / Línea arriba en editor).
  10. `btnHistoryNext`: Flecha Abajo (Historial siguiente / Línea abajo en editor).
  11. `btnAbort`: Detener cálculo o solver en ejecución.
  12. `btnCloseEditor`: Cerrar editor `featext` y regresar a la consola.

- **Ajuste de Línea Visual (Word Wrap):**
  Configuración estricta en el `EditText` del editor con `scrollHorizontally="false"` y `inputType="textMultiLine|textNoSuggestions"`: ninguna línea de código se corta horizontalmente fuera de la pantalla; al llegar al borde derecho, continúa fluidamente en el renglón inferior.

---

## 4. Tabla de Pruebas y Certificación de Calidad

| Suite de Pruebas | Pruebas Ejecutadas | Resultado | Detalle |
|---|:---:|:---:|---|
| `FeaTextTokenizerTest` | 4 | ✅ APROBADO | Verificación de sintaxis para `.inp`, `.tcl`, `.geo` y detección de formato. |
| `FeaTextDocumentTest` | 3 | ✅ APROBADO | Validación de carga, guardado UTF-8, métricas de línea/columna y edición. |
| `SolidInpAssemblerTest` | 6 | ✅ APROBADO | Validación de 5 corridas consecutivas en malla fina, soporte de esferas y voladizo. |
| **Suite Completa del Proyecto** | **127** | **✅ 100% APROBADO** | Cero regresiones en todos los módulos (`app:testDebugUnitTest`). |
