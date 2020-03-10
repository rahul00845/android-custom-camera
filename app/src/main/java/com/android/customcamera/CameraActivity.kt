package com.android.customcamera

import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment

class CameraActivity : BaseActivity() {

    private val CAMERA_STORAGE_PERMISION_REQ_CODE = 101
    private val permissionArray = arrayOf(
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.READ_EXTERNAL_STORAGE,
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
        android.Manifest.permission.RECORD_AUDIO
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        requestAppPermissions(
            permissionArray, CAMERA_STORAGE_PERMISION_REQ_CODE, this::onPermissionResult
        )
    }

    fun navigateToFragment(fragment: Fragment, arguments: Bundle?): Boolean {
        try {
            arguments?.let { fragment.arguments = it }
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, fragment)
                .commit()
        } catch (e: Exception) {
            return false
        }
        return true
    }

    private fun onPermissionResult(
        requestCode: Int,
        permissionStatus: Boolean
    ) {
        if (permissionStatus) {
            Log.d("PermissionStatus", "true")
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                navigateToFragment(VideoCapture2Fragment(), null)
            } else {
                navigateToFragment(VideoCapture1Fragment(), null)
            }
        } else {
            requestAppPermissions(
                permissionArray, CAMERA_STORAGE_PERMISION_REQ_CODE, this::onPermissionResult
            )
            Log.d("PermissionStatus", "false")
        }
    }

    override fun onResume() {
        super.onResume()
    }
}
