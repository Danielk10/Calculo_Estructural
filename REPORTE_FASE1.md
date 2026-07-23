# Reporte de Implementación: Fase 1 (Motor Civil)

## 1. Interpolación de Vigas B32 (¡Completado!)
He modificado `StructuralFragment.java` (método `modelToJson`). Ahora, por cada elemento de 2 nodos, el algoritmo calcula el punto medio exacto `(x, y, z)` y lo inserta dinámicamente en el array de nodos, asignando al elemento los 3 nodos correspondientes y cambiando su tipo a `B32`. Esto habilita las vigas cuadráticas para mayor precisión en la deformación.

## 2. El problema crítico con `*SECTION PRINT` (CalculiX 2.23)
Durante la validación local en el entorno Linux (ejecutando `ccx test_portico.inp`), descubrí un problema mayor en la estrategia de extracción de fuerzas de sección:

1. CalculiX 2.23 **rechaza** el comando `*SECTION PRINT, ELSET=EALL`. Devuelve un error fatal indicando que requiere estrictamente un parámetro `SURFACE` y un `NAME`.
2. Como las vigas 1D no tienen caras reales antes de expandirse, no se les puede asignar un `SURFACE` de forma directa en el archivo `.inp` para este comando.
3. El comando alternativo `*EL PRINT, ELSET=EALL, SECTION FORCES` (para imprimir en el `.dat`) **tampoco existe** en la versión 2.23 (arroja error "parameter not recognized: SECTIONFORCES").

### La Verdadera Solución
La única forma de que CalculiX escupa las Fuerzas Axiales, Cortantes y Momentos para vigas es usando:
```inp
*EL FILE, SECTION FORCES, OUTPUT=2D
S
```
¡He probado esto localmente y **funciona perfecto**! El solver termina exitosamente (en 0.039s) y mapea S11, S22, S33, etc., a Cortante 1, Cortante 2, Axial, Momento, etc.

### El Impacto en tu Código
Actualmente tienes `DatParser.java`, el cual busca en el archivo `.dat` el texto mágico *"section forces and moments for set BEAMS"*. Como vimos, esa salida es imposible de generar para vigas 1D en CalculiX 2.23. Las fuerzas se están yendo al archivo **`.frd`** en formato de bloques de resultados (código `-4 STRESS`).

**Decisión Necesaria para Continuar:**
Necesitamos desechar o refactorizar `DatParser.java` y crear un **`FrdParser`** (en Java o en C++) que lea el archivo `.frd`, busque el bloque `STRESS` y extraiga de ahí los momentos y cortantes usando el mapeo de `SECTION FORCES`.

He dejado el trabajo de la Fase 1 hasta aquí para no romper tu pipeline actual en Android. Avisame si estás de acuerdo con que cree el `FrdParser` para sustituir al `DatParser` y así completar la Fase 1.
