# Notas de Versión - Structural Analysis FEA 3D

## 📦 Structural Analysis FEA 3D v0.5.0 (Pre-Release)

### 🚀 Mejoras en Visor 3D, Costura de Superficies IGES y Validación Físico-Matemática Completa

#### 1. Estabilidad y Persistencia en Visor 3D
- **Cámara Independiente al Cambiar de Pestaña:**
  - Se desacopló la recarga del composable 3D en `SceneViewBridge`. Al alternar entre las pestañas de Parámetros, Visor 3D y Logs, la orientación y posición de la cámara del usuario se mantienen intactas sin recentrarse automáticamente.
  - La función de centrado queda asignada exclusivamente al botón *Centrar Vista* del visor 3D.
- **Retención y Reemplazo Seguro de Resultados 3D:**
  - Se eliminó el borrado preventivo de `job_solid.glb` durante la limpieza previa al cálculo.
  - El modelo 3D visualizado permanece en pantalla durante la ejecución y se reemplaza de forma atómica una vez que CalculiX y la conversión FRD→GLB finalizan con éxito, eliminando parpadeos, pantallas vacías o posibles cierres involuntarios de la app.

#### 2. Soporte y Costura Robusta de Archivos CAD IGES / IGS
- **Corrección en DRAWEXE (OpenCASCADE):**
  - Se actualizó el comando de serialización de sólidos cosidos en `GmshRunner` de `save` a `writebrep`.
  - Garantiza que modelos en formato `.igs` y `.iges` cosan de manera determinista sus caras NURBS en sólidos cerrados `.brep`, permitiendo que Gmsh genere la malla volumétrica tetraédrica sin errores de contorno abierto.

#### 3. Validación Físico-Matemática con CalculiX Real y Gmsh
- **29/29 Pruebas Superadas al 100%:**
  - Validación completa del pipeline CAD → Malla C3D4 → Deck CalculiX → Solver ccx → Resultados FRD en todos los formatos CAD soportados (`.step`, `.stp`, `.brep`, `.igs`, `.iges`, `.geo`) y geometrías de prueba (`cantilever_plate`, `perforated_block`, `structural_ibeam`, `flanged_connector`).
- **Verificación Analítica (Euler-Bernoulli):**
  - Validación en viga en voladizo ($100 \times 10 \times 10\text{ mm}$, $P = -100\text{ N}$, $E = 200\,000\text{ MPa}$): flecha analítica $\delta = 0.2000\text{ mm}$ vs FEA $\delta = 0.1762\text{ mm}$ (diferencia esperada de $11.88\% \le 15\%$ por rigidez de elementos C3D4).
- **Consistencia Cross-Formato:**
  - Desviaciones volumétricas inferiores al $0.7\%$ entre los diferentes formatos CAD para una misma geometría.
