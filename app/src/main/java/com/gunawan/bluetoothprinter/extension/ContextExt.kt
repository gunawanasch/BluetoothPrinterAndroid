package com.gunawan.bluetoothprinter.extension

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat

fun Context.isPermissionGranted(permission: String): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
            || ActivityCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}