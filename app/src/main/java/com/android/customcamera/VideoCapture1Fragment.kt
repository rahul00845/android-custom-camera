package com.android.customcamera

import android.hardware.Camera
import android.media.CamcorderProfile
import android.media.MediaRecorder
import android.os.AsyncTask
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.fragment_capture1.*
import java.io.File
import java.util.*


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

    }

    private fun getDefaultCameraInstance(): Camera {
        return Camera.open(Camera.CameraInfo.CAMERA_FACING_FRONT)
    }

    private fun startRecordingVideo() {
        if (!isRecording)
            MediaPrepareTask().execute(null, null, null)
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
                mOutputFile?.delete()
            }
            releaseMediaRecorder()
            isRecording = false
        }
        releaseCamera()
    }

    override fun onResume() {
        super.onResume()
        openCamera()
    }

    override fun onPause() {
        super.onPause()
        stopRecordingVideo()
        if (!isRecordingDone) {
            deleteFile()
        }
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

    private fun openCamera() {
        mCamera = getDefaultCameraInstance()
        mCamera?.setDisplayOrientation(90)
        cameraPreview = CameraPreview(layoutView.context, mCamera!!)
        texture.addView(cameraPreview)
    }

    private fun prepareVideoRecorder(): Boolean {
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
                                        Camera.CameraInfo.CAMERA_FACING_FRONT,
                                        CamcorderProfile.QUALITY_480P
                                )
                        )
                    } catch (e: Exception) {
                        setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_480P))
                    }

                    deleteFile()
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

    internal inner class MediaPrepareTask : AsyncTask<Void, Void, Boolean>() {

        override fun doInBackground(vararg voids: Void): Boolean? {
            try {
                if (prepareVideoRecorder()) {
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
