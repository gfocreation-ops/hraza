package com.gfocreation.heartrateandzoneannounce.misc

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Minimal permission helper utilities.
 *
 * NOTE: uses the literal string for NEARBY_DEVICES to avoid compile-time mismatch
 * if the platform SDK used to compile the app does not expose Manifest.permission.NEARBY_DEVICES.
 */
object PermissionHelper {


    /**
     * Utility: check whether a permission string is declared in the app manifest.
     * This protects us from requiring runtime permissions that are not declared (which would be wrong).
     */
    private fun isPermissionDeclared(ctx: Context, permission: String): Boolean {
        return try {
            val pkgInfo = ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.GET_PERMISSIONS)
            val requested = pkgInfo.requestedPermissions
            requested?.contains(permission) == true
        } catch (_: Throwable) {
            false
        }
    }



//    fun missingTrainingPermissions(ctx: Context): Array<String> {
//        val missing = mutableListOf<String>()
//
//        requiredBlePermissions(ctx).forEach { perm ->
//            if (ContextCompat.checkSelfPermission(ctx, perm)
//                != PackageManager.PERMISSION_GRANTED
//            ) {
//                missing.add(perm)
//            }
//        }
//
//        return missing.toTypedArray()
//    }


    /**
     * Return the list of runtime permissions the app should request for BLE use,
     * depending on the platform level **and** only including permissions actually declared
     * in the app manifest.
     *
     * IMPORTANT: this function now requires a Context because it inspects the manifest.
     */
    fun requiredBlePermissions(ctx: Context): Array<String> {
        val perms = mutableListOf<String>()

//        // Only include NEARBY_DEVICES if it's declared in the manifest
//        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
//            val nearby = "android.permission.NEARBY_DEVICES"
//            if (isPermissionDeclared(ctx, nearby)) {
//                perms.add(nearby)
//            }
//        }

        // Runtime permissions for BLE scanning and connecting (Android 12+)
        // Include them only if declared in the manifest to avoid requesting permissions not declared.
        if (isPermissionDeclared(ctx, Manifest.permission.BLUETOOTH_SCAN)) {
            perms.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (isPermissionDeclared(ctx, Manifest.permission.BLUETOOTH_CONNECT)) {
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (isPermissionDeclared(ctx, Manifest.permission.BLUETOOTH_ADVERTISE)) {
            perms.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }

        // Legacy location permission for older devices (kept for compatibility) if declared.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            if (isPermissionDeclared(ctx, Manifest.permission.ACCESS_FINE_LOCATION)) {
                perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        return perms.toTypedArray()
    }

    /**
     * Check whether all BLE-related runtime permissions are granted.
     * This uses the same list as requiredBlePermissions(ctx) (so consistent).
     */
    fun hasAllBlePermissions(ctx: Context): Boolean {
        val req = requiredBlePermissions(ctx)
        if (req.isEmpty()) return true // nothing to request/verify
        return req.all { p ->
            ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun permissionsPermanentlyDenied(ctx: Context, activity: Activity): Boolean {
        if (!wasBlePermissionsAsked(ctx)) return false

        return requiredBlePermissions(ctx).any { perm ->
            ContextCompat.checkSelfPermission(ctx, perm) != PackageManager.PERMISSION_GRANTED &&
                    !activity.shouldShowRequestPermissionRationale(perm)
        }
    }


    fun requiredNotificationPermissions(ctx: Context): Array<String> {
        val perms = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (isPermissionDeclared(ctx, Manifest.permission.POST_NOTIFICATIONS)) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        return perms.toTypedArray()
    }

    fun hasNotificationPermission(ctx: Context): Boolean {
        val req = requiredNotificationPermissions(ctx)
        if (req.isEmpty()) return true
        return req.all { p ->
            ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
        }
    }


}
