package com.diamon.civil.ui

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import io.github.sceneview.Scene
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberCameraManipulator
import com.google.android.filament.LightManager
import dev.romainguy.kotlin.math.Float3

/**
 * Listener de eventos táctiles en el visor 3D.
 * Interfaz simplificada sin dependencia de tipos internos de SceneView.
 */
interface OnHitListener {
    fun onHit(info: Any?)
}

/**
 * Punto de entrada desde Java (SolidFragment) para inyectar el visor 3D
 * dentro de un ComposeView del layout XML.
 *
 * Si modelPath es null se muestra la escena vacía con iluminación lista.
 */
fun setSceneViewContent(composeView: ComposeView, modelPath: String?, listener: OnHitListener?) {
    composeView.setContent {
        SceneViewWrapper(modelPath, listener)
    }
}

@Composable
fun SceneViewWrapper(modelPath: String?, listener: OnHitListener?) {

    val engine      = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val cameraManipulator = rememberCameraManipulator()

    // Resolver ruta: relativas van como asset://, absolutas van tal cual
    val resolvedPath: String? = modelPath?.let { raw ->
        when {
            raw.isBlank() -> null
            !raw.startsWith("/") -> "asset://$raw"
            else -> raw
        }
    }

    // Controlar errores de carga para evitar crashes
    var loadError by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scene(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            cameraManipulator = cameraManipulator,
            onFrame = { /* frame callback disponible si se necesita */ },
        ) {
            // Luz direccional para que el modelo sea visible
            LightNode(
                type = LightManager.Type.DIRECTIONAL,
                position = Float3(0f, 5f, 5f),
                direction = Float3(0f, -1f, -1f),
            )

            // Carga del modelo sólo si hay una ruta válida
            if (resolvedPath != null && !loadError) {
                val modelInstance = rememberModelInstance(modelLoader, resolvedPath)
                modelInstance?.let { instance ->
                    ModelNode(
                        modelInstance = instance,
                        scaleToUnits = 1.0f,
                    )
                }
            }
            // Escena vacía con cámara e iluminación por defecto si no hay modelo
        }

        if (loadError) {
            Text(
                text = "⚠ No se pudo cargar el modelo 3D",
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
            )
        }
    }
}
