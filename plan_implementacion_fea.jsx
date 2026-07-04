import { useState } from "react";

const stages = [
  {
    id: "A",
    title: "Cerrar Fase 1",
    subtitle: "Motor completo",
    color: "amber",
    progress: 85,
    eta: "1-2 semanas",
    steps: [
      {
        id: "A1",
        title: "Pipeline CAD completo (Modo Abaqus)",
        status: "next",
        files: ["AssetHelper.java", "GmshRunner.java", "MshToInpConverter.java"],
        description: "El binario Gmsh ya existe. Falta el hilo Java que lo invoca y convierte su salida al formato CalculiX.",
        tasks: [
          { done: false, text: "Crear file-picker Intent para STL/STEP/IGES en el Fragment de Solid Analysis" },
          { done: false, text: "Escribir GmshRunner.java: ejecutar `gmsh archivo.step -3 -o salida.msh -v 0` via ProcessBuilder" },
          { done: false, text: "Escribir MshToInpConverter.java: leer secciones $Nodes y $Elements del .msh, emitir *NODE y *ELEMENT,TYPE=C3D4" },
          { done: false, text: "Conectar con InpEnricher.java existente para inyectar material y condiciones de frontera" },
          { done: false, text: "Pasar el .inp resultante a CalculixRunner y verificar que genera .frd" },
        ],
        note: "Gmsh genera tetraedros C3D4 por defecto. InpEnricher ya funciona — solo conectarlo.",
      },
      {
        id: "A2",
        title: "Structural Result Mapping — Diagramas N/V/M (Modo SAP2000)",
        status: "next",
        files: ["StructuralInpGenerator.java", "DatParser.java"],
        description: "CalculiX escribe las fuerzas de sección al archivo .dat cuando el .inp incluye *SECTION PRINT. Hay que agregar esa tarjeta al generador y parsear la salida.",
        tasks: [
          { done: false, text: "Agregar `*SECTION PRINT, ELSET=BEAMS` al final del *STEP en StructuralInpGenerator.java" },
          { done: false, text: "Escribir DatParser.java: leer el archivo .dat, buscar bloques 'section forces', extraer N, V2, V3, M1, M2 por elemento" },
          { done: false, text: "Crear clase SectionForces con campos: elementId, N, V2, V3, M1, M2, M3" },
          { done: false, text: "Exponer resultados al Fragment de Structural Analysis para renderizado posterior" },
        ],
        note: "El .dat es texto plano, mucho más fácil de parsear que el .frd. El .frd sigue siendo el fuente para el heatmap 3D.",
      },
      {
        id: "A3",
        title: "Integration Testing en dispositivo ARM64",
        status: "pending",
        files: [],
        description: "Todo fue verificado en Linux local. Hay que confirmar que la cadena completa funciona en hardware real ARM64.",
        tasks: [
          { done: false, text: "Generar APK Release firmado" },
          { done: false, text: "Caso A — Modo Solid: cargar STL de cubo, mallar con Gmsh, resolver con CalculiX, ver heatmap Von Mises en SceneView" },
          { done: false, text: "Caso B — Modo Frame: dibujar viga biapoyada con carga puntual central, resolver, verificar valores de M y V en el .dat" },
          { done: false, text: "Validar el symlink libz.so.1 → /system/lib64/libz.so en AssetHelper (verificar crash Tcl)" },
          { done: false, text: "Si CalculiX crashea por ruta Termux hardcodeada: ejecutar patchelf --replace-needed en el binario" },
        ],
        note: "CRÍTICO: revisar ALINEACION_Y_DEPENDENCIAS.md — la ruta absoluta de Termux en libCalculiX.so puede causar crash al arranque.",
      },
    ],
  },
  {
    id: "B",
    title: "Editor Estructural",
    subtitle: "Modo SAP2000",
    color: "teal",
    progress: 0,
    eta: "4-6 semanas",
    steps: [
      {
        id: "B1",
        title: "Custom OpenGL ES Renderer — Lienzo de pórticos",
        status: "pending",
        files: ["FrameRenderer.java", "FrameGLSurfaceView.java", "GridShader.glsl"],
        description: "El núcleo gráfico del Modo Civil. El usuario dibuja su estructura directamente en pantalla.",
        tasks: [
          { done: false, text: "Crear FrameGLSurfaceView extends GLSurfaceView con contexto OpenGL ES 3.0" },
          { done: false, text: "Renderizar cuadrícula (grid) con líneas finas — coordenadas del mundo en metros" },
          { done: false, text: "Gestos: tap en vacío = crear Nodo, tap+drag Nodo→Nodo = crear Elemento de barra" },
          { done: false, text: "Renderizar nodos como círculos (shaders), barras como líneas coloreadas (viga=azul, columna=rojo)" },
          { done: false, text: "Renderizar apoyos: triángulo para articulado, rectángulo para empotrado" },
          { done: false, text: "Renderizar cargas: flecha con magnitud flotante sobre el nodo" },
        ],
        note: "No usar LibGDX — secuestra la Activity. GLSurfaceView convive perfectamente con botones nativos de Android alrededor.",
      },
      {
        id: "B2",
        title: "Biblioteca de Secciones Transversales",
        status: "pending",
        files: ["assets/sections.json", "SectionLibrary.java", "SectionPickerDialog.java"],
        description: "Base de datos de perfiles comerciales para alimentar la tarjeta *BEAM SECTION de CalculiX.",
        tasks: [
          { done: false, text: "Crear sections.json con perfiles: W8×31, W12×50, IPE 200, IPE 300, IPE 400, HSS 100×100×6, Tubo circular Ø200×10, Rectangular 300×400" },
          { done: false, text: "Cada perfil: nombre, tipo (I/rectangular/circular/tubo), h, b, tf, tw, A, Iy, Iz, J" },
          { done: false, text: "SectionLibrary.java: cargar y consultar sections.json" },
          { done: false, text: "SectionPickerDialog: diálogo RecyclerView para seleccionar perfil al crear elemento" },
          { done: false, text: "Almacenar la sección elegida en el modelo de datos FrameElement" },
        ],
        note: "Los valores de Iy, Iz, J son necesarios para BEAM SECTION GENERAL. Calcularlos de las dimensiones si no están en el JSON.",
      },
      {
        id: "B3",
        title: "Generador .inp para Pórticos (Elementos B32)",
        status: "pending",
        files: ["StructuralInpGenerator.java"],
        description: "Traducir el modelo gráfico del lienzo al formato CalculiX para vigas cuadráticas B32.",
        tasks: [
          { done: false, text: "Para cada barra del modelo: calcular nodo intermedio automáticamente = ( (x1+x2)/2, (y1+y2)/2, (z1+z2)/2 )" },
          { done: false, text: "Emitir *ELEMENT, TYPE=B32, ELSET=BEAMS con los 3 nodos (extremo1, intermedio, extremo2)" },
          { done: false, text: "Por cada sección diferente: emitir *BEAM SECTION, ELSET=E_n, SECTION=GENERAL con los 7 valores de sección" },
          { done: false, text: "Emitir *MATERIAL, NAME=... y *ELASTIC con E y ν" },
          { done: false, text: "Emitir apoyos como *BOUNDARY (nodo, DoF_inicio, DoF_fin, valor=0)" },
          { done: false, text: "Emitir cargas como *CLOAD (nodo, DoF, magnitud)" },
          { done: false, text: "Al final del *STEP: *SECTION PRINT, ELSET=BEAMS y *NODE PRINT, NSET=ALL" },
        ],
        note: "B32 requiere obligatoriamente el nodo intermedio en la posición 2 del elemento. Sin él, CalculiX abortará.",
      },
      {
        id: "B4",
        title: "Diagram Engine — BMD / SFD / AFD",
        status: "pending",
        files: ["DiagramRenderer.java", "DatParser.java"],
        description: "Visualización clásica de ingeniería civil: diagramas de Momento Flector, Cortante y Fuerza Axial.",
        tasks: [
          { done: false, text: "Completar DatParser.java (iniciado en A2) para extraer todos los valores por elemento" },
          { done: false, text: "Calcular escala automática: valor_máximo_absoluto → tamaño visual del pico del diagrama" },
          { done: false, text: "Para BMD: dibujar polígono perpendicular a cada barra en Canvas de Android (en overlay sobre el GLSurfaceView)" },
          { done: false, text: "Para SFD y AFD: ídem con colores distintos — SFD=azul, BMD=rojo, AFD=verde" },
          { done: false, text: "Mostrar etiquetas de valores en los picos de los diagramas" },
          { done: false, text: "Toggle UI para alternar entre diagrama deformado / BMD / SFD / AFD" },
        ],
        note: "Los diagramas se dibujan en Android Canvas 2D, no en OpenGL. Superponer un Canvas transparente sobre el GLSurfaceView.",
      },
    ],
  },
  {
    id: "C",
    title: "Editor 3D Sólidos",
    subtitle: "Modo Abaqus",
    color: "purple",
    progress: 0,
    eta: "3-4 semanas",
    steps: [
      {
        id: "C1",
        title: "CAD Primitivas — Box, Cylinder, Sphere vía OCCT",
        status: "pending",
        files: ["OcctPrimitivesJNI.cpp", "OcctPrimitivesJNI.java", "SolidEditorFragment.xml"],
        description: "OpenCASCADE ya está embebida en las libTK*.so. Exponer creación de sólidos básicos vía JNI.",
        tasks: [
          { done: false, text: "OcctPrimitivesJNI.cpp: implementar createBox(l,w,h), createCylinder(r,h), createSphere(r) usando BRepPrimAPI_Make*" },
          { done: false, text: "Exportar cada sólido como BREP a archivo temporal, luego pasarlo a Gmsh para mallar" },
          { done: false, text: "SolidEditorFragment: botones flotantes '+Box', '+Cylinder', '+Sphere' con diálogo de dimensiones" },
          { done: false, text: "Mostrar la malla resultante en SceneView (ya funciona — reutilizar el pipeline GLB)" },
        ],
        note: "No implementar el visualizador OCCT nativo — usar siempre Gmsh→FRD→GLB→SceneView. Es el camino ya probado.",
      },
      {
        id: "C2",
        title: "Operaciones Booleanas vía OCCT",
        status: "pending",
        files: ["OcctBooleanJNI.cpp"],
        description: "Combinar sólidos mediante unión, corte e intersección.",
        tasks: [
          { done: false, text: "OcctBooleanJNI.cpp: implementar fuseShapes(a.brep, b.brep)→out.brep usando BRepAlgoAPI_Fuse" },
          { done: false, text: "Implementar cutShapes (BRepAlgoAPI_Cut) e intersectShapes (BRepAlgoAPI_Common)" },
          { done: false, text: "UI: modo 'selección de 2 sólidos' + menú contextual Union/Cut/Intersect" },
          { done: false, text: "Re-mallar el resultado con Gmsh y actualizar la vista SceneView" },
        ],
        note: "Si OCCT no está disponible como JNI en esta fase, postergar y priorizar C3 y C4.",
      },
      {
        id: "C3",
        title: "Ray-Casting — Selección táctil de caras",
        status: "pending",
        files: ["FaceSelector.java", "SolidEditorFragment.java"],
        description: "Permitir al usuario tocar el modelo 3D para seleccionar una cara y aplicarle condiciones.",
        tasks: [
          { done: false, text: "Capturar MotionEvent.ACTION_DOWN en la vista de SceneView" },
          { done: false, text: "Calcular rayo desde la posición de cámara a través del píxel tocado" },
          { done: false, text: "Intersectar el rayo con las caras del modelo (bounding boxes de los triángulos del GLB)" },
          { done: false, text: "Resaltar la cara seleccionada (cambiar color del material en SceneView)" },
          { done: false, text: "Mostrar BottomSheet con opciones: 'Aplicar presión', 'Empotramiento', 'Refinar malla aquí'" },
          { done: false, text: "Almacenar la selección en el modelo de datos como FaceCondition" },
        ],
        note: "SceneView no expone ray-casting directamente. Implementar contra los triángulos del GLB parseado.",
      },
      {
        id: "C4",
        title: "Material Library UI",
        status: "pending",
        files: ["MaterialDatabase.java", "assets/materials.json"],
        description: "Base de datos de materiales para el Modo Abaqus. InpEnricher ya inyecta propiedades — solo conectar la UI.",
        tasks: [
          { done: false, text: "Crear materials.json: Acero A36 (E=200GPa, ν=0.3, ρ=7850, σy=250MPa), Aluminio 6061-T6, Concreto 25MPa, Titanio Ti-6Al-4V, Madera (ortótropo)" },
          { done: false, text: "MaterialDatabase.java: cargar y consultar materials.json" },
          { done: false, text: "MaterialPickerDialog: diálogo con tarjetas de material mostrando propiedades clave" },
          { done: false, text: "Conectar la selección de material con InpEnricher.java (ya implementado)" },
        ],
        note: "InpEnricher.java ya está unit-tested. Esta tarea es puro Frontend.",
      },
      {
        id: "C5",
        title: "Mesh Controls — Densidad y refinamiento local",
        status: "pending",
        files: ["GmshRunner.java"],
        description: "Dar al usuario control sobre la calidad de la malla sin exponerle la CLI de Gmsh.",
        tasks: [
          { done: false, text: "Agregar parámetro meshDensity (1-5) a GmshRunner" },
          { done: false, text: "Mapear el slider a: density=1 → -clmax 50, density=3 → -clmax 20, density=5 → -clmax 5" },
          { done: false, text: "UI: slider 'Coarse ←→ Fine' con preview del número estimado de elementos" },
          { done: false, text: "Refinamiento local: si una cara fue marcada en C3, emitir Mesh.Field.setNumber para esa zona en el script de Gmsh" },
        ],
        note: "Valores de -clmax en mm. Advertir al usuario si estima más de 10k elementos en un teléfono de gama baja.",
      },
    ],
  },
  {
    id: "D",
    title: "Publicación",
    subtitle: "Play Store",
    color: "coral",
    progress: 0,
    eta: "3-4 semanas",
    steps: [
      {
        id: "D1",
        title: "INP Importer — Compatibilidad Abaqus",
        status: "pending",
        files: ["AbaqusInpImporter.java"],
        description: "Permitir importar modelos .inp hechos en Abaqus o CalculiX directamente en la app.",
        tasks: [
          { done: false, text: "AbaqusInpImporter.java: parser de tarjetas *NODE, *ELEMENT, *MATERIAL, *ELASTIC, *BOUNDARY, *CLOAD, *STEP" },
          { done: false, text: "Reconstruir el AnalysisModel C++ desde el .inp importado" },
          { done: false, text: "Detectar automáticamente si el modelo es Frame (B31/B32) o Solid (C3D4/C3D10) y abrir el editor correspondiente" },
          { done: false, text: "File-picker para archivos .inp desde almacenamiento externo" },
        ],
        note: "Dado que CalculiX usa la misma sintaxis que Abaqus, el mismo parser funciona para ambos. Ignorar tarjetas desconocidas con un warning.",
      },
      {
        id: "D2",
        title: "PDF Reporting",
        status: "pending",
        files: ["ReportGenerator.java", "build.gradle"],
        description: "Generar un reporte profesional descargable con el modelo, resultados y screenshots.",
        tasks: [
          { done: false, text: "Añadir dependencia iText7 Community (GPL-compatible) al build.gradle" },
          { done: false, text: "Capturar screenshot del modelo 3D: sceneView.draw(canvas) o PixelCopy" },
          { done: false, text: "Generar tabla de resultados: nodos con máx desplazamiento, elementos con máx Von Mises o máx Momento" },
          { done: false, text: "ReportGenerator.java: componer PDF con logo de la app, datos del análisis, imagen del modelo, tabla de resultados" },
          { done: false, text: "Compartir PDF mediante FileProvider + Intent.ACTION_SEND" },
        ],
        note: "iText7 Community usa la licencia AGPL, compatible con GPL. Verificar que no rompe las obligaciones de distribución.",
      },
      {
        id: "D3",
        title: "Performance — Threading y feedback al usuario",
        status: "pending",
        files: ["GmshRunner.java", "CalculixRunner.java", "DatParser.java"],
        description: "Mover todo el trabajo pesado fuera del hilo principal para no congelar la UI.",
        tasks: [
          { done: false, text: "Migrar GmshRunner a Kotlin Coroutine (Dispatchers.IO) o AsyncTask deprecado → ExecutorService" },
          { done: false, text: "Mostrar ProgressBar indeterminada + texto 'Mallando...' / 'Calculando...' / 'Parseando resultados...' durante cada etapa" },
          { done: false, text: "Migrar DatParser a background thread" },
          { done: false, text: "Para mallas > 10k elementos: mostrar estimación de tiempo basada en benchmark del dispositivo" },
          { done: false, text: "Botón 'Cancelar' que envíe SIGTERM al proceso ccx/gmsh si el usuario aborta" },
        ],
        note: "CalculixRunner ya usa JNI — revisar si la ejecución nativa bloquea el hilo JNI. Si es así, lanzar en nuevo Thread antes del JNI call.",
      },
      {
        id: "D4",
        title: "Play Store — Publicación",
        status: "pending",
        files: ["app/build.gradle", "fastlane/"],
        description: "Preparar la app para distribución pública cumpliendo con GPL y políticas de Google.",
        tasks: [
          { done: false, text: "Generar APK/AAB Release firmado con clave de producción" },
          { done: false, text: "Preparar listing: screenshots Modo Frame + Modo Solid, icono final, descripción ES/EN" },
          { done: false, text: "Declarar que el código fuente está disponible en GitHub (obligación GPL)" },
          { done: false, text: "Configurar monetización: app base gratis (Frame 2D), compra interna para Frame 3D + Solid Mode + PDF" },
          { done: false, text: "Subir a Play Console — track Pruebas Internas → Alpha → Beta → Producción" },
        ],
        note: "La GPL permite monetización con anuncios y compras internas. Solo exige que el APK y el código fuente estén disponibles.",
      },
    ],
  },
];

const STATUS_COLOR = { done: "success", next: "warning", pending: "default" };
const STATUS_LABEL = { done: "Completado", next: "Próximo paso", pending: "Pendiente" };
const STAGE_BG = { amber: "var(--bg-warning)", teal: "var(--bg-success)", purple: "var(--bg-pro)", coral: "#FAECE7" };
const STAGE_TEXT = { amber: "var(--text-warning)", teal: "var(--text-success)", purple: "var(--text-pro)", coral: "#993C1D" };
const STAGE_BORDER = { amber: "var(--border-warning)", teal: "var(--border-success)", purple: "var(--border-pro)", coral: "#F5C4B3" };

function ProgressBar({ value, color }) {
  const bg = { amber: "#EF9F27", teal: "#1D9E75", purple: "#7F77DD", coral: "#D85A30" };
  return (
    <div style={{ background: "var(--border)", borderRadius: 4, height: 6, width: "100%", overflow: "hidden" }}>
      <div style={{ width: `${value}%`, height: "100%", background: bg[color] || "#888", borderRadius: 4, transition: "width 0.4s ease" }} />
    </div>
  );
}

function Badge({ status }) {
  const colors = {
    done: { bg: "var(--bg-success)", color: "var(--text-success)", border: "var(--border-success)" },
    next: { bg: "var(--bg-warning)", color: "var(--text-warning)", border: "var(--border-warning)" },
    pending: { bg: "var(--surface-1)", color: "var(--text-muted)", border: "var(--border)" },
  };
  const c = colors[status] || colors.pending;
  return (
    <span style={{ background: c.bg, color: c.color, border: `0.5px solid ${c.border}`, borderRadius: "var(--radius)", padding: "2px 8px", fontSize: 11, fontWeight: 500, whiteSpace: "nowrap" }}>
      {STATUS_LABEL[status]}
    </span>
  );
}

function StepCard({ step }) {
  const [open, setOpen] = useState(step.status === "next");
  const done = step.tasks.filter(t => t.done).length;
  const total = step.tasks.length;
  return (
    <div style={{ background: "var(--surface-2)", border: "0.5px solid var(--border)", borderRadius: 12, overflow: "hidden", marginBottom: 10 }}>
      <button onClick={() => setOpen(o => !o)} style={{ width: "100%", background: "none", border: "none", cursor: "pointer", padding: "14px 16px", display: "flex", alignItems: "flex-start", gap: 12, textAlign: "left" }}>
        <span style={{ width: 32, height: 32, minWidth: 32, borderRadius: "50%", background: "var(--surface-1)", border: "0.5px solid var(--border-strong)", display: "flex", alignItems: "center", justifyContent: "center", fontFamily: "var(--font-mono)", fontSize: 12, fontWeight: 500, color: "var(--text-secondary)" }}>
          {step.id}
        </span>
        <div style={{ flex: 1 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 8, flexWrap: "wrap" }}>
            <span style={{ fontSize: 14, fontWeight: 500, color: "var(--text-primary)" }}>{step.title}</span>
            <Badge status={step.status} />
          </div>
          <div style={{ display: "flex", gap: 12, marginTop: 6, alignItems: "center" }}>
            <span style={{ fontSize: 12, color: "var(--text-muted)" }}>{done}/{total} tareas</span>
            <div style={{ flex: 1, maxWidth: 120 }}>
              <ProgressBar value={(done / total) * 100} color="teal" />
            </div>
          </div>
        </div>
        <i className={`ti ${open ? "ti-chevron-up" : "ti-chevron-down"}`} aria-hidden="true" style={{ fontSize: 16, color: "var(--text-muted)", marginTop: 6 }} />
      </button>

      {open && (
        <div style={{ borderTop: "0.5px solid var(--border)", padding: "0 16px 16px" }}>
          <p style={{ fontSize: 13, color: "var(--text-secondary)", margin: "12px 0 12px", lineHeight: 1.6 }}>{step.description}</p>

          {step.files.length > 0 && (
            <div style={{ display: "flex", flexWrap: "wrap", gap: 6, marginBottom: 12 }}>
              {step.files.map(f => (
                <span key={f} style={{ fontFamily: "var(--font-mono)", fontSize: 11, background: "var(--surface-1)", border: "0.5px solid var(--border-strong)", borderRadius: 4, padding: "2px 7px", color: "var(--text-accent)" }}>
                  {f}
                </span>
              ))}
            </div>
          )}

          <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
            {step.tasks.map((task, i) => (
              <div key={i} style={{ display: "flex", gap: 10, alignItems: "flex-start" }}>
                <div style={{ width: 16, height: 16, minWidth: 16, marginTop: 2, borderRadius: 4, border: `1.5px solid ${task.done ? "var(--border-success)" : "var(--border-strong)"}`, background: task.done ? "var(--bg-success)" : "transparent", display: "flex", alignItems: "center", justifyContent: "center" }}>
                  {task.done && <i className="ti ti-check" style={{ fontSize: 10, color: "var(--text-success)" }} aria-hidden="true" />}
                </div>
                <span style={{ fontSize: 13, color: task.done ? "var(--text-muted)" : "var(--text-primary)", textDecoration: task.done ? "line-through" : "none", lineHeight: 1.55 }}>
                  {task.text}
                </span>
              </div>
            ))}
          </div>

          {step.note && (
            <div style={{ marginTop: 14, background: "var(--bg-accent)", border: "0.5px solid var(--border-accent)", borderRadius: 8, padding: "10px 12px", display: "flex", gap: 8 }}>
              <i className="ti ti-info-circle" aria-hidden="true" style={{ fontSize: 15, color: "var(--text-accent)", marginTop: 1, minWidth: 15 }} />
              <span style={{ fontSize: 12, color: "var(--text-accent)", lineHeight: 1.55 }}>{step.note}</span>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

function StagePanel({ stage, active, onClick }) {
  const totalTasks = stage.steps.flatMap(s => s.tasks).length;
  const doneTasks = stage.steps.flatMap(s => s.tasks).filter(t => t.done).length;
  return (
    <div
      role="button"
      tabIndex={0}
      onClick={onClick}
      onKeyDown={e => e.key === "Enter" && onClick()}
      style={{
        border: active ? `2px solid ${STAGE_TEXT[stage.color]}` : "0.5px solid var(--border)",
        borderRadius: 12, padding: "14px 16px", cursor: "pointer", background: active ? STAGE_BG[stage.color] : "var(--surface-2)", transition: "all 0.15s",
        flex: 1, minWidth: 140,
      }}
    >
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 8 }}>
        <span style={{ width: 28, height: 28, borderRadius: "50%", background: STAGE_TEXT[stage.color], display: "flex", alignItems: "center", justifyContent: "center", fontSize: 13, fontWeight: 500, color: "#fff" }}>
          {stage.id}
        </span>
        <span style={{ fontSize: 11, color: STAGE_TEXT[stage.color], fontWeight: 500 }}>{stage.eta}</span>
      </div>
      <div style={{ fontSize: 14, fontWeight: 500, color: "var(--text-primary)", marginBottom: 2 }}>{stage.title}</div>
      <div style={{ fontSize: 12, color: "var(--text-muted)", marginBottom: 10 }}>{stage.subtitle}</div>
      <ProgressBar value={stage.progress} color={stage.color} />
      <div style={{ marginTop: 5, fontSize: 11, color: "var(--text-muted)" }}>{doneTasks}/{totalTasks} tareas</div>
    </div>
  );
}

const COMPLETED_ITEMS = [
  "Navigation Drawer + tabs MODEL/TERMINAL/VIEWER",
  "SceneView v0.10.0 integrado (Filament)",
  "FRD → GLB converter en C++/tinygltf (TET4 + TRIA3)",
  "Heatmap Von Mises (azul a rojo) en vértices",
  "NativeFeaCore JNI wrapper completo",
  "CalculixRunner (ccx via ProcessBuilder)",
  "ProjectStore: serialización JSON del estado",
  "InpEnricher: inyección de propiedades en mallas Gmsh",
  "Unit tests NDK en Linux: test_analysis_model, test_calculix_runner, test_project_store",
  "InpEnricherTest JUnit validado",
  "CalculixRunner.cpp: fix buffer PATH_MAX compilado",
  "Alineación 16 KB verificada en todos los .so (Android 15 listo)",
  "Dependencias dinámicas mapeadas en jniLibs/arm64-v8a",
  "Symlink libz.so.1 → /system/lib64/libz.so implementado",
];

export default function ImplementationPlan() {
  const [activeStage, setActiveStage] = useState("A");
  const [showCompleted, setShowCompleted] = useState(false);
  const stage = stages.find(s => s.id === activeStage);

  const totalAll = stages.flatMap(s => s.steps.flatMap(st => st.tasks)).length + COMPLETED_ITEMS.length;
  const doneAll = COMPLETED_ITEMS.length;
  const pct = Math.round((doneAll / totalAll) * 100);

  return (
    <div style={{ padding: "1.25rem 0" }}>
      <h2 className="sr-only">Plan de implementación — Structural FEA Advanced</h2>

      {/* Header */}
      <div style={{ marginBottom: 20 }}>
        <div style={{ fontSize: 11, fontFamily: "var(--font-mono)", color: "var(--text-muted)", letterSpacing: 1, marginBottom: 6 }}>
          STRUCTURAL FEA ADVANCED · ANDROID NDK
        </div>
        <h2 style={{ margin: 0, fontSize: 22, fontWeight: 500, color: "var(--text-primary)" }}>Plan de implementación</h2>
        <p style={{ margin: "6px 0 0", fontSize: 14, color: "var(--text-secondary)" }}>
          Motor CalculiX compilado · CalculiX 2.23 + SPOOLES + ARPACK + OpenBLAS + Gmsh + OCCT
        </p>
      </div>

      {/* Overall progress */}
      <div style={{ background: "var(--surface-1)", border: "0.5px solid var(--border)", borderRadius: 12, padding: "16px", marginBottom: 20 }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 8 }}>
          <span style={{ fontSize: 13, color: "var(--text-secondary)" }}>Progreso total del proyecto</span>
          <span style={{ fontFamily: "var(--font-mono)", fontSize: 15, fontWeight: 500, color: "var(--text-primary)" }}>{pct}%</span>
        </div>
        <ProgressBar value={pct} color="teal" />
        <div style={{ display: "flex", gap: 20, marginTop: 12, flexWrap: "wrap" }}>
          <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
            <div style={{ width: 10, height: 10, borderRadius: "50%", background: "var(--fill-success)" }} />
            <span style={{ fontSize: 12, color: "var(--text-muted)" }}>{COMPLETED_ITEMS.length} completados</span>
          </div>
          <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
            <div style={{ width: 10, height: 10, borderRadius: "50%", background: "var(--fill-warning)" }} />
            <span style={{ fontSize: 12, color: "var(--text-muted)" }}>2 en progreso</span>
          </div>
          <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
            <div style={{ width: 10, height: 10, borderRadius: "50%", background: "var(--border-strong)" }} />
            <span style={{ fontSize: 12, color: "var(--text-muted)" }}>15 pendientes</span>
          </div>
        </div>
      </div>

      {/* Completed section */}
      <div style={{ marginBottom: 20 }}>
        <button
          onClick={() => setShowCompleted(o => !o)}
          style={{ width: "100%", background: "var(--bg-success)", border: "0.5px solid var(--border-success)", borderRadius: 12, padding: "12px 16px", cursor: "pointer", display: "flex", justifyContent: "space-between", alignItems: "center" }}
        >
          <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
            <i className="ti ti-circle-check" aria-hidden="true" style={{ fontSize: 17, color: "var(--text-success)" }} />
            <span style={{ fontSize: 14, fontWeight: 500, color: "var(--text-success)" }}>Fase 0 + Fase 1 (parcial) — Completados</span>
          </div>
          <i className={`ti ${showCompleted ? "ti-chevron-up" : "ti-chevron-down"}`} aria-hidden="true" style={{ fontSize: 15, color: "var(--text-success)" }} />
        </button>
        {showCompleted && (
          <div style={{ background: "var(--surface-2)", border: "0.5px solid var(--border)", borderRadius: "0 0 12px 12px", marginTop: -1, padding: "12px 16px", display: "grid", gridTemplateColumns: "1fr 1fr", gap: 6 }}>
            {COMPLETED_ITEMS.map((item, i) => (
              <div key={i} style={{ display: "flex", gap: 8, alignItems: "flex-start" }}>
                <i className="ti ti-check" aria-hidden="true" style={{ fontSize: 13, color: "var(--text-success)", marginTop: 3, minWidth: 13 }} />
                <span style={{ fontSize: 12, color: "var(--text-muted)", lineHeight: 1.5 }}>{item}</span>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Stage selectors */}
      <div style={{ display: "flex", gap: 10, marginBottom: 16, flexWrap: "wrap" }}>
        {stages.map(s => (
          <StagePanel key={s.id} stage={s} active={activeStage === s.id} onClick={() => setActiveStage(s.id)} />
        ))}
      </div>

      {/* Stage detail */}
      {stage && (
        <div>
          <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 14, paddingBottom: 14, borderBottom: "0.5px solid var(--border)" }}>
            <div style={{ width: 36, height: 36, borderRadius: "50%", background: STAGE_TEXT[stage.color], display: "flex", alignItems: "center", justifyContent: "center", fontSize: 16, fontWeight: 500, color: "#fff" }}>
              {stage.id}
            </div>
            <div>
              <div style={{ fontSize: 17, fontWeight: 500, color: "var(--text-primary)" }}>{stage.title} — {stage.subtitle}</div>
              <div style={{ fontSize: 12, color: "var(--text-muted)" }}>Estimado: {stage.eta} · {stage.steps.length} módulos</div>
            </div>
          </div>
          {stage.steps.map(step => <StepCard key={step.id} step={step} />)}
        </div>
      )}

      {/* Critical reminders */}
      <div style={{ marginTop: 20, background: "var(--bg-danger)", border: "0.5px solid var(--border-danger)", borderRadius: 12, padding: "14px 16px" }}>
        <div style={{ display: "flex", gap: 8, alignItems: "flex-start" }}>
          <i className="ti ti-alert-triangle" aria-hidden="true" style={{ fontSize: 16, color: "var(--text-danger)", marginTop: 1, minWidth: 16 }} />
          <div>
            <div style={{ fontSize: 13, fontWeight: 500, color: "var(--text-danger)", marginBottom: 6 }}>Puntos críticos antes de publicar</div>
            <div style={{ fontSize: 12, color: "var(--text-danger)", lineHeight: 1.7 }}>
              1. La <span style={{ fontFamily: "var(--font-mono)" }}>ruta absoluta de Termux</span> hardcodeada en <span style={{ fontFamily: "var(--font-mono)" }}>libCalculiX.so</span> puede causar crash al arranque en dispositivos reales. Verificar con patchelf si ocurre.<br />
              2. SceneView v0.10.0 requiere <span style={{ fontFamily: "var(--font-mono)" }}>minSdkVersion=24</span> (Android 7). Confirmar que el build.gradle lo tiene.<br />
              3. La GPL exige publicar el código fuente completo junto con el APK. GitHub ya lo cumple — incluir la URL en el listing de Play Store.
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
