package com.example.nocamera

import android.Manifest
import android.annotation.SuppressLint
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
    private var jpegReader: ImageReader? = null
    private var deviceOrientation: Int = 0

    private var lastPhotoTimestamp: Long = 0L
        private var lastPhotoBaseName: String? = null

    private fun saveJpeg(image: Image) {
        try {
            if (lastPhotoTimestamp == 0L) {
                lastPhotoTimestamp = System.currentTimeMillis()
            }
            val baseName = android.text.format.DateFormat.format("yyyyMMdd_HHmmss", lastPhotoTimestamp).toString()
            val filename = "IMG_${baseName}.RAW-01.COVER.jpg"
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/Camera")
                put(android.provider.MediaStore.Images.ImageColumns.DATE_TAKEN, lastPhotoTimestamp)
                put("group_id", lastPhotoTimestamp)
            }
            val resolver = applicationContext.contentResolver
            val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri == null) {
                toast("Error: Could not create MediaStore entry for JPEG")
                return
            }
            // Декодуємо JPEG у Bitmap
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bitmap == null) {
                toast("Error decoding JPEG bitmap")
                return
            }
            // Визначаємо sensorOrientation та lensFacing
            val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
            val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING) ?: CameraCharacteristics.LENS_FACING_BACK
            var finalRotation = if (lensFacing == CameraCharacteristics.LENS_FACING_FRONT) {
                var rot = (sensorOrientation + deviceOrientation) % 360
            // Збереження JPEG тимчасово вимкнено
            // try {
            //     val baseName = lastPhotoBaseName ?: android.text.format.DateFormat.format("yyyyMMdd_HHmmss", System.currentTimeMillis()).toString()
            //     val filename = "IMG_${baseName}.RAW-01.COVER.jpg"
            //     val values = android.content.ContentValues().apply {
            //         put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
            //         put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            //         put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/Camera")
            //         put(android.provider.MediaStore.Images.ImageColumns.DATE_TAKEN, lastPhotoTimestamp)
            //         put("group_id", lastPhotoTimestamp)
            //     }
            //     val resolver = applicationContext.contentResolver
            //     val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            //     if (uri == null) {
            //         toast("Error: Could not create MediaStore entry for JPEG")
            //         return
            //     }
            //     // Декодуємо JPEG у Bitmap
            //     val buffer = image.planes[0].buffer
            //     val bytes = ByteArray(buffer.remaining())
            //     buffer.get(bytes)
            //     val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            //     if (bitmap == null) {
            //         toast("Error decoding JPEG bitmap")
            //         return
            //     }
            //     // Визначаємо sensorOrientation та lensFacing
            //     val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
            //     val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING) ?: CameraCharacteristics.LENS_FACING_BACK
            //     var finalRotation = if (lensFacing == CameraCharacteristics.LENS_FACING_FRONT) {
            //         var rot = (sensorOrientation + deviceOrientation) % 360
            //         rot = (360 - rot) % 360
            //         rot
            //     } else {
            //         (sensorOrientation - deviceOrientation + 360) % 360
            //     }
            //     val matrix = android.graphics.Matrix()
            //     matrix.postRotate(finalRotation.toFloat())
            //     val rotatedBitmap = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            //     resolver.openOutputStream(uri)?.use { out ->
            //         rotatedBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out)
            //     }
            //     // Додаємо EXIF Orientation та камеру
            //     try {
            //         val exif = androidx.exifinterface.media.ExifInterface(resolver.openInputStream(uri)!!)
            //         // Orientation: 6 = Rotate 270 CW
            //         exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, "6")
            //         exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_MAKE, "Google")
            //         exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_MODEL, "Pixel 7 Pro")
            //         exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_SOFTWARE, "HDR+ 1.0.773153310zd")
            //         exif.saveAttributes()
            //     } catch (e: Exception) {
            //         e.printStackTrace()
            //     }
            //     val scanIntent = android.content.Intent(android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            //     scanIntent.data = uri
            //     sendBroadcast(scanIntent)
            //     toast("Saved JPEG to gallery: $filename (rotation: $finalRotation)")
            // } catch (e: Exception) {
            //     e.printStackTrace()
            //     toast("Error saving JPEG: ${e.message}")
            // }
    private var currentCrop: AndroidRect? = null

    private lateinit var characteristics: CameraCharacteristics
    private var lastCaptureResult: CaptureResult? = null

    private lateinit var captureButton: Button
    private var zoomSeekBar: android.widget.SeekBar? = null
    private var zoomText: android.widget.TextView? = null
    private var focusView: android.view.View? = null

    private fun takePicture() {
        if (cameraDevice == null || captureSession == null) return
        if (rawReader == null) {
            toast("RAW capture not supported на цій камері")
            return
        }

        try {
            val captureRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureRequestBuilder.addTarget(rawReader!!.surface)
            jpegReader?.surface?.let { captureRequestBuilder.addTarget(it) }
            currentCrop?.let { captureRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, it) }
            captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
            // Встановлюємо орієнтацію для JPEG
            captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, 0) // Можна замінити на deviceOrientation, якщо є
            captureSession!!.capture(captureRequestBuilder.build(),
                object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult
                    ) {
                        super.onCaptureCompleted(session, request, result)
                        synchronized(rawLock) {
                            if (pendingRawImage != null) {
                                val img = pendingRawImage
                                pendingRawImage = null
                                img?.use {
                                    saveDng(it, result)
                            // Generate baseName once per capture
                            lastPhotoTimestamp = System.currentTimeMillis()
                            lastPhotoBaseName = android.text.format.DateFormat.format("yyyyMMdd_HHmmss", lastPhotoTimestamp).toString()
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
        val cameraNames = mutableListOf<String>()
        val cameraIdList = mutableListOf<String>()
        for (id in cameraIds) {
            val chars = cameraManager.getCameraCharacteristics(id)
            val lensFacing = chars.get(CameraCharacteristics.LENS_FACING)
            val capabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            val physicalSizes = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
            val isTele = focalLengths != null && focalLengths.maxOrNull() != null && focalLengths.maxOrNull()!! > 6.0f
            val name = when {
                lensFacing == CameraCharacteristics.LENS_FACING_FRONT -> "Фронтальна"
                isTele -> "Телеоб'єктив"
                capabilities?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_ULTRA_HIGH_RESOLUTION_SENSOR) == true -> "Телефото"
                capabilities?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA) == true -> "Основна/Широка"
                else -> "Інша"
            }
            // Only add telephoto if detected
            if (name == "Телеоб'єктив" || name == "Телефото") {
                cameraNames.add(name)
                cameraIdList.add(id)
            } else if (name != "Телеоб'єктив" && name != "Телефото") {
                cameraNames.add(name)
                cameraIdList.add(id)
            }
        }
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Виберіть камеру")
        builder.setItems(cameraNames.toTypedArray()) { _, which ->
            currentCameraIndex = cameraIds.indexOf(cameraIdList[which])
            closeCamera()
            openCamera(cameraIdList[which])
        }
        builder.show()
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        // Orientation listener
        val orientationListener = object : android.view.OrientationEventListener(this) {
            override fun onOrientationChanged(orientation: Int) {
                deviceOrientation = when {
                    orientation in 45..134 -> 270
                    orientation in 135..224 -> 180
                    orientation in 225..314 -> 90
                    else -> 0
                }
            }
        }
        if (orientationListener.canDetectOrientation()) {
            orientationListener.enable()
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

    textureView = findViewById(R.id.textureView)
    textureView.setAspectRatio(3, 4)
        switchButton = findViewById(R.id.switchButton)
        selectCameraButton = findViewById(R.id.selectCameraButton)

    cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
    cameraIds = cameraManager.cameraIdList.toList()
    // Використовується тільки перша камера

        switchButton.setOnClickListener {
            switchCamera()
        }

        selectCameraButton.setOnClickListener {
            showCameraSelectionDialog()
        }

        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val factor = detector.scaleFactor
                val newZoom = (currentZoom * factor).coerceIn(1.0f, maxZoom)
                handleZoomSwitch(newZoom)
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
        focusView = findViewById(R.id.focusView)
        // style focusView: circular stroke
        focusView?.background = android.graphics.drawable.ShapeDrawable(android.graphics.drawable.shapes.OvalShape()).apply {
            paint.style = android.graphics.Paint.Style.STROKE
            paint.strokeWidth = 6f
            paint.color = android.graphics.Color.YELLOW
        }
        zoomSeekBar?.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val range = maxZoom - 1.0f
                    val z = 1.0f + (progress / 100.0f) * range
                    handleZoomSwitch(z.coerceIn(1.0f, maxZoom))
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

    @SuppressLint("SuspiciousIndentation")
    private fun createCameraSession() {
        if (cameraDevice == null) return

        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val rawSizes = map?.getOutputSizes(ImageFormat.RAW_SENSOR)
        val rawSize = rawSizes?.maxByOrNull { it.width * it.height } ?: Size(1920, 1080)

        rawReader?.close()
        jpegReader?.close()
        if (rawSizes != null && rawSizes.isNotEmpty()) {
            rawReader = ImageReader.newInstance(rawSize.width, rawSize.height, ImageFormat.RAW_SENSOR, 2)
            rawReader?.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                if (image == null) {
                    toast("ImageReader: RAW image is null")
                    return@setOnImageAvailableListener
                }
                synchronized(rawLock) {
                    if (lastCaptureResult != null) {
                        val result = lastCaptureResult
                        lastCaptureResult = null
                        image.use {
                            saveDng(it, result!!)
                        }
                    } else {
                        pendingRawImage?.close()
                        pendingRawImage = image
                    }
                }
            }, null)

            // JPEG ImageReader
            jpegReader = ImageReader.newInstance(rawSize.width, rawSize.height, ImageFormat.JPEG, 2)
            jpegReader?.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                if (image == null) {
                    toast("ImageReader: JPEG image is null")
                    return@setOnImageAvailableListener
                }
                image.use {
                    saveJpeg(it)
                }
            }, null)
        } else {
            rawReader = null
            jpegReader = null
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

            val outputs = if (rawReader != null && jpegReader != null) listOf(previewSurface, rawReader!!.surface, jpegReader!!.surface) else listOf(previewSurface)

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
            currentCrop = crop
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
            val half = (Math.min(viewW, viewH) * 0.08).toInt().coerceAtLeast(50)
            val left = (sensorX - half).coerceAtLeast(rect.left)
            val top = (sensorY - half).coerceAtLeast(rect.top)
            val right = (sensorX + half).coerceAtMost(rect.right)
            val bottom = (sensorY + half).coerceAtMost(rect.bottom)
            val meterRect = MeteringRectangle(left, top, right - left, bottom - top, MeteringRectangle.METERING_WEIGHT_MAX / 2)

            val maxAf = characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) ?: 0
            val maxAe = characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE) ?: 0

            // set regions if supported
            if (maxAf > 0) previewRequestBuilder?.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(meterRect))
            if (maxAe > 0) previewRequestBuilder?.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(meterRect))

            // trigger AF
            previewRequestBuilder?.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
            previewRequestBuilder?.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
            previewRequestBuilder?.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)
            captureSession?.capture(previewRequestBuilder!!.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                    // after AF completes, reset to continuous AF for preview
                    try {
                        previewRequestBuilder?.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
                        previewRequestBuilder?.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                        // clear regions after focusing to avoid permanent override
                        if (maxAf > 0) previewRequestBuilder?.set(CaptureRequest.CONTROL_AF_REGIONS, null)
                        if (maxAe > 0) previewRequestBuilder?.set(CaptureRequest.CONTROL_AE_REGIONS, null)
                        session.setRepeatingRequest(previewRequestBuilder!!.build(), null, null)
                        runOnUiThread { toast("Focus locked") }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }, null)
            runOnUiThread {
                toast("Focusing...")
            }
            // show focus indicator at tap (place relative to textureView)
            runOnUiThread {
                textureView.post {
                    val texLoc = IntArray(2)
                    textureView.getLocationOnScreen(texLoc)
                    val parentView = textureView.parent as android.view.View
                    val parentLoc = IntArray(2)
                    parentView.getLocationOnScreen(parentLoc)
                    val absoluteX = texLoc[0] + x
                    val absoluteY = texLoc[1] + y
                    val targetX = (absoluteX - parentLoc[0] - (focusView?.width ?: 0) / 2).toFloat()
                    val targetY = (absoluteY - parentLoc[1] - (focusView?.height ?: 0) / 2).toFloat()
                    focusView?.apply {
                        bringToFront()
                        this.x = targetX
                        this.y = targetY
                        visibility = android.view.View.VISIBLE
                        alpha = 1f
                        scaleX = 1f
                        scaleY = 1f
                        animate().scaleX(0.6f).scaleY(0.6f).alpha(0f).setDuration(800).withEndAction {
                            visibility = android.view.View.GONE
                        }.start()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveDng(image: Image, result: CaptureResult) {
        try {
            if (lastPhotoTimestamp == 0L) {
                lastPhotoTimestamp = System.currentTimeMillis()
            }
            val baseName = android.text.format.DateFormat.format("yyyyMMdd_HHmmss", lastPhotoTimestamp).toString()
            val filename = "IMG_${baseName}.RAW-02.ORIGINAL.dng"
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/x-adobe-dng")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/Camera")
                put(android.provider.MediaStore.Images.ImageColumns.ORIENTATION, 0)
                put(android.provider.MediaStore.Images.ImageColumns.DATE_TAKEN, lastPhotoTimestamp)
                put("group_id", lastPhotoTimestamp)
            }
            val resolver = applicationContext.contentResolver
            val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri == null) {
                toast("Error: Could not create MediaStore entry")
                return
            }
            resolver.openOutputStream(uri)?.use { out ->
                val creator = DngCreator(characteristics, result)
                // Orientation: 6 = Rotate 270 CW
                creator.setOrientation(6)
                creator.writeImage(out, image)
                creator.close()
            }
                        // ...existing code...
            toast("Saved RAW to gallery: $filename")
            lastPhotoTimestamp = 0L // Скидаємо після збереження пари
        } catch (e: Exception) {
            e.printStackTrace()
            toast("Error saving DNG: ${e.message}")
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
    // Зум — тільки цифровий
    private fun handleZoomSwitch(newZoom: Float) {
        currentZoom = newZoom
        zoomText?.text = String.format("x%.2f", currentZoom)
        applyZoom()
    }

    // Видалено фізичне перемикання
}
