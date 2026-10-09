package com.tyh24647.devtools

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.webkit.PermissionRequest
import android.webkit.WebView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/** Each website request needs consent, independently of the app's Android permissions. */
class WebMediaPermissions(private val activity: MainActivity) {
    private var pending: PermissionRequest? = null
    private var owner: WebView? = null
    private var dialog: AlertDialog? = null
    private var runtimePending = false
    private val launcher = activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        runtimePending = false
        finish()
    }
    fun request(view: WebView, request: PermissionRequest) {
        val resources = request.resources
        if (pending != null || runtimePending || resources.isEmpty() || resources.any { permission(it) == null } ||
            !(request.origin.scheme == "https" || (request.origin.scheme == "http" && request.origin.host in listOf("localhost", "127.0.0.1", "::1")))) {
            request.deny(); return
        }
        pending = request; owner = view
        val devices = resources.map { if (it == PermissionRequest.RESOURCE_VIDEO_CAPTURE) "camera" else "microphone" }.distinct().joinToString(" and ")
        dialog = AlertDialog.Builder(activity).setTitle("Website access")
            .setMessage("Allow ${request.origin} to use your $devices?")
            .setPositiveButton("Allow") { _, _ ->
                if (pending !== request) return@setPositiveButton
                val missing = resources.mapNotNull(::permission).filter { ContextCompat.checkSelfPermission(activity,it) != PackageManager.PERMISSION_GRANTED }
                if (missing.isEmpty()) finish() else { runtimePending = true; launcher.launch(missing.toTypedArray()) }
            }
            .setNegativeButton("Deny") { _, _ -> deny() }
            .setOnCancelListener { deny() }.show()
    }
    private fun finish() {
        val request = pending ?: return
        val allowed = request.resources.filter { permission(it)?.let { p -> ContextCompat.checkSelfPermission(activity,p) == PackageManager.PERMISSION_GRANTED } == true }
        pending = null; owner = null; dialog?.dismiss(); dialog = null
        if (allowed.isEmpty()) request.deny() else request.grant(allowed.toTypedArray())
    }
    private fun deny() { val request=pending;pending=null;owner=null;dialog?.dismiss();dialog=null;request?.deny() }
    fun canceled(request: PermissionRequest) { if (pending === request) { pending=null;owner=null;dialog?.dismiss();dialog=null } }
    fun leave(view: WebView) { if (owner === view) deny() }
    fun destroy() { deny() }
    companion object {
        fun permission(resource: String): String? = when(resource) {
            PermissionRequest.RESOURCE_VIDEO_CAPTURE -> Manifest.permission.CAMERA
            PermissionRequest.RESOURCE_AUDIO_CAPTURE -> Manifest.permission.RECORD_AUDIO
            else -> null
        }
    }
}
