package com.gunawan.bluetoothprinter.core

import java.text.DecimalFormat

class CustomCurrency {

    fun currencyFormat(amount: Int): String? {
        return "Rp " + DecimalFormat("#,###").format(amount).replace(",", ".")
    }

}