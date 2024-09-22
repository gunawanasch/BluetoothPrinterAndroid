package com.gunawan.bluetoothprinter.core

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme
import androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme
import androidx.security.crypto.EncryptedSharedPreferences.create
import androidx.security.crypto.MasterKeys

class PrinterConfig(internal var context: Context) {
    companion object {
        const val PRINTER_GLOBAL    = "printer_global"
        const val PRINTER_EPSON     = "printer_epson"
        const val PRINTER_NAME      = "printer_name"
        const val PRINTER_ADDRESS   = "printer_address"
        const val PRINTER_TYPE      = "printer_type"
    }

    internal var esp: EncryptedSharedPreferences = create(
        "printer_config",
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context,
        PrefKeyEncryptionScheme.AES256_SIV,
        PrefValueEncryptionScheme.AES256_GCM
    ) as EncryptedSharedPreferences

    fun saveInt(key: String, value: Int) {
        esp.edit().putInt(key, value).apply()
    }

    fun getInt(key: String): Int {
        return esp.getInt(key, 0)
    }

    fun saveString(key: String, value: String) {
        esp.edit().putString(key, value).apply()
    }

    fun getString(key: String): String? {
        return esp.getString(key, "")
    }

    fun saveBoolean(key: String, value: Boolean) {
        esp.edit().putBoolean(key, value).apply()
    }

    fun getBoolean(key: String): Boolean {
        return esp.getBoolean(key, false)
    }

    fun remove(string: String){
        esp.edit().remove(string).apply()
    }

    fun clear() {
        esp.edit().clear().apply()
    }

    fun getPrinterType(): String? {
        return esp.getString(PRINTER_TYPE, "") ?: ""
    }

    fun isPrinterGlobal(): Boolean {
        return getPrinterType().equals(PRINTER_GLOBAL, ignoreCase = true)
    }

    fun isPrinterEpson(): Boolean {
        return getPrinterType().equals(PRINTER_EPSON, ignoreCase = true)
    }

}