# NOTA TÉCNICA: Nomenclatura de Librerías OCCT para Compatibilidad Windows/Android Studio

**PROBLEMA DETECTADO:**
Los binarios originales de OpenCASCADE (OCCT) en el entorno Linux (`fake_root`) terminan con un punto (ej: `libTKBRep.so.`). **Windows prohíbe archivos que terminan en punto**, lo que provocaba el error `invalid path` al intentar clonar o hacer checkout del proyecto en Android Studio sobre Windows.

**SOLUCIÓN APLICADA:**
Para permitir el desarrollo en Windows y Android Studio sin romper el motor de cálculo, se ha establecido la siguiente convención de renombrado:

1. **Archivo Físico (Repositorio):** `libTKXXX._so_.so`
   - Se reemplazó el punto final por el sufijo `._so_.so`.
   - Este nombre es 100% válido para sistemas de archivos NTFS (Windows) y ext4 (Android).
   - Se aplicó este cambio a las 47 librerías de OCCT tanto en `jniLibs` como en `fake_root_o_g`.

2. **Enlace Simbólico (Tiempo de Ejecución):** `libTKXXX.so.` -> `libTKXXX._so_.so`
   - El código en `AssetHelper.java` detecta el patrón `._so_.so`.
   - Crea un enlace simbólico en la memoria interna (`usr/lib/`) con el nombre **original con punto**.
   - Esto permite que `libgmsh.so` encuentre sus dependencias tal cual fueron compiladas originalmente.
