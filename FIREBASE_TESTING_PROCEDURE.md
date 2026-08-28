# Guía de Pruebas de Ingeniería: Firebase Test Lab

Esta guía estandariza el procedimiento para ejecutar pruebas automatizadas en dispositivos reales o virtuales mediante Firebase Test Lab. Sigue estos pasos para evitar errores de configuración y asegurar resultados reproducibles.

## 1. Requisitos Previos
Asegúrate de que tu entorno local esté correctamente autenticado y configurado:

```bash
# 1. Autenticación
gcloud auth login

# 2. Configurar el proyecto por defecto
gcloud config set project pic-k150-programing-9d189
```

## 2. Preparación del Binario
Antes de ejecutar la prueba, siempre realiza una compilación limpia para evitar conflictos de caché:

```bash
./gradlew clean assembleDebug
```
El archivo APK resultante se encuentra típicamente en:
`/tmp/calculoestructural_build/outputs/apk/debug/app-debug.apk`

## 3. Selección de Dispositivo
No utilices nombres de modelos genéricos que puedan cambiar o ser inválidos. Consulta siempre la lista de modelos soportados:

```bash
gcloud firebase test android models list
```
*   **Recomendación:** Utiliza un modelo físico estable de la lista, ej: `Pixel2.arm` o un Samsung A53 (verificar ID en la lista).

## 4. Ejecución del Test
Ejecuta el test en **primer plano** para monitorear el progreso y capturar errores de inmediato. Reemplaza `MODEL_ID` y `OS_VERSION` por valores obtenidos en el paso 3.

```bash
gcloud firebase test android run \
  --app /tmp/calculoestructural_build/outputs/apk/debug/app-debug.apk \
  --device model=MODEL_ID,version=OS_VERSION \
  --project pic-k150-programing-9d189
```

## 5. Análisis de Resultados (Si el Test falla)
Si el test retorna `OUTCOME: Failed` y `Application crashed`:

1.  **Obtener la URL:** Firebase te proporcionará un enlace en la consola.
2.  **Identificar el bucket:** La URL del bucket GCS también se mostrará.
3.  **Descargar logs:**
    ```bash
    # Ejemplo de comando para descargar el log de crash
    gcloud storage cp gs://BUCKET_URL/.../data_app_crash_0_com_diamon_civil.txt ./crash_log.txt
    ```
## 6. Modo de Depuración Automática (Auto-Tester)
Se ha implementado una clase `AutoTester` que se ejecuta 2 segundos después de iniciar la app.
*   **Función:** Simula la entrada de datos en los módulos "Estructural" y "3D Sólido" y presiona los botones de cálculo.
*   **Diagnóstico:** Busca en los logs etiquetas como `AutoTester` y `FRD_CONVERTER`.
*   **Crashes:** Si la app se cierra, busca `FATAL EXCEPTION` y verifica si es un `UnsatisfiedLinkError` (problema de binarios) o un `SIGSEGV` (error nativo en el código C++).

```bash
# Filtrar logs por el AutoTester
adb logcat -s AutoTester:D *:E
```
