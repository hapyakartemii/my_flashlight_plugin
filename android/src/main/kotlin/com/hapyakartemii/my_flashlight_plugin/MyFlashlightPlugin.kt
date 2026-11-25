package com.hapyakartemii.my_flashlight_plugin

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Turns the camera flash (torch) on and off through [CameraManager].
 *
 * Channel methods: `isAvailable`, `toggle`, `turnOff`.
 * Error codes: `NO_FLASH`, `PERMISSION_DENIED`, `CAMERA_ERROR`.
 *
 * Safety: before touching the camera it checks the hardware feature
 * ([PackageManager.FEATURE_CAMERA_FLASH]), the manifest permission and that a
 * camera with a flash unit exists, so a device without a flash gets an error
 * code instead of an exception.
 *
 * Threading: calls arrive on the Android main (UI) thread; camera work runs
 * on a background thread and the reply is posted back to the main thread.
 *
 * Lifecycle: the torch is switched off when the app goes to the background
 * and when the engine detaches, so it never stays on by accident.
 */
class MyFlashlightPlugin :
    FlutterPlugin,
    MethodCallHandler,
    ActivityAware,
    Application.ActivityLifecycleCallbacks {
    private lateinit var channel: MethodChannel
    private lateinit var context: Context
    private var cameraManager: CameraManager? = null
    private var activity: Activity? = null

    private val worker: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainThread = Handler(Looper.getMainLooper())

    /** Current torch state, kept in sync by [torchCallback]. */
    @Volatile
    private var isTorchOn = false

    /** Id of the camera whose flash we use; `null` if there is none. */
    private var flashCameraId: String? = null

    /** Also notices when another app or the quick settings tile changes it. */
    private val torchCallback =
        object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (cameraId == flashCameraId) isTorchOn = enabled
            }
        }

    // ---- FlutterPlugin: plugin added to / removed from the engine ----

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        context = binding.applicationContext
        cameraManager =
            context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        flashCameraId = findFlashCamera()
        cameraManager?.registerTorchCallback(torchCallback, mainThread)
        channel = MethodChannel(binding.binaryMessenger, CHANNEL)
        channel.setMethodCallHandler(this)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
        cameraManager?.unregisterTorchCallback(torchCallback)
        worker.execute { switchOffQuietly() }
        worker.shutdown()
    }

    // ---- MethodCallHandler: runs on the Android main thread ----

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "isAvailable" -> result.success(problem() == null)
            "toggle" -> runOnWorker(result) { setTorch(!isTorchOn) }
            "turnOff" -> runOnWorker(result) { setTorch(false) }
            else -> result.notImplemented()
        }
    }

    /** Runs camera work off the UI thread, replies on the main thread. */
    private fun runOnWorker(result: Result, action: () -> Boolean) {
        val error = problem()
        if (error != null) {
            result.error(error.first, error.second, null)
            return
        }
        worker.execute {
            try {
                val state = action()
                mainThread.post { result.success(state) }
            } catch (e: CameraAccessException) {
                mainThread.post { result.error(CAMERA_ERROR, e.message, null) }
            } catch (e: SecurityException) {
                mainThread.post {
                    result.error(PERMISSION_DENIED, e.message, null)
                }
            } catch (e: IllegalArgumentException) {
                mainThread.post { result.error(CAMERA_ERROR, e.message, null) }
            }
        }
    }

    // ---- Hardware checks ----

    /** Why the torch can't be used (code to message), or `null` if it can. */
    private fun problem(): Pair<String, String>? {
        val pm = context.packageManager
        if (!pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
            return NO_FLASH to "This device has no camera flash."
        }
        if (!hasFlashlightPermission()) {
            return PERMISSION_DENIED to
                "android.permission.FLASHLIGHT is missing in AndroidManifest.xml."
        }
        if (cameraManager == null || flashCameraId == null) {
            return NO_FLASH to "No camera with a flash unit was found."
        }
        return null
    }

    /** FLASHLIGHT is a normal permission: granted only if declared. */
    private fun hasFlashlightPermission(): Boolean =
        context.checkSelfPermission(FLASHLIGHT_PERMISSION) ==
            PackageManager.PERMISSION_GRANTED

    /** Back camera with a flash first, otherwise any camera with one. */
    private fun findFlashCamera(): String? {
        val manager = cameraManager ?: return null
        return try {
            val withFlash = manager.cameraIdList.filter { id ->
                manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            withFlash.firstOrNull { id ->
                manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_BACK
            } ?: withFlash.firstOrNull()
        } catch (e: CameraAccessException) {
            null
        }
    }

    /** Switches the torch and returns the new state. */
    private fun setTorch(on: Boolean): Boolean {
        val id = flashCameraId ?: return false
        val manager = cameraManager ?: return false
        if (isTorchOn == on) return on
        manager.setTorchMode(id, on)
        isTorchOn = on
        return on
    }

    /** Used by the lifecycle: errors here have nobody to report to. */
    private fun switchOffQuietly() {
        try {
            setTorch(false)
        } catch (e: Exception) {
            // The camera is busy or gone: nothing left to switch off.
        }
    }

    // ---- Lifecycle: torch off when the app goes to the background ----

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
        binding.activity.application.registerActivityLifecycleCallbacks(this)
    }

    override fun onDetachedFromActivity() {
        activity?.application?.unregisterActivityLifecycleCallbacks(this)
        activity = null
    }

    override fun onDetachedFromActivityForConfigChanges() =
        onDetachedFromActivity()

    override fun onReattachedToActivityForConfigChanges(
        binding: ActivityPluginBinding,
    ) = onAttachedToActivity(binding)

    override fun onActivityStopped(stopped: Activity) {
        if (stopped == activity && isTorchOn && !stopped.isChangingConfigurations) {
            worker.execute { switchOffQuietly() }
        }
    }

    override fun onActivityCreated(a: Activity, state: Bundle?) {}

    override fun onActivityStarted(a: Activity) {}

    override fun onActivityResumed(a: Activity) {}

    override fun onActivityPaused(a: Activity) {}

    override fun onActivitySaveInstanceState(a: Activity, state: Bundle) {}

    override fun onActivityDestroyed(a: Activity) {}

    private companion object {
        const val CHANNEL = "my_flashlight_plugin"
        const val FLASHLIGHT_PERMISSION = "android.permission.FLASHLIGHT"
        const val NO_FLASH = "NO_FLASH"
        const val PERMISSION_DENIED = "PERMISSION_DENIED"
        const val CAMERA_ERROR = "CAMERA_ERROR"
    }
}
