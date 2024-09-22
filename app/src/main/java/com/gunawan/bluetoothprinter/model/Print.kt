package com.gunawan.bluetoothprinter.model

import android.graphics.Bitmap

data class Print(
    var logo: String?,
    var logoBitmap: Bitmap?,
    var merchantName: String?,
    var merchantAddress: String?,
    val transactionCode: String?,
    val trxDate: String?,
    val trxTime: String?,
    val total: String?,
    val paid: String?,
    val change: String?,
    val transactionDetails: List<PrintTransactionDetail>
)

data class PrintTransactionDetail(
    val name: String?,
    val price: String?
)