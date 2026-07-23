package com.diamon.civil.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.collision.HitResult
import io.github.sceneview.SceneView
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberModelInstance
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberOnGestureListener
interface OnHitListener {
    fun onHit(hitResult: Any?)
}

fun setSceneViewContent(composeView: ComposeView, modelPath: String?, listener: OnHitListener?) {
    composeView.setContent {
        SceneViewWrapper(modelPath, listener)
    }
}

@Composable
fun SceneViewWrapper(modelPath: String?, listener: OnHitListener?) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val cameraManipulator = rememberCameraManipulator()

    val resolvedPath: String? = modelPath?.let { raw ->
        when {
            raw.isBlank() -> null
            !raw.startsWith("/") -> raw // SceneView uses relative paths for assets automatically
            else -> "file://$raw" // SceneView needs file:// scheme for local files
        }
    }
    
    SceneView(
        modifier = Modifier.fillMaxSize().background(Color.DarkGray),
        engine = engine,
        modelLoader = modelLoader,
        cameraManipulator = cameraManipulator,
        onGestureListener = rememberOnGestureListener(
            onSingleTapConfirmed = { motionEvent, node ->
                listener?.onHit(null)
            }
        )
    ) {
        if (resolvedPath != null) {
            val modelInstance = rememberModelInstance(modelLoader, resolvedPath)
            modelInstance?.let {
                ModelNode(
                    modelInstance = it,
                    scaleToUnits = 1.0f
                )
            }
        } else {
            // Cubo por defecto si no hay modelo
            CubeNode(size = Float3(0.5f, 0.5f, 0.5f))
        }
    }
}
