# Dependencias de Binarios (Structural Analysis FEA Advanced)

Este documento lista las dependencias y la matriz de renombrado entre la versión original y la versión de Android JNI para el motor CalculiX.

## Ejecutables (usr/bin/)
| Nombre Original | Nombre JNI (arm64-v8a) | Enlazado en app como |
|-----------------|------------------------|----------------------|
| `ccx` | `libccx.so` | `usr/bin/ccx` |

## Librerías Compartidas (usr/lib/)
| Nombre Original | Nombre JNI (arm64-v8a) | Enlazado en app como |
|-----------------|------------------------|----------------------|
| `libopenblas.so`| `libopenblas.so` | `usr/lib/libopenblas.so` |
| `libopenblas.so.0`| `libopenblas_so_0.so`| `usr/lib/libopenblas.so.0` |
| `libopenblasp-r0.3.33.dev.so`| `libopenblasp_r0_3_33_dev.so`| `usr/lib/libopenblasp-r0.3.33.dev.so` |
| `libgmsh.so.5.0`| `libgmsh.so.5.0` | `usr/lib/libgmsh.so.5.0` |
| `libTK*.so` | `libTK*.so` | `usr/lib/libTK*.so` (OCCT) |
| `libc++_shared.so`| `libc++_shared.so` | `usr/lib/libc++_shared.so` |
| `libgmp.so` | `libgmp.so` | `usr/lib/libgmp.so` |
| `libfreetype.so`| `libfreetype.so` | `usr/lib/libfreetype.so` |

## Estado de Dependencias Externas: **COMPLETO**
Todos los binarios requeridos por Gmsh y OCCT han sido integrados exitosamente en la arquitectura arm64-v8a:
- `libgmp.so`: Integrado y verificado.
- `libfreetype.so`: Integrado y verificado.
- `libc++_shared.so`: Integrado y verificado.

*Nota:* El binario `libccx.so` ha sido parcheado mediante `patchelf --remove-rpath` para garantizar que cargue las librerías desde el directorio nativo de la aplicación en Android, eliminando referencias al entorno de compilación (Termux).

*Nota:* Si CalculiX depende de otra librería del sistema (ej. `libm.so`, `libc.so`), estas son provistas por el sistema operativo Android. Las librerías estáticas (SPOOLES, ARPACK) han sido integradas directamente en el ejecutable durante la compilación.
