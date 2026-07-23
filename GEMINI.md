# Reglas para Gemini (Asistente IA)

## 📦 Reglas para hacer un Pre-lanzamiento (Pre-release)

Cuando se solicite hacer un pre-lanzamiento, **DEBES** seguir estrictamente estas instrucciones claras:

1. **Escribir un mensaje claro de lo que se hizo:** Debes redactar un resumen claro de todas las funcionalidades, arreglos o cambios que incluye esta nueva versión.
2. **Aumentar la versión:** Debes definir y proponer el nuevo número de versión incrementado (por ejemplo, `v0.2.0`).
3. **Preguntar al desarrollador:** Antes de lanzar, debes preguntarle al desarrollador si desea enviar la release usando el comando `gh release create`.
4. **Comando de creación de release:** Si el desarrollador te da el "Ok", debes ejecutar el siguiente comando (sustituyendo la versión correspondiente y las notas reales):

```bash
gh release create v0.1.0 /tmp/calculoestructural_build/outputs/apk/debug/app-debug.apk --title "Version Alfa 0.1.0" --notes "Mensaje claro de lo que se hizo" --prerelease
```

**⚠️ REGLAS CRÍTICAS DEL COMANDO:**
- La ruta del APK **siempre** es `/tmp/calculoestructural_build/outputs/apk/debug/app-debug.apk`.
- Debes incluir el flag `--prerelease`.
