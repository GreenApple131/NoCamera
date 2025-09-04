package com.example.nocamera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.DngCreator
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.MeteringRectangle
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Bundle
import android.util.Size
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.Surface
import android.view.TextureView
import android.widget.Button
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import android.graphics.Rect as AndroidRect

class MainActivity : ComponentActivity() {

    private lateinit var textureView: AspectRatioTextureView
    private lateinit var switchButton: Button
    private lateinit var selectCameraButton: Button

    private lateinit var cameraManager: CameraManager
    private lateinit var cameraIds: List<String>
    private var currentCameraIndex = 0

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var rawReader: ImageReader? = null
    private var pendingRawImage: Image? = null
    private val rawLock = Any()
    private var previewRequestBuilder: CaptureRequest.Builder? = null

    // Zoom
    private var scaleGestureDetector: ScaleGestureDetector? = null
    private var currentZoom = 1.0f
    private var maxZoom = 1.0f
    private var activeArraySize: AndroidRect? = null

    private lateinit var characteristics: CameraCharacteristics
    private var lastCaptureResult: CaptureResult? = null

    private lateinit var captureButton: Button
    private var zoomSeekBar: android.widget.SeekBar? = null
    private var zoomText: android.widget.TextView? = null

    private fun takePicture() {
        if (cameraDevice == null || captureSession == null) return
        if (rawReader == null) {
            toast("RAW capture not supported на цій камері")
            return
        }

        try {
            val captureRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureRequestBuilder.addTarget(rawReader!!.surface)
            captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
            captureSession!!.capture(captureRequestBuilder.build(),
                object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult
                    ) {
                        super.onCaptureCompleted(session, request, result)
                        // Pair capture result with any pending image (handle race in either order)
                        synchronized(rawLock) {
                            if (pendingRawImage != null) {
                                val img = pendingRawImage
                                pendingRawImage = null
                                img?.use {
                                    saveDng(it, result)
                                }
                            } else {
                                lastCaptureResult = result
                                toast("Captured RAW frame, чекаю ImageReader...")
                            }
                        }
                    }
                },
                null
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
            toast("CameraAccessException: ${e.message}")
        }
    }

    private fun showCameraSelectionDialog() {
        val cameraNames = cameraIds.map { id ->
            val chars = cameraManager.getCameraCharacteristics(id)
            val lensFacing = chars.get(CameraCharacteristics.LENS_FACING)
            val capabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            when {
                lensFacing == CameraCharacteristics.LENS_FACING_FRONT -> "Фронтальна"
                capabilities?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_ULTRA_HIGH_RESOLUTION_SENSOR) == true -> "Телефото"
                capabilities?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA) == true -> "Основна/Широка"
                else -> "Інша"
            }
        }
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Виберіть камеру")
        builder.setItems(cameraNames.toTypedArray()) { _, which ->
            currentCameraIndex = which
            closeCamera()
            openCamera(cameraIds[which])
        }
        builder.show()
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

    textureView = findViewById(R.id.textureView)
    textureView.setAspectRatio(3, 4)
        switchButton = findViewById(R.id.switchButton)
        selectCameraButton = findViewById(R.id.selectCameraButton)

        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cameraIds = cameraManager.cameraIdList.toList()

        switchButton.setOnClickListener {
            switchCamera()
        }

        selectCameraButton.setOnClickListener {
            showCameraSelectionDialog()
        }

        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val factor = detector.scaleFactor
                currentZoom = (currentZoom * factor).coerceIn(1.0f, maxZoom)
                applyZoom()
                return true
            }
        })

        textureView.setOnTouchListener { v, event ->
            scaleGestureDetector?.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_UP) {
                val x = event.x.toInt()
                val y = event.y.toInt()
                triggerFocus(x, y)
            }
            true
        }

        zoomSeekBar = findViewById(R.id.zoomSeekBar)
        zoomText = findViewById(R.id.zoomText)
        zoomSeekBar?.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val range = maxZoom - 1.0f
                    val z = 1.0f + (progress / 100.0f) * range
                    currentZoom = z.coerceIn(1.0f, maxZoom)
                    zoomText?.text = String.format("x%.2f", currentZoom)
                    applyZoom()
                }
            }

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {
                openCamera(cameraIds[currentCameraIndex])
            }
            override fun onSurfaceTextureSizeChanged(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {}
            override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture): Boolean = true
            override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) {}
        }

        captureButton = findViewById(R.id.captureButton)
        captureButton.setOnClickListener {
            takePicture()
        }
    }

    private fun openCamera(cameraId: String) {
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1)
                return
            }
            characteristics = cameraManager.getCameraCharacteristics(cameraId)
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createCameraSession()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                }
            }, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun closeCamera() {
        try {
            captureSession?.close()
            captureSession = null

            cameraDevice?.close()
            cameraDevice = null

            rawReader?.close()
            rawReader = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun switchCamera() {
        if (cameraIds.isEmpty()) return
        currentCameraIndex = (currentCameraIndex + 1) % cameraIds.size
        val newCameraId = cameraIds[currentCameraIndex]
        closeCamera()
        openCamera(newCameraId)
    }

    private fun createCameraSession() {
        if (cameraDevice == null) return

        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val rawSizes = map?.getOutputSizes(ImageFormat.RAW_SENSOR)
        val rawSize = rawSizes?.maxByOrNull { it.width * it.height } ?: Size(1920, 1080)

        rawReader?.close()
        if (rawSizes != null && rawSizes.isNotEmpty()) {
            rawReader = ImageReader.newInstance(rawSize.width, rawSize.height, ImageFormat.RAW_SENSOR, 2)
            rawReader?.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                if (image == null) {
                    toast("ImageReader: RAW image is null")
                    return@setOnImageAvailableListener
                }
                // Try to pair image with capture result; if not available, buffer image
                synchronized(rawLock) {
                    if (lastCaptureResult != null) {
                        val result = lastCaptureResult
                        lastCaptureResult = null
                        image.use {
                            saveDng(it, result!!)
                        }
                    } else {
                        // buffer image until capture result arrives
                        pendingRawImage?.close()
                        pendingRawImage = image
                    }
                }
            }, null)
        } else {
            rawReader = null
        }

    val texture = textureView.surfaceTexture ?: return
    // choose a preview size from the camera's supported SurfaceTexture sizes (prefer 4:3 largest)
    val previewSizes = map?.getOutputSizes(android.graphics.SurfaceTexture::class.java)
    val previewSize = previewSizes?.filter { it.width * 4 == it.height * 3 }
        ?.maxByOrNull { it.width * it.height }
        ?: previewSizes?.maxByOrNull { it.width * it.height }
    if (previewSize != null) {
        texture.setDefaultBufferSize(previewSize.width, previewSize.height)
    }
    val previewSurface = Surface(texture)

        try {
            previewRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            previewRequestBuilder?.addTarget(previewSurface)

            // compute zoom range
            activeArraySize = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
            val maxDigitalZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1.0f
            maxZoom = maxDigitalZoom * 1.0f
            // enable zoom UI
            runOnUiThread {
                if (maxZoom > 1.0f) {
                    zoomSeekBar?.isEnabled = true
                    zoomSeekBar?.progress = 0
                    zoomText?.text = String.format("x%.2f", currentZoom)
                } else {
                    zoomSeekBar?.isEnabled = false
                }
            }

            val outputs = if (rawReader != null) listOf(previewSurface, rawReader!!.surface) else listOf(previewSurface)

            cameraDevice!!.createCaptureSession(
                outputs,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        try {
                            previewRequestBuilder?.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                            session.setRepeatingRequest(previewRequestBuilder!!.build(), null, null)
                        } catch (e: CameraAccessException) {
                            e.printStackTrace()
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        toast("Failed to configure camera session")
                    }
                },
                null
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun applyZoom() {
        try {
            val rect = activeArraySize ?: return
            val centerX = rect.centerX()
            val centerY = rect.centerY()
            val zoomFactor = currentZoom
            val halfWidth = (rect.width() / (2 * zoomFactor)).toInt()
            val halfHeight = (rect.height() / (2 * zoomFactor)).toInt()
            val crop = AndroidRect(centerX - halfWidth, centerY - halfHeight, centerX + halfWidth, centerY + halfHeight)
            previewRequestBuilder?.set(CaptureRequest.SCALER_CROP_REGION, crop)
            captureSession?.setRepeatingRequest(previewRequestBuilder!!.build(), null, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun triggerFocus(x: Int, y: Int) {
        try {
            val rect = activeArraySize ?: return
            // map view coordinates to sensor coordinates
            val viewW = textureView.width
            val viewH = textureView.height
            val sensorX = (x.toFloat() / viewW * rect.width() + rect.left).toInt()
            val sensorY = (y.toFloat() / viewH * rect.height() + rect.top).toInt()
            val half = 100
            val meterRect = MeteringRectangle(Math.max(sensorX - half, rect.left), Math.max(sensorY - half, rect.top), half * 2, half * 2, MeteringRectangle.METERING_WEIGHT_MAX - 1)

            previewRequestBuilder?.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(meterRect))
            previewRequestBuilder?.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(meterRect))
            previewRequestBuilder?.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
            previewRequestBuilder?.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)
            captureSession?.capture(previewRequestBuilder!!.build(), object : CameraCaptureSession.CaptureCallback() {}, null)
            // reset trigger
            previewRequestBuilder?.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
            captureSession?.setRepeatingRequest(previewRequestBuilder!!.build(), null, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveDng(image: Image, result: CaptureResult) {
        try {
            val filename = "photo_${System.currentTimeMillis()}.dng"
            // force orientation to 90 degrees (rotate right)
            val orientationDegrees = 90
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/x-adobe-dng")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/DCIM")
                put(android.provider.MediaStore.Images.ImageColumns.ORIENTATION, orientationDegrees)
            }
            val resolver = applicationContext.contentResolver
            val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { out ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        DngCreator(characteristics, result).use { creator ->
                            creator.writeImage(out, image)
                        }
                    }
                }
                toast("Saved RAW to gallery: $filename")
            } else {
                toast("Error: Could not create MediaStore entry")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            toast("Error saving DNG: ${e.message}")
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
