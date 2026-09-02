# 📘 Manual de Usuario — Structural Analysis FEA 3D

Bienvenido a la suite de **Structural Analysis FEA 3D** para Android (`com.diamon.civil`). Esta plataforma profesional permite modelar, simular y analizar estructuras reticulares, pórticos espaciales y componentes mecánicos tridimensionales directamente en dispositivos móviles mediante los motores industriales **CalculiX CCX 2.23**, **OpenCASCADE (OCCT 8.0.0.p1)** y **Gmsh**.

---

## 🏛️ 1. Módulo de Cálculo Estructural (Pórticos, Vigas, Cerchas y Losas)

El **Módulo de Cálculo Estructural** combina la agilidad de modelado 2D directo con la potencia de cálculo tridimensional de **CalculiX CCX** con aceleración multihilo OpenMP.

Permite analizar pórticos espaciales 3D, vigas continuas, cerchas, placas/losas y muros de corte sometidos a cargas puntuales en nudos y cargas distribuidas en barras/elementos.

---

## 🚀 Flujo Rápido de Uso (Paso a Paso)

1. **Seleccionar o Dibujar Estructura:**
   - **Plantillas Predefinidas:** Elige un modelo en el selector desplegable `📚 Ejemplos / Plantillas:` (*Pórtico 2D, Viga Continua, Cercha Warren, etc.*).
   - **Dibujo Libre:** O pulsa `🧹 Limpiar` y dibuja nudos y barras con `✏️ Dibujar` (con snap magnético a $0.5\text{ m}$).
2. **Asignar Condiciones de Apoyo:**
   - Pulsa `⚓ Apoyo` y toca cualquier nudo en la base para alternar su tipo:
     - 🟧 **Empotramiento (Fixed):** Restringe traslaciones y rotaciones ($U_x = U_y = U_z = 0$, $R_x = R_y = R_z = 0$).
     - 🔺 **Apoyo Fijo (Pinned):** Articulación con traslaciones fijas ($U_x = U_y = U_z = 0$).
     - ⚪ **Apoyo Móvil (Roller):** Restringe traslación vertical ($U_y = 0$).
3. **Asignar Cargas y Secciones:**
   - Pulsa `⚡ Cargas` y toca un nudo para asignar fuerzas puntuales $F_x, F_y\ (\text{en kN})$.
   - Toca una barra/viga para asignar carga distribuida uniforme $w\ (\text{en kN/m})$ o carga puntual en el vano $P\ (\text{en kN})$, además de su sección transversal y material.
4. **Ejecutar el Solver CalculiX:**
   - Pulsa el botón flotante **`⚡ RESOLVER`** en la esquina inferior derecha.
   - CalculiX CCX resolverá la matriz de rigidez global en milisegundos con Timoshenko beam (B31/B32).
5. **Explorar Resultados 3D:**
   - Cambia a la pestaña **\"VISTA 3D\"** para ver:
     - Deformada escalable con slider interactivo ($0.1\times$ a $5.0\times$).
     - Diagramas continuos con relleno sombreado: **Momento Flector ($M_z$)**, **Fuerza Cortante ($V_y$)** y **Fuerza Axial ($N$)**.
     - Visualización sólida 3D de perfiles estructurales extruidos (`HEB`, `IPE`, `W`, etc.).
6. **Exportar Memoria de Cálculo:**
   - Presiona **\"EXPORTAR PDF\"** para generar la memoria de cálculo en formato profesional.
- ⚡ **CARGAS (`LOAD`):** Toca un nudo para abrir directamente el editor de cargas puntuales ($F_x, F_y$). Las cargas se visualizan inmediatamente en la rejilla mediante flechas vectoriales de ingeniería con sus magnitudes.
- 🗑️ **ELIMINAR (`DELETE`):** Toca cualquier nudo, barra o panel para eliminarlo.
- ↩️ **DESHACER (`UNDO`) y LIMPIAR (`CLEAR`):** Permiten revertir los últimos 20 pasos de dibujo o reiniciar el modelo.

### 1.2 Plantillas Predefinidas de Ingeniería (12 Presets)
Puedes cargar con un solo toque estructuras completas desde el selector superior:
1. **Pórtico Simple 2D (Portal Frame):** $4\text{ m} \times 3\text{ m}$ con carga lateral de viento/sismo de $10\text{ kN}$.
2. **Pórtico de 2 Crujías (Two-Bay Frame):** $8\text{ m} \times 3\text{ m}$ con carga gravitacional central de $30\text{ kN}$.
3. **Viga Continua 2 Vanos (Continuous Beam):** Dos vanos de $3\text{ m}$ sobre apoyos fijo y móviles con carga de $20\text{ kN}$.
4. **Cercha a Dos Aguas (Pitched Roof Truss):** Nave de $6\text{ m}$ con cumbrera a $4.5\text{ m}$ y carga en la cúspide de $25\text{ kN}$.
5. **Viga con Voladizo (Overhanging Beam):** Viga continua con voladizo exterior cargado en la punta con $15\text{ kN}$.
6. **Edificio de 3 Pisos (3-Story Building):** Marco de 2 crujías y 3 niveles con distribución de fuerzas sísmicas por piso ($15\text{ kN}, 30\text{ kN}, 45\text{ kN}$).
7. **Puente Cercha Warren (Warren Truss Bridge):** Estructura de celosía de $12\text{ m}$ de luz con cargas rodantes de $20\text{ kN}$ en nodos de calzada.
8. **Viga Continua de Concreto (Concrete Continuous Beam):** Dos vanos y voladizo con perfiles rectangulares de $300 \times 400\text{ mm}$.
9. **Cercha Pratt (Pratt Truss):** Armadura de cordón paralelo de $10\text{ m}$ con diagonales traccionadas hacia el centro y carga de $50\text{ kN}$.
10. **Ménsula en Voladizo (Cantilever Bracket):** Estructura triangulada anclada a muro rígido con carga de $20\text{ kN}$.
11. **Losa / Placa 2D (Concrete Slab Plate):** Placa perimetralmente apoyada discretizada con elementos Shell `S4R` y carga de $40\text{ kN}$.
12. **Muro de Corte (Shear Wall):** Muro de concreto armado en tensión plana `CPS4` con esfuerzo cortante de $50\text{ kN}$.

### 1.3 Resolución y Visor 3D OpenGL ES 3.0
1. Presiona **"CALCULAR"** (`RUN SOLVER`). El motor compilará el archivo `.inp` y ejecutará CalculiX `ccx` en segundo plano.
2. En la pestaña **Visor 3D**, inspecciona:
   - **Malla Inicial (Wireframe):** Geometría neutra no deformada.
   - **Deformada:** Curva elástica tridimensional continua con slider de escala de amplificación ($1\times$ a $1000\times$).
   - **Diagrama de Momentos Flectores ($M_{33}$):** Representación continua con relleno cromático de áreas positivas (Cian) y negativas (Naranja), con división exacta en puntos de inflexión ($M=0$).
   - **Diagrama de Fuerzas Cortantes ($V_{22}$) y Esfuerzo Axial ($N$).**
   - **Símbolos de Apoyo y Flechas de Carga 3D:** Glifos de ingeniería (placas de anclaje, pirámides y rodillos).

### 1.4 Exportación de Reportes de Cálculo en PDF
- Presiona **"EXPORTAR PDF"** para generar la memoria de cálculo en formato profesional de ingeniería estructural.
- Incluye: Portada técnica, datos del proyecto, definición geométrica de nudos, conectividad de barras, cargas aplicadas, verificación de derivas sísmicas según normas internacionales (**NSR-10** máx 1.0% y **COVENIN 1756** máx 1.2%), esfuerzos máximos y deformaciones elásticas.
- Compatible con **Android 11, 12, 13, 14 y 15+** mediante *MediaStore API / Scoped Storage*, guardando directamente en la carpeta pública `Download/`.

---

## ⚙️ 2. Módulo de Sólidos 3D (CAD Mecánico y FEA Continuo)

Este módulo está dedicado al modelado tridimensional continuo de piezas y componentes mecánicos mediante **OpenCASCADE (OCCT 8.0.0.p1)**, mallado volumétrico con **Gmsh** y análisis de esfuerzos de Von Mises con **CalculiX CCX 2.23**.

### 2.1 Modelado CAD Paramétrico
- **Primitivas:** Creación de Bloques (`Box`), Cilindros (`Cylinder`) y Esferas (`Sphere`).
- **Operaciones de Modificación:** Redondeo de aristas (`Fillet`), Achaflanado (`Chamfer`) y Extrusión (`Extrude`).
- **Operaciones Booleanas:** Unión, Corte (Sustracción) e Intersección entre sólidos.
- **Importación CAD:** Lectura y visualización de archivos **STEP (`.step`, `.stp`)**, **IGES (`.iges`, `.igs`)**, **BREP (`.brep`)** y **STL (`.stl`)**.

### 2.2 Condiciones de Contorno y Cargas en Superficies
1. **Material:** Asignación desde la base de datos (Acero estructural, Aluminio 6061-T6, Concreto, etc.).
2. **Densidad de Malla:** Selector de refinamiento de discretización de 1 a 5.
3. **Cara de Empotramiento (Fixed Face):** Restricción de cara espacial ($X^-, X^+, Y^-, Y^+, Z^-, Z^+$ o Detección Automática).
4. **Cara y Dirección de Carga:** Selección de superficie de aplicación y vector de fuerza ($F_x, F_y, F_z$).
5. **Magnitud de Carga:** Entrada numérica en Newtons ($N$).

### 2.3 Visualizador 3D de Esfuerzos de Von Mises
- Mapeo cromático de calor continuo sobre la geometría (Azul = Esfuerzo Mínimo, Rojo = Esfuerzo Máximo Crítico).
- Control de cámara orbital táctil (rotación 360°, paneo y zoom).
- Exportación del reporte técnico formal en PDF y del modelo tridimensional en formato `.glb`.

---

## 💻 3. Terminal Avanzada de Ingeniería

La aplicación incluye un intérprete de comandos Unix integrado en su sandbox local para usuarios avanzados, investigadores e ingenieros. Permite ejecutar simulaciones directas, automatizaciones y comprobaciones de integridad físico-matemática:

### 3.1 Comandos de Validación y Pipelines Especiales
- **`test-calculix`:** Validación de elasticidad lineal 3D (Cubo unitario C3D8 sometido a $P = 400\text{ N}$). Muestra la tensión axial exacta $\sigma_z = 400\text{ MPa}$, la deformación de Hooke $\delta_z = +0.001905\text{ mm}$ y la contracción de Poisson $\delta_x = \delta_y = -0.000571\text{ mm}$ con $0.0000\%$ de error analítico.
- **`test-calculix-parallel`:** Ejecuta la simulación anterior con aceleración multi-núcleo OpenMP en todos los núcleos de la CPU, comprobando determinismo numérico del $100\%$.
- **`test-frame` / `test-portico`:** Análisis estructural completo de un pórtico plano 2D con elementos viga B31. Reporta el equilibrio estático global (cortante basal $\sum R_x = -10\text{ kN}$, momento de vuelco $40\text{ kN}\cdot\text{m}$, reacciones axiales $\pm 8\text{ kN}$) y la deriva elástica de entrepiso.
- **`run-sim-test`:** Simulación automatizada de viga en voladizo ($100 \times 10 \times 10\text{ mm}$, $P = -100\text{ N}$). Muestra el mallado tetraédrico C3D4, la deflexión en la punta ($\delta = 0.1679\text{ mm}$) y la comparación contra la teoría analítica de Euler-Bernoulli ($\delta = 0.1905\text{ mm}$).
- **`test-cad-solve`:** Pipeline completo sin interfaz: crea una barra sólida $2 \times 2 \times 10\text{ mm}$ en `DRAWEXE`, genera la malla en `Gmsh`, asigna condiciones de contorno y calcula la respuesta elástica en CalculiX.
- **`test-step-solve` / `test-bracket-solve`:** Pipeline de importación de modelos industriales en formato STEP (`linkrods.step`, `bracket_simple.step`), con detección espacial automática de caras de anclaje y carga.
- **`test-coordinate-fallback`:** Prueba el algoritmo de detección geométrica de planos de apoyo en geometrías sin grupos nombrados.
- **`test-gmsh`:** Comprueba la integración de operaciones booleanas CSG (Cilindro $-$ Esfera) y generación de malla tetraédrica 3D ($V_{\text{teórico}} = 48.69\text{ mm}^3$).
- **`test-draw`:** Prueba la inicialización de OpenCASCADE en modo headless generando un prisma ortoédrico de $10 \times 10 \times 10\text{ mm}$ ($1\,000\text{ mm}^3$).
- **`test-dat-parser` / `test-frd-parser`:** Valida la extracción de desplazamientos y fuerzas internas desde los archivos de resultados `.dat` y `.frd`.

### 3.2 Comandos de Sistema y Gestión de Archivos
- `ls [ruta]`: Listar archivos de simulación en el sandbox (`.inp`, `.dat`, `.frd`, `.glb`, `.pdf`).
- `cd <directorio>`: Navegar entre directorios internos del espacio de trabajo.
- `pwd`: Consultar la ruta del directorio activo actual.
- `cat <archivo>`: Visualizar el contenido de archivos de texto o decks `.inp` directamente en pantalla.
- `mkdir <nombre>` / `rm [-rf] <archivo/carpeta>`: Crear o eliminar directorios y archivos.
- `ccx <nombre_deck>`: Ejecuta directamente CalculiX `ccx -i <nombre_deck>` sobre cualquier archivo `.inp`.
- `gmsh <argumentos>` / `DRAWEXE <argumentos>`: Invocar directamente los binarios nativos con parámetros personalizados.
- **Exportar Reporte / Copiar Log:** Botones dedicados en la barra superior para exportar el registro completo a PDF o copiarlo al portapapeles.

---

## 🔒 4. Requisitos y Recomendaciones
- **Android 7.0 (API 24) o superior** (Optimizado para Android 14 / 15 y arquitecturas ARM64 `arm64-v8a`).
- **RAM recomendada:** 3 GB o superior para modelos de más de 20,000 elementos finitos.
- **Almacenamiento:** Los archivos temporales se gestionan de forma segura y los reportes PDF se exportan a la carpeta `Descargas/`.
