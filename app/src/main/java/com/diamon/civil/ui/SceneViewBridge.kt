package com.diamon.civil.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import com.google.android.filament.LightManager
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.SceneView
import io.github.sceneview.collision.HitResult
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberOnGestureListener

interface OnHitListener {
    fun onHit(hitResult: HitResult?)
}

/**
 * Punto de entrada desde Java (MainActivity) para actualizar el modelo cargado en el SceneView.
 * Si modelPath es null se muestra la escena vacía con iluminación lista para recibir un modelo.
 */
fun setSceneViewContent(composeView: ComposeView, modelPath: String?, listener: OnHitListener?) {
    composeView.setContent {
        SceneViewWrapper(modelPath, listener)
    }
}

@Composable
fun SceneViewWrapper(modelPath: String?, listener: OnHitListener?) {

    val engine        = rememberEngine()
    val modelLoader   = rememberModelLoader(engine)

    // Camara con gestos orbit/zoom/pan habilitados
    val cameraManipulator = rememberCameraManipulator()

    // Convertir ruta absoluta a "asset://..." cuando viene de assets,
    // o dejarla como ruta de archivo cuando es un resultado de simulación.
    val resolvedPath: String? = modelPath?.let { raw ->
        when {
            // Ruta de asset relativa → prefijo que SceneView entiende
            !raw.startsWith("/") -> "asset://$raw"
            // Ruta absoluta de archivo (resultado FRD→GLB generado en runtime)
            else -> raw
        }
    }

    SceneView(
        modifier          = Modifier.fillMaxSize(),
        engine            = engine,
        modelLoader       = modelLoader,
        // ► FIX 2: cameraManipulator habilita zoom/orbita/pan
        cameraManipulator = cameraManipulator,
        // ► FIX 3: onGestureListener para notificar hits sin bloquear la cámara
        onGestureListener = rememberOnGestureListener(
            onSingleTapConfirmed = { motionEvent, node ->
                listener?.onHit(null)
            }
        ),
    ) {
        // ► FIX 5: Luz direccional para que el modelo sea visible
        LightNode(
            type      = LightManager.Type.DIRECTIONAL,
            position  = Float3(0f, 5f, 5f),
            direction = Float3(0f, -1f, -1f),
        )

        // ► FIX 4: Carga del modelo con autoCenter y scaleToUnits
        if (resolvedPath != null) {
            val modelInstance = rememberModelInstance(modelLoader, resolvedPath)
            modelInstance?.let { instance ->
                ModelNode(
                    modelInstance = instance,
                    // Escala el modelo para que quepa en ~1 unidad — siempre visible
                    scaleToUnits  = 1.0f,
                )
            }
        }
        // Si no hay modelo cargado la escena queda lista con cámara e iluminación
    }
}
