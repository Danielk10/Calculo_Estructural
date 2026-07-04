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

interface OnHitListener {
    fun onHit(hitResult: HitResult?)
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
    
    SceneView(
        modifier = Modifier.fillMaxSize(),
        engine = engine,
        onTouchEvent = { motionEvent, hitResult ->
            if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                listener?.onHit(hitResult)
            }
            true
        }
    ) {
        if (modelPath != null) {
            val modelInstance = rememberModelInstance(modelLoader, modelPath)
            modelInstance?.let {
                ModelNode(modelInstance = it)
            }
        } else {
            // Cubo por defecto si no hay modelo
            CubeNode(size = Float3(0.5f, 0.5f, 0.5f))
        }
    }
}
