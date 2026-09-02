# Notas de Versión - Structural Analysis FEA 3D

## 📦 Structural Analysis FEA 3D v0.4.0 (Pre-Release)

### 🚀 Determinismo Absoluto, Aislamiento de Ejecución y Suite Completa de Elementos Finitos 3D

#### 1. Aislamiento Total del Espacio de Trabajo y Eliminación de Archivos Residuales
- **Limpieza Previa Obligatoria:**
  - Al presionar *Calcular* o importar archivos INP/CAD en el módulo de sólidos 3D, se eliminan automáticamente todos los archivos intermedios y de resultados de corridas anteriores (`job_solid_raw.inp`, `job_solid.inp`, `job_solid_clean.inp`, `nsets.inp`, `job_solid.frd`, `job_solid.dat`, `job_solid.sta`, `job_solid.cvg`, `job_solid.12d`, `job_solid.glb`).
  - Previene que ejecuciones canceladas, con errores de geometría o cambios de parámetros reutilicen por accidente archivos `.frd` o mallas cacheadas de corridas previas.
- **Limpieza en Gmsh:**
  - `GmshRunner` elimina el archivo de salida `.inp` antes de invocar el binario nativo de Gmsh, garantizando un resultado limpio desde cero.

#### 2. Detección Espacial de Apoyos y Cargas con Tolerancia Numérica Estricta
- **Eliminación de Alias Ambiguos (`SURFACE1`/`SURFACE2`):**
  - Se eliminó el mapeo ciego de superficies genéricas OpenCASCADE en `SolidInpAssembler`, evitando que en piezas CAD importadas se confunda la cara inferior ($Y=0$) con el empotramiento en voladizo ($X=0$).
- **Tolerancia Planar Submilimétrica:**
  - Se ajustó la captura de nodos en caras de contorno a una tolerancia planar estricta de $0.001\text{ mm}$ ($0.1\%$). Garantiza que únicamente se seleccionen los nodos superficiales del plano de apoyo y ningún nodo interno de volumen quede atrapado erróneamente en el conjunto `NFix`.

#### 3. Soporte Completo y Riguroso de las 8 Familias de Elementos Finitos Sólidos 3D
- **C3D4 (Tetraedro Lineal, 4 nodos):** Formulación de 1er orden con recomendación de refinamiento de malla para mitigar rigidez por *shear locking*.
- **C3D8 (Hexaedro Lineal, integración completa 2×2×2):** Integración exacta a cortante para tracción/compresión y sólidos con múltiples capas en espesor.
- **C3D8R (Hexaedro Lineal, integración reducida 1 punto):** Formulación económica con control automático de *hourglassing*.
- **C3D6 (Cuña/Prisma Lineal, 6 nodos):** Formulación prismática estructurada por capas para transiciones geométricas.
- **C3D10 (Tetraedro Cuadrático, 10 nodos, 4 puntos de Gauss):** Elemento de propósito general recomendado para geometrías libres no estructuradas; exactitud analítica del 99.9%.
- **C3D20 (Hexaedro Cuadrático, integración completa 3×3×3):** Malla cuadrática serendipity de 20 nodos para gradientes tensionales complejos.
- **C3D20R (Hexaedro Cuadrático, integración reducida 2×2×2):** Elemento óptimo y balanceado recomendado por CalculiX con puntos superconvergentes.
- **C3D15 (Cuña/Prisma Cuadrático, 15 nodos, 9 puntos de Gauss):** Formulación cuadrática para mallas extruidas estructuradas.

#### 4. Verificación y Determinismo Numérico al 100%
- **Batería de 200 Simulaciones:**
  - Evaluadas 5 repeticiones idénticas en los 5 niveles de densidad de malla con $0.000000\%$ de variabilidad numérica (100% determinista).
- **Suite de Tests Unitarios:**
  - 26/26 tests pasando con éxito en `SolidInpAssemblerTest`, incluyendo validaciones automatizadas para cada una de las 8 familias de elementos finitos y modelos STEP reales.
