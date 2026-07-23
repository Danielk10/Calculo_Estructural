# Plan de Coordinación de Subagentes para Implementación

Para ejecutar el `NUEVO_PLAN_IMPLEMENTACION.md` de forma paralela y acelerada, el Agente Principal (Manager) coordinará a 3 Subagentes Constructores especializados. Para evitar conflictos de escritura en los mismos archivos, cada subagente desarrollará sus componentes de forma aislada, y el Manager realizará la integración final.

## 🏗️ Asignación de Tareas

### Subagente 1: Desarrollador Frontend Civil (Civil UI)
**Objetivo:** Fase 1, Paso 3 (Trazador Gráfico Civil).
- **Tarea:** Crear una nueva clase `GridEditorView.java` (heredando de `View` de Android) en `app/src/main/java/com/diamon/civil/ui/views/`.
- **Requisitos:** Debe ser un lienzo (Canvas) interactivo 2D donde el usuario pueda tocar para crear Nodos, y arrastrar entre nodos para crear Elementos (Vigas). Debe tener un sistema de ajuste a cuadrícula (snap-to-grid) y métodos públicos `getNodes()` y `getElements()` para poder extraer los datos visuales. No debe modificar otros archivos.

### Subagente 2: Desarrollador de Parsing (Backend Civil)
**Objetivo:** Sustituir `DatParser` por `FrdParser` (Mitigar el problema detectado en la Fase 1).
- **Tarea:** Crear una nueva clase `FrdParser.java` en `app/src/main/java/com/diamon/civil/engine/`.
- **Requisitos:** Debe leer un archivo binario/texto `.frd` de CalculiX, buscar los bloques `-4  STRESS` y `-1` (donde están los valores), y mapear el tensor de 6 componentes de CalculiX (S11, S22, S33, S12, S23, S13) a `Cortante 1, Cortante 2, Axial, Momento 1, Momento 2, Torque`. Debe devolver un objeto similar al `ParseResult` antiguo.

### Subagente 3: Desarrollador CAD y Mallado (Backend Sólidos)
**Objetivo:** Fase 3 (Configurador Avanzado de Malla).
- **Tarea:** Modificar `GmshRunner.java` para que acepte un nuevo parámetro `elementOrder` (1 o 2). El script interno de Gmsh (`mesh.geo`) debe inyectar `Mesh.ElementOrder = 2;` si se solicita. También debe agregar soporte para `Mesh.Algorithm3D` (permitir elegir entre Delaunay o Frontal).

## 🚀 Flujo de Ejecución
1. El Manager lanzará a los 3 subagentes en paralelo con modelos de alta capacidad (pro).
2. Cada subagente escribirá su código y reportará al terminar.
3. El Manager revisará el código generado, realizará pruebas locales (si aplica) y ensamblará las piezas (por ejemplo, conectando el `GridEditorView` en `StructuralFragment.java`).
4. Finalmente, el Manager hará el commit de todo el sistema.
