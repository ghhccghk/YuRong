package top.ghhccghk.yadea.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class AndroidPermissionChecker(private val activity: ComponentActivity) : PermissionChecker {

    private var pendingCallback: (() -> Unit)? = null

    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            pendingCallback?.invoke()
        }
        pendingCallback = null
    }

    override fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
    }

    override fun getMissingPermissions(permissions: List<String>): List<String> {
        return permissions.filter { permission ->
            isPermissionRequired(permission) && !hasPermission(permission)
        }
    }

    private fun isPermissionRequired(permission: String): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ||
            permission != Manifest.permission.POST_NOTIFICATIONS
    }

    override fun requestPermissions(permissions: List<String>) {
        val missing = getMissingPermissions(permissions)
        if (missing.isNotEmpty()) {
            launcher.launch(missing.toTypedArray())
        }
    }

    fun requestPermissionsWithCallback(permissions: List<String>, onGranted: () -> Unit) {
        val missing = getMissingPermissions(permissions)
        if (missing.isEmpty()) {
            onGranted()
        } else {
            pendingCallback = onGranted
            launcher.launch(missing.toTypedArray())
        }
    }
}
