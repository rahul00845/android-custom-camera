package com.android.customcamera

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.MediaRecorder
import android.os.*
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.*
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.fragment_capture2.*
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Created by Rahul Raj
 */

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class VideoCapture2Fragment : Fragment() {

    private var mCameraDevice: CameraDevice? = null

    private var mPreviewSession: CameraCaptureSession? = null


    private var mPreviewSize: Size? = null

    private var mVideoSize: Size? = null

    private var mMediaRecorder: MediaRecorder? = null

    private var mIsRecordingVideo: Boolean = false

    private var mBackgroundThread: HandlerThread? = null

    private var mBackgroundHandler: Handler? = null

    private val mCameraOpenCloseLock = Semaphore(1)

    private var mSensorOrientation: Int? = null
    private var mNextVideoAbsolutePath: String? = null
    private var mPreviewBuilder: CaptureRequest.Builder? = null
    private var isRecordingDone = false
    private var sensorOrientation = 0
    private var videoWidth = 0
    private var videoHeight = 0
    private var cameraFront = false


    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_capture2, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        startCapturing.setOnClickListener {
            if(mIsRecordingVideo) {
                changeCameraFacing.visibility = View.VISIBLE
                startCapturing.setImageResource(R.drawable.ic_circle)
                stopRecordingVideo()
                startPreview()
            } else {
                changeCameraFacing.visibility = View.GONE
                startCapturing.setImageResource(R.drawable.ic_stop_circle)
                startRecordingVideo()
            }
        }
        changeCameraFacing.setOnClickListener {
            closeCamera()
            cameraFront = !cameraFront
            if(cameraFront) {
                initiateCameraPreview(CameraMetadata.LENS_FACING_FRONT)
            } else {
                initiateCameraPreview(CameraMetadata.LENS_FACING_BACK)
            }
        }
    }

    private fun initiateCameraPreview(cameraFacing: Int) {
        startBackgroundThread()
        if (texture.isAvailable) {
            openCamera(texture.width, texture.height, cameraFacing)
        } else {
            texture.surfaceTextureListener = mSurfaceTextureListener
        }
    }

    override fun onResume() {
        super.onResume()
        parentVieww.post {
            videoHeight = parentVieww.height
            videoWidth = parentVieww.width
        }
        initiateCameraPreview(CameraMetadata.LENS_FACING_BACK)
    }


    override fun onPause() {
        stopRecordingVideo()
        closeCamera()
        stopBackgroundThread()
        /*if (!isRecordingDone) {
            deleteFile()
        }*/
        super.onPause()
    }

    private val mSurfaceTextureListener = object : TextureView.SurfaceTextureListener {

        override fun onSurfaceTextureAvailable(
                surfaceTexture: SurfaceTexture,
                width: Int, height: Int
        ) {
            openCamera(width, height, CameraMetadata.LENS_FACING_BACK)
        }

        override fun onSurfaceTextureSizeChanged(
                surfaceTexture: SurfaceTexture,
                width: Int, height: Int
        ) {
            configureTransform(width, height)
        }

        override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
            return true
        }

        override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {}

    }


    private val mStateCallback = object : CameraDevice.StateCallback() {

        override fun onOpened(cameraDevice: CameraDevice) {
            mCameraDevice = cameraDevice
            startPreview()
            mCameraOpenCloseLock.release()
            if (null != texture) {
                configureTransform(texture.width, texture.height)
            }
        }

        override fun onDisconnected(cameraDevice: CameraDevice) {
            mCameraOpenCloseLock.release()
            cameraDevice.close()
            mCameraDevice = null
        }

        override fun onError(cameraDevice: CameraDevice, error: Int) {
            mCameraOpenCloseLock.release()
            cameraDevice.close()
            mCameraDevice = null
            activity?.finish()
        }

    }

    private fun chooseVideoSize(choices: Array<Size>): Size {
        try {
            val  displayRatio = videoHeight.toDouble()/videoWidth
            var previousDiff = 0.0
            var sizeChoice:Size? = null
            for (size in choices) {
                val sizeRatio = size.width.toDouble()/size.height
                val diff = abs(displayRatio-sizeRatio)
                if(diff < previousDiff && size.width <= 1080  && size.width > 800) {
                    sizeChoice = size
                }
                previousDiff = diff
                /*if (size.width == size.height * 4 / 3 && size.width <= 1080) {
                    return size
                }*/
            }
            return sizeChoice!!
        } catch (e: NullPointerException) {
            Log.e(TAG, "Couldn't find any suitable video size")
            return choices[choices.size - 1]
        }
    }

    private fun startBackgroundThread() {
        mBackgroundThread = HandlerThread("CameraBackground")
        mBackgroundThread!!.start()
        mBackgroundHandler = Handler(mBackgroundThread!!.looper)
    }

    private fun stopBackgroundThread() {
        mBackgroundThread?.quitSafely()
        try {
            mBackgroundThread?.join()
            mBackgroundThread = null
            mBackgroundHandler = null
        } catch (e: InterruptedException) {

        }

    }

    @SuppressLint("MissingPermission")
    private fun openCamera(width: Int, height: Int, cameraFacing: Int) {
        val manager = texture.context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            Log.d(TAG, "tryAcquire")
            if (!mCameraOpenCloseLock.tryAcquire(
                    2500L,
                            TimeUnit.MILLISECONDS
                    )
            ) {
                throw RuntimeException("Time out waiting to lock camera opening.")
            }
            var cameraId = ""

            for (camera in manager.cameraIdList) {
                val cam = manager.getCameraCharacteristics(camera)
                if (cam[CameraCharacteristics.LENS_FACING] == cameraFacing) {
                    cameraId = camera
                    break
                }
            }

            if (cameraId == "") {
                showToastAndFinish()
            }

            val characteristics = manager.getCameraCharacteristics(cameraId)
            val map = characteristics
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)
            if (map == null) {
                throw RuntimeException("Cannot get available preview/video sizes")
            }
            mVideoSize = chooseVideoSize(map.getOutputSizes(MediaRecorder::class.java))
            mPreviewSize = mVideoSize

            val orientation = resources.configuration.orientation
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                texture.setAspectRatio(mPreviewSize!!.width, mPreviewSize!!.height)
            } else {
                texture.setAspectRatio(mPreviewSize!!.height, mPreviewSize!!.width)
            }
            configureTransform(width, height)
            mMediaRecorder = MediaRecorder()
            manager.openCamera(cameraId, mStateCallback, null)
        } catch (e: Exception) {
            Log.e(TAG, e.localizedMessage)
            showToastAndFinish()
        }
    }

    private fun closeCamera() {
        try {
            mCameraOpenCloseLock.acquire()
            closePreviewSession()
            if (null != mCameraDevice) {
                mCameraDevice!!.close()
                mCameraDevice = null
            }
            if (null != mMediaRecorder) {
                mMediaRecorder!!.release()
                mMediaRecorder = null
            }
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera closing.")
        } catch (e: Exception) {

        } finally {
            mCameraOpenCloseLock.release()
        }
    }

    private fun startPreview() {
        if (null == mCameraDevice || !texture.isAvailable || null == mPreviewSize) {
            showToastAndFinish()
        }
        try {
            closePreviewSession()
            val surfaceTexture = texture.surfaceTexture!!
            surfaceTexture.setDefaultBufferSize(mPreviewSize!!.width, mPreviewSize!!.height)
            mPreviewBuilder = mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)

            val previewSurface = Surface(surfaceTexture)
            mPreviewBuilder!!.addTarget(previewSurface)

            mCameraDevice!!.createCaptureSession(listOf(previewSurface),
                    object : CameraCaptureSession.StateCallback() {

                        override fun onConfigured(session: CameraCaptureSession) {
                            mPreviewSession = session
                            updatePreview()
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            Toast.makeText(texture.context, "Failed", Toast.LENGTH_SHORT).show()
                        }
                    }, mBackgroundHandler
            )
        } catch (e: CameraAccessException) {
            showToastAndFinish()
        }

    }

    private fun showToastAndFinish() {
        Toast.makeText(activity, "Cannot access the camera.", Toast.LENGTH_SHORT).show()
        activity?.finish()
    }

    private fun updatePreview() {
        if (null == mCameraDevice) {
            return
        }
        try {
            setUpCaptureRequestBuilder(mPreviewBuilder!!)
            val thread = HandlerThread("CameraPreview")
            thread.start()
            mPreviewSession!!.setRepeatingRequest(
                    mPreviewBuilder!!.build(),
                    null,
                    mBackgroundHandler
            )
        } catch (e: CameraAccessException) {

        }

    }

    private fun setUpCaptureRequestBuilder(builder: CaptureRequest.Builder) {
        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
    }

    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        if (null == texture || null == mPreviewSize) {
            return
        }
        val rotation = activity?.windowManager?.defaultDisplay?.rotation
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect =
                RectF(0f, 0f, mPreviewSize!!.height.toFloat(), mPreviewSize!!.width.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale = Math.max(
                    viewHeight.toFloat() / mPreviewSize!!.height,
                    viewWidth.toFloat() / mPreviewSize!!.width
            )
            matrix.postScale(scale, scale, centerX, centerY)
            matrix.postRotate((90 * (rotation - 2)).toFloat(), centerX, centerY)
        }
        texture.setTransform(matrix)
    }

    @Throws(IOException::class)
    private fun setUpMediaRecorder() {
        mMediaRecorder?.let {
            it.setAudioSource(MediaRecorder.AudioSource.MIC)
            it.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            it.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mNextVideoAbsolutePath = getVideoFilePath(texture.context)
            it.setOutputFile(mNextVideoAbsolutePath)
            it.setVideoEncodingBitRate(3000000)
            it.setVideoFrameRate(30)
            it.setVideoSize(mPreviewSize!!.width, mPreviewSize!!.height)
            it.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            it.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            val rotation = activity?.windowManager?.defaultDisplay?.rotation!!
            when (mSensorOrientation) {
                SENSOR_ORIENTATION_DEFAULT_DEGREES -> it.setOrientationHint(
                        DEFAULT_ORIENTATIONS.get(
                                rotation
                        )
                )
                SENSOR_ORIENTATION_INVERSE_DEGREES -> it.setOrientationHint(
                        INVERSE_ORIENTATIONS.get(
                                rotation
                        )
                )
            }
            it.prepare()
        }
    }

    private fun deleteFile() {
        if (mNextVideoAbsolutePath != null) {
            val file = File(mNextVideoAbsolutePath)
            if (file.exists()) {
                file.delete()
            }
        }
    }

    private fun getVideoFilePath(context: Context): String {
        val dir = context.getExternalFilesDir(null);
        return ((if (dir == null) "" else dir.absolutePath + "/")
                +  System.currentTimeMillis() + ".mp4")
    }

    private fun startRecordingVideo() {
        if (null == mCameraDevice || !texture.isAvailable || null == mPreviewSize) {
            return
        }
        try {
            closePreviewSession()
            setUpMediaRecorder()
            val surfaceTexture = texture.surfaceTexture!!
            surfaceTexture.setDefaultBufferSize(mPreviewSize!!.width, mPreviewSize!!.height)
            mPreviewBuilder = mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            val surfaces = ArrayList<Surface>()
            val previewSurface = Surface(surfaceTexture)
            surfaces.add(previewSurface)
            mPreviewBuilder!!.addTarget(previewSurface)
            val recorderSurface = mMediaRecorder!!.surface
            surfaces.add(recorderSurface)
            mPreviewBuilder!!.addTarget(recorderSurface)
            mCameraDevice!!.createCaptureSession(
                    surfaces,
                    object : CameraCaptureSession.StateCallback() {

                        override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                            mPreviewSession = cameraCaptureSession
                            updatePreview()
                            activity?.runOnUiThread {
                                mIsRecordingVideo = true
                                mMediaRecorder!!.start()
                            }
                        }

                        override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {

                        }
                    },
                    mBackgroundHandler
            )
        } catch (e: Exception) {

        }
    }

    private fun closePreviewSession() {
        if (mPreviewSession != null) {
            mPreviewSession!!.close()
            mPreviewSession = null
        }
    }

    private fun stopRecordingVideo() {
        try {
            if (mIsRecordingVideo) {
                mIsRecordingVideo = false
                mMediaRecorder?.stop()
                mMediaRecorder?.reset()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {

        private val SENSOR_ORIENTATION_DEFAULT_DEGREES = 90
        private val SENSOR_ORIENTATION_INVERSE_DEGREES = 270
        private val DEFAULT_ORIENTATIONS = SparseIntArray()
        private val INVERSE_ORIENTATIONS = SparseIntArray()

        private val TAG = "VideoCapture2Fragment"

        init {
            DEFAULT_ORIENTATIONS.append(Surface.ROTATION_0, 90)
            DEFAULT_ORIENTATIONS.append(Surface.ROTATION_90, 0)
            DEFAULT_ORIENTATIONS.append(Surface.ROTATION_180, 270)
            DEFAULT_ORIENTATIONS.append(Surface.ROTATION_270, 180)
        }

        init {
            INVERSE_ORIENTATIONS.append(Surface.ROTATION_0, 270)
            INVERSE_ORIENTATIONS.append(Surface.ROTATION_90, 180)
            INVERSE_ORIENTATIONS.append(Surface.ROTATION_180, 90)
            INVERSE_ORIENTATIONS.append(Surface.ROTATION_270, 0)
        }

    }

}