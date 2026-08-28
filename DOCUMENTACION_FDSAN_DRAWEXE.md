# Documentación del Bypass de Fdsan (File Descriptor Sanitizer)

## El Problema: Crash en DRAWEXE (Exit Code 134)
A partir de Android 11 (API 30), el sistema operativo activa por defecto y en modo `FATAL` una medida de seguridad llamada **fdsan** (File Descriptor Sanitizer).

El binario precompilado de OpenCASCADE (`DRAWEXE`) contiene ciertas rutinas (probablemente herencia de código C/C++ antiguo) en las que intenta cerrar un descriptor de archivo (`fd`) utilizando la función de bajo nivel `close()`, a pesar de que dicho archivo pertenece a un objeto de alto nivel `FILE*` (que debería cerrarse con `fclose()`). 

Al detectar esto, Bionic (la librería estándar de C de Android, `libc.so`) lo clasifica como un posible error de corrupción de memoria y aborta preventivamente la ejecución del programa emitiendo un `SIGABRT` con el siguiente error:
`fdsan: attempted to close file descriptor X, expected to be unowned, actually owned by FILE* Y`

## La Solución Actual: Wrapper de Inicialización con `LD_PRELOAD`
Dado que recompilar todo el ecosistema de OpenCASCADE es un proceso largo y muy complejo, se ha implementado una solución técnica oficial proporcionada por el NDK de Android.

1. **`fdsan_bypass.cpp`**: Se creó un código fuente mínimo en `app/src/main/cpp/fdsan_bypass.cpp` que utiliza la API pública del NDK de Android (`<android/fdsan.h>`) para invocar la función `android_fdsan_set_error_level(ANDROID_FDSAN_ERROR_LEVEL_DISABLED)`. 
2. **El truco del Constructor**: Este código está encapsulado en una función marcada con `__attribute__((constructor))`, lo que obliga al sistema a ejecutarla inmediatamente cuando la librería es cargada en memoria, incluso antes de que arranque la función `main()`.
3. **Inyección Dinámica**: Esta librería se compila como `libfdsan_bypass.so`. Luego, en `CalculixExecutor.java`, justo al momento de lanzar el proceso hijo (`DRAWEXE`), se define la variable de entorno `LD_PRELOAD` apuntando a esta nueva librería.

**¿Qué logramos con esto?** 
Obligamos a Android a cargar nuestro wrapper de configuración *antes* de iniciar `DRAWEXE`. Al cargarlo, se relaja la seguridad de `fdsan` de forma exclusiva y temporal para ese proceso, permitiendo que OpenCASCADE cierre sus archivos a su manera sin que el sistema lo mate.

## Cómo Solucionarlo Definitivamente (En OpenCASCADE)
Si en un futuro decides prescindir de la inyección por `LD_PRELOAD` y aplicar el parche directamente al código base de OpenCASCADE para que los binarios ya vengan "arreglados" de fábrica, debes seguir estos pasos en su código fuente antes de recompilar:

1. Localiza el archivo fuente principal donde se inicia `DRAWEXE` (por lo general, es `src/Draw/Draw_Main.cxx` o `src/Draw/Draw_Appli.cxx`).
2. Añade el include del sistema Android al inicio del archivo:
   ```cpp
   #if defined(__ANDROID__)
   #include <android/api-level.h>
   #if __ANDROID_API__ >= 29
   #include <android/fdsan.h>
   #endif
   #endif
   ```
3. Justo al inicio de la función principal `main()`, inyecta la directiva:
   ```cpp
   #if defined(__ANDROID__) && __ANDROID_API__ >= 29
   android_fdsan_set_error_level(ANDROID_FDSAN_ERROR_LEVEL_DISABLED);
   #endif
   ```
4. Recompila todo el ecosistema de OpenCASCADE usando tu toolchain habitual para Android NDK.
5. Reemplaza el `libDRAWEXE.so` actual en la carpeta `jniLibs` por el recién compilado. 

Una vez hecho esto, el binario de DRAWEXE ya desactivará el fdsan por sí solo, y podrás eliminar `fdsan_bypass.cpp`, su registro en `CMakeLists.txt` y la definición de la variable `LD_PRELOAD` en `CalculixExecutor.java`.
