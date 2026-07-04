# Plan Maestro de Implementación: Structural FEA Suite

Este documento describe la hoja de ruta estratégica para desarrollar una plataforma de ingeniería de tubería dual. La aplicación converge en **CalculiX** como el solucionador unificado mientras mantiene flujos de pre-procesamiento y post-procesamiento distintos para **Análisis de Sólidos 3D** y **Análisis Estructural**.

---

## 🏛️ Descripción General de la Arquitectura

El sistema está construido sobre un núcleo compartido de C++ NDK que maneja cálculos pesados, análisis y lógica geométrica, con una capa de interfaz de usuario basada en Kotlin.

### Dos Tuberías, Un Solucionador
1.  **Tubería de Análisis de Sólidos 3D**: CAD (STEP/IGES/BREP) ➔ OpenCASCADE ➔ Gmsh ➔ CalculiX (.inp) ➔ glTF ➔ SceneView.
2.  **Tubería de Análisis Estructural**: Modelo Estructural (Nodos/Vigas/Cáscaras) ➔ Modelo Interno ➔ CalculiX (.inp) ➔ Resultados ➔ Renderizador OpenGL ES (Diagramas).

---

## 📊 Fase 0: Infraestructura de UI y Navegación Principal (COMPLETADO)
- [x] **Navegación Modular**: Implementación de un Navigation Drawer para cambiar entre módulos.
- [x] **Separación de Layouts**: Interfaz de usuario dedicada para **Análisis de Sólidos 3D** y **Análisis Estructural**.
- [x] **Terminal Compartida**: Interfaz de línea de comandos unificada para la ejecución directa de binarios.
- [x] **Vista 3D Híbrida**: Sistema de sub-pestañas en **Análisis de Sólidos 3D** para Parámetros y SceneView.
- [x] **Prototipos Funcionales**: Áreas de entrada iniciales para ambos módulos implementadas.

---

## ⚙️ Fase 1: Núcleo NDK Compartido y Tubería del Solucionador (COMPLETADO)
*Objetivo: Establecer el flujo de extremo a extremo desde la entrada hasta los resultados.*

### 1.1 Núcleo Común (JNI/C++)
- [x] **`AnalysisModel`**: Definir estructuras de datos unificadas en C++ para nodos, elementos y materiales.
- [x] **`toInpString()`**: Lógica para exportar el modelo interno al formato `.inp` de CalculiX.
- [x] **`CalculixRunner`**: Wrapper JNI robusto para la ejecución de `ccx` y gestión de trabajos (Implementado en C++ y expuesto vía NativeFeaCore JNI).
- [x] **`FrdConverter`**: Lógica en C++ usando **tinygltf** para convertir `.frd` a glTF/GLB coloreado.
- [x] **`ProjectStore`**: Serialización JSON/Binaria del estado del proyecto.

### 1.2 Tubería de Análisis de Sólidos 3D
- [x] **`CAD Pipeline`**: Procesamiento de CAD (STEP/IGES/BREP) mediante la ejecución del binario Gmsh (Java).
- [x] **`InpEnricher`**: Lógica para inyectar propiedades de materiales y condiciones de contorno en mallas generadas por Gmsh (Java).
- [x] **`Conversión Visual`**: Lógica en C++ usando **tinygltf** para convertir `.frd` a glTF/GLB coloreado.

### 1.3 Tubería de Análisis Estructural
- [x] **Exportación de Inp Estructural**: Implementación inicial para datos nodales y elementales.
- [x] **Mapeo de Resultados Estructurales**: Conversión de resultados nodales en fuerzas de miembros (N, V, M) para diagramas (Implementado vía DatParser).

---

## 🧊 Fase 2: Análisis de Sólidos 3D - Editor de CAD y Malla (COMPLETADO)
*Objetivo: Permitir la creación y manipulación de geometría dentro de la aplicación.*

- [x] **Primitivas CAD**: Creación de Cubo, Cilindro, Esfera vía OCCT (Implementado vía OcctPrimitivesJNI).
- [x] **Motor Booleano**: Operaciones de Unión (Fuse), Corte e Intersección (Implementado vía OcctBooleanJNI).
- [x] **Controles de Malla**: Parámetros de refinamiento local y densidad de malla global (Integrado en MainActivity vía GmshRunner).
- [x] **Biblioteca de Materiales**: Gestión de propiedades de materiales predefinidos (MaterialDatabase.java + materials.json).

---

## 📐 Fase 3: Análisis Estructural - Editor Estructural (estilo SAP) (COMPLETADO)
*Objetivo: Implementar un editor estructural dedicado de alto rendimiento en 2D/3D.*

- [x] **Renderizador OpenGL ES Personalizado**: Dibujo de alto rendimiento de estructuras alámbricas, nodos y vigas (FrameRenderer.java).
- [x] **Herramientas de Edición**: Renderizado de cuadrícula y creación interactiva de nodos/vigas (Integrado en FrameRenderer).
- [x] **Gestión de Entidades**: Coordenadas de nodos y conectividad de elementos (StructuralModel.java).
- [x] **Motor de Diagramas**: Visualización de diagramas de Momento Flector, Fuerza Cortante y Axial (DiagramView.java + DatParser.java).

---

## 🚀 Fase 4: Integración Avanzada y Optimización (COMPLETED)
*Objetivo: Pulir, soporte multi-módulo y rendimiento.*

- [x] **Rendimiento**: Multihilo para el mallado y análisis de resultados (ExecutorService en MainActivity).
- [x] **Importador de .inp de Abaqus**: Compatibilidad de formato Abaqus de alta fidelidad (AbaqusInpImporter.java).
- [x] **Informes**: Generación automatizada de PDF con datos de simulación y tablas de resultados (ReportGenerator.java).
- [x] **Modelado Mixto**: Soporte para modelos que contienen tanto sólidos como elementos estructurales (Implementado en AnalysisModel.cpp).
- [x] **Picking Avanzado**: Ray-casting de alta fidelidad para la selección de caras/nodos (Implementado vía SceneViewBridge + OnHitListener).

---

## 🧪 Estrategia de Prueba y Validación

### Verificación de C++ Local Primero
Para asegurar la robustez del núcleo FEA, toda la nueva lógica de C++ (Analizadores, Escritores, Modelos) debe verificarse en el entorno Linux local antes de la integración con NDK:
1.  **Pruebas Unitarias**: Crear controladores `main.cpp` independientes para los componentes de NDK.
2.  **Validación del Solucionador**: Ejecutar el binario `ccx` local con los archivos `.inp` generados.
3.  **Verificación de Resultados**: Comparar las salidas locales con los resultados de ingeniería esperados.

---

## 🛠️ Resumen del Stack Tecnológico
| Categoría | Herramientas | Estado |
|---|---|---|
| **CAD/Geometría** | OpenCASCADE (OCCT) | Integrado (JNI) |
| **Mallado** | Gmsh | Integrado (Binario + Ejecutor) |
| **Solucionador** | CalculiX (ccx) | Integrado (vía Java/NDK) |
| **Visualización (3D)** | SceneView (Filament/Compose) | Integrado |
| **Visualización (Estruct.)** | OpenGL ES 3.0+ | Integrado |
| **Formatos** | glTF/GLB, STEP, IGES, BREP, INP, FRD | TOTALMENTE SOPORTADO |

---
*Última actualización: 1 de julio de 2026*
