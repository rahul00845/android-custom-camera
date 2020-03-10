package com.android.customcamera

import android.hardware.Camera
import android.media.CamcorderProfile
import android.media.MediaRecorder
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.fragment_capture1.*
import java.io.File


/**
 * Created by Rahul Raj
 */

class VideoCapture1Fragment : Fragment() {

    private var mCamera: Camera? = null
    private var mMediaRecorder: MediaRecorder? = null
    private var mOutputFile: File? = null

    private var isRecording = false
    private val TAG = "Recorder"
    private lateinit var layoutView: View

    private var cameraPreview: CameraPreview? = null

    private var isRecordingDone = false
    private var cameraFront = false


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        layoutView = inflater.inflate(R.layout.fragment_capture1, container, false)
        return layoutView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        startCapturing.setOnClickListener {
            if (isRecording) {
                changeCameraFacing.visibility = View.VISIBLE
                startCapturing.setImageResource(R.drawable.ic_circle)
                stopRecordingVideo()
            } else {
                changeCameraFacing.visibility = View.GONE
                startCapturing.setImageResource(R.drawable.ic_stop_circle)
                if(cameraFront) {
                    startRecordingVideo(Camera.CameraInfo.CAMERA_FACING_FRONT)
                } else {
                    startRecordingVideo(Camera.CameraInfo.CAMERA_FACING_BACK)
                }
            }
        }
        changeCameraFacing.setOnClickListener {
            releaseCamera()
            cameraFront = !cameraFront
            if(cameraFront) {
                openCamera(Camera.CameraInfo.CAMERA_FACING_FRONT)
            } else {
                openCamera(Camera.CameraInfo.CAMERA_FACING_BACK)
            }
        }
    }

    private fun getDefaultCameraInstance(cameraFacing: Int): Camera {
        return Camera.open(cameraFacing)
    }

    private fun startRecordingVideo(camerFacing: Int) {
        if (!isRecording)
            MediaPrepareTask().execute(camerFacing, null, null)
    }

    private fun stopRecordingVideo() {
        if (isRecording) {
            try {
                mMediaRecorder?.stop()
            } catch (e: RuntimeException) {
                Log.e(
                    TAG,
                    "RuntimeException: stop() is called immediately after start()"
                )
            }
            releaseMediaRecorder()
            isRecording = false
        }
    }

    override fun onResume() {
        super.onResume()
        openCamera(Camera.CameraInfo.CAMERA_FACING_BACK)
    }

    override fun onPause() {
        super.onPause()
        stopRecordingVideo()
        /*if (!isRecordingDone) {
            deleteFile()
        }*/
        releaseCamera()
    }

    private fun releaseMediaRecorder() {
        try {
            mMediaRecorder?.let {
                it.reset()
                it.release()
                mMediaRecorder = null
                mCamera?.lock()
            }
        } catch (ignored: Exception) {
        }
    }

    private fun releaseCamera() {
        mCamera?.let {
            it.release()
            mCamera = null
        }
    }

    private fun openCamera(camerFacing: Int) {
        mCamera = getDefaultCameraInstance(camerFacing)
        mCamera?.setDisplayOrientation(90)
        cameraPreview = CameraPreview(layoutView.context, mCamera!!)
        texture.addView(cameraPreview)
    }

    private fun prepareVideoRecorder(camerFacing: Int): Boolean {
        mMediaRecorder = MediaRecorder()

        try {
            mCamera?.let { camera ->
                camera.unlock()
                mMediaRecorder?.run {
                    setCamera(camera)
                    setAudioSource(MediaRecorder.AudioSource.DEFAULT)
                    setVideoSource(MediaRecorder.VideoSource.CAMERA)
                    try {
                        setProfile(
                            CamcorderProfile.get(
                                camerFacing,
                                CamcorderProfile.QUALITY_1080P
                            )
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                        setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_480P))
                    }

                    mOutputFile = getOutputFile()
                    setOutputFile(mOutputFile!!.path)
                    setPreviewDisplay(cameraPreview?.holder?.surface)
                    setVideoEncodingBitRate(25000000)
                    return try {
                        prepare()
                        true
                    } catch (e: Exception) {
                        releaseMediaRecorder()
                        false
                    }
                }

            }
        } catch (e: Exception) {
            Log.e(TAG, e.localizedMessage)
        }
        return false

    }

    private fun deleteFile() {
        if (mOutputFile != null && mOutputFile!!.exists()) {
            mOutputFile!!.delete()
        }
    }

    private fun getOutputFile(): File {
        val dir = layoutView.context.getExternalFilesDir(null)
        val path = ((if (dir == null) "" else dir.absolutePath + "/")
                + "_" + System.currentTimeMillis() + ".mp4")
        return File(path)
    }

    internal inner class MediaPrepareTask : AsyncTask<Int, Void, Boolean>() {

        override fun doInBackground(vararg camerFacing: Int?): Boolean? {
            try {
                if (prepareVideoRecorder(camerFacing[0]!!)) {
                    mMediaRecorder?.start()

                    isRecording = true
                } else {
                    releaseMediaRecorder()
                    return false
                }
            } catch (e: Exception) {
                releaseMediaRecorder()
                return false
            }
            return true
        }

        override fun onPostExecute(result: Boolean?) {
            if (result == null || result == false) {
                activity?.finish()
            }
        }
    }

}
