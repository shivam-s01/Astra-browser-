package com.astra.browser.privacy.permissions

import android.webkit.PermissionRequest
import com.astra.browser.data.local.dao.SitePermissionDao
import com.astra.browser.data.local.entity.SitePermissionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

enum class PermissionState { ALLOW, ASK, BLOCK }
enum class PermissionType { CAMERA, MICROPHONE, LOCATION, NOTIFICATIONS, POPUPS, COOKIES, JAVASCRIPT, AUTOPLAY, CLIPBOARD }

/**
 * Bridges real android.webkit.PermissionRequest callbacks (camera/mic access
 * requested by page JS) to the user's stored per-site decisions. A pending
 * request that has no stored decision is surfaced to the UI layer via
 * [pendingRequest] for the user to approve — it is never auto-granted.
 */
@Singleton
class PermissionManager @Inject constructor(
    private val sitePermissionDao: SitePermissionDao
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    var onPermissionNeeded: ((origin: String, resources: Array<String>, request: PermissionRequest) -> Unit)? = null

    fun handleWebPermissionRequest(request: PermissionRequest) {
        val origin = request.origin.toString()
        scope.launch {
            val cameraDecision = sitePermissionDao.getPermission(origin, PermissionType.CAMERA.name)
            val micDecision = sitePermissionDao.getPermission(origin, PermissionType.MICROPHONE.name)

            val needsCamera = request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
            val needsMic = request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)

            val cameraBlocked = needsCamera && cameraDecision?.state == PermissionState.BLOCK.name
            val micBlocked = needsMic && micDecision?.state == PermissionState.BLOCK.name

            when {
                cameraBlocked || micBlocked -> request.deny()
                (!needsCamera || cameraDecision?.state == PermissionState.ALLOW.name) &&
                (!needsMic || micDecision?.state == PermissionState.ALLOW.name) -> {
                    request.grant(request.resources)
                }
                else -> onPermissionNeeded?.invoke(origin, request.resources, request)
            }
        }
    }

    suspend fun setPermission(origin: String, type: PermissionType, state: PermissionState) {
        sitePermissionDao.upsert(
            SitePermissionEntity(origin = origin, permissionType = type.name, state = state.name)
        )
    }

    suspend fun resetSitePermissions(origin: String) {
        sitePermissionDao.resetForOrigin(origin)
    }

    fun observeForOrigin(origin: String) = sitePermissionDao.observeForOrigin(origin)
}
