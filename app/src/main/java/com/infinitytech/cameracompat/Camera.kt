@file:Suppress("DEPRECATION")

package com.infinitytech.cameracompat

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.Camera
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView

class Camera(context: Context, attrs: AttributeSet?) : SurfaceView(context, attrs), SurfaceHolder.Callback{

    private val cameraDevice: CameraHardwares = if (Build.VERSION.SDK_INT < 21) CameraOld(context) else CameraOld(context)

    public var size: CameraHardwares.Size
        get() = cameraDevice.size
        set(value) {
            cameraDevice.size = value
        }

    public var light: Boolean
        get() = cameraDevice.light
        set(value) {
            cameraDevice.light = value
        }

    fun open() {
        cameraDevice.open()
        size = cameraDevice.size
    }

    fun setPreview(surface: SurfaceView) {
        cameraDevice.setPreview(surface)
    }

    fun close() {
        cameraDevice.close()
    }

    fun takePreview(callback: (ByteArray, CameraHardwares.Size) -> Unit) {
        cameraDevice.takePreview(callback)
    }

    fun focus(after: (() -> Unit)? = null) {
        cameraDevice.focus(after)
    }

    init {
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder?) {
        cameraDevice.open()
        cameraDevice.setPreview(this)
    }

    override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
        // Do nothing
    }

    override fun surfaceDestroyed(holder: SurfaceHolder?) {
        cameraDevice.close()
    }

}

/**
 * CameraHardware is a abstract layer of Android System Camera Hardware, managing all of the
 * camera funtions through this layer.
 */
@Suppress("RedundantVisibilityModifier")
interface CameraHardwares {

    var cameraId: Int

    public var light: Boolean

    public var size: Size

    public fun open()
    public fun setPreview(surface: SurfaceView)
    public fun close()
    public fun takePreview(callback: (ByteArray, Size) -> Unit)
    public fun focus(after: (() -> Unit)? = null)

    public data class Size(var width: Int, var height: Int)
}


/**
 * Camera implemention under API 21
 */
@Suppress("RedundantVisibilityModifier")
internal class CameraOld(var context: Context) : CameraHardwares {
    private val TAG = "CameraOld"

    private var camera: Camera? = null
    override lateinit var size: CameraHardwares.Size

    public override var cameraId: Int = Camera.CameraInfo.CAMERA_FACING_BACK

    private var supportFlash = lazy {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
    }

    public override var light: Boolean = false
        get() = camera?.parameters?.flashMode == Camera.Parameters.FLASH_MODE_TORCH
        set(value) {
            Log.i(TAG, "CameraOld:light:set()")
            field = value
            if (supportFlash.value)
                if (value) {
                    Log.d(TAG, "\tFlash ON")
                    val params = camera?.parameters?.apply { flashMode = Camera.Parameters.FLASH_MODE_TORCH }
                    camera?.parameters = params
                } else {
                    Log.d(TAG, "\tFlash OFF")
                    val params = camera?.parameters?.apply { flashMode = Camera.Parameters.FLASH_MODE_OFF }
                    camera?.parameters = params
                }
        }

    override fun open() {
        Log.i(TAG, "CameraOld Open")
        Log.d(TAG, "CameraBack: ${Camera.CameraInfo.CAMERA_FACING_BACK}")
        camera = Camera.open(cameraId) ?: throw RuntimeException("Can't get System Camera")
        camera?.apply {
            Log.d(TAG, "CameraOld setparameters")

            val params = parameters
            params.previewFormat = ImageFormat.NV21

            if (params.supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE))
                params.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
            else
                params.focusMode = Camera.Parameters.FOCUS_MODE_AUTO
            val bestPreviewSize =
                    params.supportedPreviewSizes.find { it.width == 1920 } ?: params.previewSize
            size = CameraHardwares.Size(bestPreviewSize.width, bestPreviewSize.height)

            params.setPreviewSize(bestPreviewSize.width, bestPreviewSize.height)
            val bestPicSize =
                    params.supportedPictureSizes.find { it.width == 1920 } ?: params.pictureSize
            params.setPictureSize(bestPicSize.width, bestPicSize.height)
            Log.d(TAG, "CameraPreview: ${bestPreviewSize.width} x ${bestPreviewSize.height}" +
                    "CameraPicture: ${bestPicSize.width} x ${bestPicSize.height}")

            parameters = params
            cancelAutoFocus()
            setDisplayOrientation(90)
        }
    }

    override fun setPreview(surface: SurfaceView) {
        Log.i(TAG, "CameraOld:setPreview()")
        camera?.setPreviewDisplay(surface.holder)
        camera?.startPreview()
    }

    override fun close() {
        Log.i(TAG, "CameraOld:close()")
        camera?.stopPreview()
        light = false
        camera?.release()
    }

    override fun takePreview(callback: (ByteArray, CameraHardwares.Size) -> Unit) {
        Log.i(TAG, "CameraOld:takePreview()")
        camera?.setOneShotPreviewCallback { data, camera ->
            callback.invoke(data, size)
            camera.stopPreview()
        }
    }

    override fun focus(after: (() -> Unit)?) {
        camera?.autoFocus { success, _ ->
            if (success) after?.invoke()
        }
    }

}
