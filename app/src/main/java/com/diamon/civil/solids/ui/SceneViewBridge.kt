package com.diamon.civil.solids.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.SceneView
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberEnvironment
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberOnGestureListener
import io.github.sceneview.node.LightNode
import io.github.sceneview.loaders.ModelLoader
import com.google.android.filament.gltfio.FilamentInstance
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import java.io.File

interface OnHitListener {
    fun onHit(hitResult: Any?)
}

private val currentModelPath = mutableStateOf<String?>(null)
private val modelRevision = mutableIntStateOf(0)

fun setSceneViewContent(composeView: ComposeView, modelPath: String?, listener: OnHitListener?) {
    currentModelPath.value = modelPath
    modelRevision.intValue++

    if (composeView.tag != "scene_view_initialized") {
        composeView.tag = "scene_view_initialized"
        composeView.setContent {
            SceneViewWrapper(listener)
        }
    }
}

fun resetSceneViewCamera() {
    modelRevision.intValue++
}

private fun loadModelSafely(modelLoader: ModelLoader, rawPath: String?): FilamentInstance? {
    if (rawPath.isNullOrBlank()) return null
    return try {
        val cleanPath = rawPath.removePrefix("file://")
        val file = File(cleanPath)
        if (cleanPath.startsWith("/") && file.exists() && file.length() > 0) {
            modelLoader.createModelInstance(file)
        } else {
            modelLoader.createModelInstance(rawPath)
        }
    } catch (e: Throwable) {
        e.printStackTrace()
        null
    }
}

@Composable
fun SceneViewWrapper(listener: OnHitListener?) {
    val modelPath by currentModelPath
    val revision by modelRevision

    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    val environment = rememberEnvironment(environmentLoader)

    // Initial camera position positioned at a comfortable distance (3.5 units) to frame the model
    val cameraHome = Float3(0.0f, 0.6f, 3.5f)
    val cameraTarget = Float3(0.0f, 0.0f, 0.0f)

    val cameraManipulator = rememberCameraManipulator(
        orbitHomePosition = cameraHome,
        targetPosition = cameraTarget
    )
    val cameraNode = rememberCameraNode(engine) {
        position = cameraHome
        lookAt(cameraTarget)
    }

    SceneView(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E293B)),
        engine = engine,
        modelLoader = modelLoader,
        environmentLoader = environmentLoader,
        environment = environment,
        cameraManipulator = cameraManipulator,
        cameraNode = cameraNode
    ) {
        // Main Key Light (Directional Sunlight from top-right)
        LightNode(
            type = com.google.android.filament.LightManager.Type.DIRECTIONAL,
            apply = {
                color(1.0f, 1.0f, 1.0f)
                intensity(120_000.0f)
                direction(0.5f, -1.0f, -0.8f)
                castShadows(true)
            }
        )

        // Fill Light (Soft sky fill from opposite side so shadows are illuminated)
        LightNode(
            type = com.google.android.filament.LightManager.Type.DIRECTIONAL,
            apply = {
                color(0.85f, 0.9f, 1.0f)
                intensity(50_000.0f)
                direction(-0.5f, 1.0f, 0.8f)
                castShadows(false)
            }
        )

        key(modelPath, revision) {
            val modelInstance = remember(modelPath, revision) {
                loadModelSafely(modelLoader, modelPath)
            }

            if (modelInstance != null) {
                ModelNode(
                    modelInstance = modelInstance,
                    scaleToUnits = 1.0f,
                    centerOrigin = Float3(0.0f, 0.0f, 0.0f)
                )
            } else {
                CubeNode(
                    size = Float3(0.7f, 0.7f, 0.7f),
                    center = Float3(0.0f, 0.0f, 0.0f)
                )
            }
        }
    }
}
