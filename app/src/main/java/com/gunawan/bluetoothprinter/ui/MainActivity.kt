package com.gunawan.bluetoothprinter.ui

import android.Manifest
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.lifecycleScope
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.DeviceConnection
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection
import com.dantsu.escposprinter.connection.usb.UsbConnection
import com.dantsu.escposprinter.connection.usb.UsbPrintersConnections
import com.dantsu.escposprinter.exceptions.EscPosBarcodeException
import com.dantsu.escposprinter.exceptions.EscPosConnectionException
import com.dantsu.escposprinter.textparser.PrinterTextParserImg
import com.epson.epos2.Epos2CallbackCode
import com.epson.epos2.printer.Printer
import com.epson.epos2.printer.PrinterStatusInfo
import com.epson.epos2.printer.ReceiveListener
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.gunawan.bluetoothprinter.R
import com.gunawan.bluetoothprinter.core.PrinterConfig.Companion.PRINTER_ADDRESS
import com.gunawan.bluetoothprinter.core.PrinterConfig.Companion.PRINTER_EPSON
import com.gunawan.bluetoothprinter.core.PrinterConfig.Companion.PRINTER_GLOBAL
import com.gunawan.bluetoothprinter.core.PrinterConfig.Companion.PRINTER_NAME
import com.gunawan.bluetoothprinter.core.PrinterConfig.Companion.PRINTER_TYPE
import com.gunawan.bluetoothprinter.core.CustomCurrency
import com.gunawan.bluetoothprinter.core.CustomImage
import com.gunawan.bluetoothprinter.core.PrinterConfig
import com.gunawan.bluetoothprinter.databinding.ActivityMainBinding
import com.gunawan.bluetoothprinter.extension.getSpannedText
import com.gunawan.bluetoothprinter.extension.isPermissionGranted
import com.gunawan.bluetoothprinter.model.Print
import com.gunawan.bluetoothprinter.model.PrintTransactionDetail
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MainActivity : ComponentActivity(), ReceiveListener {
    companion object {
        private const val USB_PERMISSION = "com.gunawan.printbluetooth.USB_PERSMISSION"
    }

    private lateinit var binding: ActivityMainBinding
    private var epsonPrinter: Printer? = null
    private var printUsb: Print? = null
    private var printObj: Print? = null
    private var mBluetoothAdapter: BluetoothAdapter? = null
    private var globalPrinter: EscPosPrinter? = null

    private val printerConfig by lazy {
        PrinterConfig(this)
    }

    private val bluetoothIntentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode === RESULT_OK) {
            binding.btnPrint.performClick()
        }
    }

    private val bluetoothPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (isPermissionGranted(Manifest.permission.BLUETOOTH_SCAN)
            && isPermissionGranted(Manifest.permission.BLUETOOTH_CONNECT)) {
            bluetoothIntentLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
        else MaterialAlertDialogBuilder(this)
            .setTitle("Permintaan Izin Akses")
            .setMessage(
                (getString(R.string.app_name) + " memerlukan izin akses <b>Nearby Devices (Perangkat Terdekat)</b> " +
                        "agar bisa melakukan print").getSpannedText()
            )
            .setNegativeButton("Tutup", null)
            .setPositiveButton("Pengaturan") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            }
            .show()
    }

    private fun initGlobalPrinter(deviceConnection: DeviceConnection) {
        releaseAllPrinterConnection()
        globalPrinter = EscPosPrinter(deviceConnection, 203, 50f, 32)
    }

    private fun enableBluetooth(): Boolean {
        return if (mBluetoothAdapter == null) {
            Toast.makeText(applicationContext, "Maaf perangkat Anda tidak mendukung Bluetooth", Toast.LENGTH_LONG).show()
            false
        } else {
            var isEnabled: Boolean = mBluetoothAdapter!!.isEnabled

            //jika bt sudah aktif tapi os versi s ke atas, cek permissionnya
            if (isEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                isEnabled = isPermissionGranted(Manifest.permission.BLUETOOTH_SCAN)
                        && isPermissionGranted(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (!isEnabled) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    bluetoothIntentLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                } else bluetoothPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                    )
                )
            }
            isEnabled
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val listTransactionDetail: MutableList<PrintTransactionDetail> = arrayListOf()
        listTransactionDetail.add(PrintTransactionDetail("Nasi Goreng Mawut", CustomCurrency().currencyFormat(15000)))
        listTransactionDetail.add(PrintTransactionDetail("Nasi Goreng Thailand", CustomCurrency().currencyFormat(20000)))

        val imageUrl = "https://storage.googleapis.com/gweb-uniblog-publish-prod/images/logo_Google_FullColor_3x_830x271px.original.png"

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        with(binding) {
            rgPrinter.clearCheck()
            rbGlobal.isChecked = printerConfig.isPrinterGlobal()
            rbEpson.isChecked = printerConfig.isPrinterEpson()

            rbGlobal.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) printerConfig.saveString(PRINTER_TYPE, PRINTER_GLOBAL)
                else printerConfig.saveString(PRINTER_TYPE, PRINTER_EPSON)
            }

            tvPrinterName.text = printerConfig.getString(PRINTER_NAME)

            btnChoose.setOnClickListener {
                startActivity(Intent(this@MainActivity, SettingPrinterActivity::class.java))
            }

            btnPrint.setOnClickListener {
                if (printerConfig.getString(PRINTER_ADDRESS).isNullOrEmpty()) Toast.makeText(applicationContext, "Silakan pilih printer", Toast.LENGTH_LONG).show()
                else if (enableBluetooth()) {
                    CoroutineScope(Dispatchers.IO).launch {
                        printObj = Print(
                            imageUrl,
                            CustomImage().getImageBitmap(imageUrl, this@MainActivity),
                            "Nasi Goreng Sedap",
                            "Jl. Trunojoyo 2 No.10, Kec. Sukolilo, Surabaya, Jawa Timur",
                            "INV-1234",
                            "2020-10-15",
                            "17:15",
                            CustomCurrency().currencyFormat(35000),
                            CustomCurrency().currencyFormat(40000),
                            CustomCurrency().currencyFormat(5000),
                            listTransactionDetail
                        )
                        if(binding.rbGlobal.isChecked) {
                            val btConnection = BluetoothConnection(
                                mBluetoothAdapter!!.getRemoteDevice(
                                    printerConfig.getString(PRINTER_ADDRESS)
                                )
                            )
                            printTicket(printObj, btConnection)
                        }
                        else {
                            printTicketEpson(printObj)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.tvPrinterName.text = printerConfig.getString(PRINTER_NAME)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(applicationContext, "Izin akses Bluetooth gagal", Toast.LENGTH_LONG).show()
                return
            }
            mBluetoothAdapter?.disable()
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }

        releaseAllPrinterConnection()
    }

    private fun releaseAllPrinterConnection(){
        try {
            globalPrinter?.disconnectPrinter()
        }
        catch (e: Exception){
            e.printStackTrace()
        }
        releaseEpsonPrinterConnection()
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
//            setProgressIndicator(false)
            if (USB_PERMISSION == action) {
                synchronized(this) {
                    val usbManager = getSystemService<UsbManager>()
                    val usbDevice = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (usbDevice != null && usbManager != null) {
                            printTicket(printUsb, UsbConnection(usbManager, usbDevice))
                        }
                    }
                }
            }
        }

    }

    private fun printOnUSBDevice(print: Print?, ticketBundle: Boolean) {
        printUsb = print
        val usbConnection = UsbPrintersConnections.selectFirstConnected(this@MainActivity)
        val usbManager = this.getSystemService<UsbManager>()
        if (usbConnection == null) {
            binding.tvPrinterName.text = "-"
//            setProgressIndicator(false)
        } else if (usbManager == null) {
//            setProgressIndicator(false)
        } else {
            val pendingIntent = PendingIntent.getBroadcast(
                this, 0, Intent(USB_PERMISSION),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            )
            val filter = IntentFilter(USB_PERMISSION)
            registerReceiver(usbReceiver, filter)
            usbManager.requestPermission(usbConnection.device, pendingIntent)
        }
    }

    private fun initEpsonPrinter(){
        releaseAllPrinterConnection()

        epsonPrinter = Printer(
            Printer.TM_M30,
            Printer.MODEL_ANK,
            this
        )
        epsonPrinter?.setReceiveEventListener(this)
    }

    private fun printTicketEpson(print: Print?) {
//        setProgressIndicator(true)
        lifecycleScope.launch(Dispatchers.IO) {
            initEpsonPrinter()

            /*

                Layout tiket:

                1.          <Logo>
                2.       Merchant Name
                3.        Merchant Address
                4.-------------------------------
                5.No Transaksi        00000000000
                6.Tanggal             28 Feb 2020
                7.Jam                       09:24
                8._______________________________
                9.Nama Produk 1         Rp 10.000
                10.Nama Produk 2        Rp 15.000
                11.------------------------------
                12.Total                Rp 35.000
                13.Dibayar              Rp 40.000
                14.Kembali               Rp 5.000
                15.------------------------------
                16.      Terima Kasih
                17.  Atas Kunjungan Anda
                18.-------------------------------
                19.        Scan Here
                20.          Qrcode

            */

            try {
                val textData: StringBuilder = StringBuilder()
                epsonPrinter?.addTextAlign(Printer.ALIGN_CENTER)
                print?.logoBitmap?.let { logoBitmap ->
                    epsonPrinter?.addImage(
                        logoBitmap, 0, 0,
                        logoBitmap.width,
                        logoBitmap.height,
                        Printer.COLOR_1,
                        Printer.MODE_MONO,
                        Printer.HALFTONE_DITHER,
                        Printer.PARAM_DEFAULT.toDouble(),
                        Printer.COMPRESS_AUTO
                    )
                }
                textData.append(print?.merchantName + "\n")
                textData.append(print?.merchantAddress + "\n")
                epsonPrinter?.addTextSize(1, 1)
                epsonPrinter?.addText(textData.toString())

                textData.append("------------------------------\n")

                textData.append("No. Transaksi    " + print?.transactionCode + "\n")
                textData.append("Tanggal")
                val trxDate = print?.trxDate ?: ""
                val spaceCountDate = 23 - trxDate.length
                for (i in 1..spaceCountDate) {
                    textData.append(" ")
                }
                textData.append(trxDate)
                textData.append("\n")

                textData.append("Jam")
                val trxTime = print?.trxTime ?: ""
                val spaceCountTime = 27 - trxTime.length
                for (i in 1..spaceCountTime) {
                    textData.append(" ")
                }
                textData.append(trxTime)

                textData.append("------------------------------\n")

                print?.transactionDetails?.let { details ->
                    for (detail in details) {
                        val productName = detail.name ?: ""
                        textData.append(productName)
                        val spaceCount = 27 - productName.length - detail.price!!.length
                        for (i in 1..spaceCount) {
                            textData.append(" ")
                        }
                        textData.append(detail.price + "\n")
                    }
                }

                textData.append("------------------------------\n")

                textData.append("Total")
                val totalPrice = print?.total ?: ""
                val spaceCountTotalPrice = 27 - totalPrice.length
                for (i in 1..spaceCountTotalPrice) {
                    textData.append(" ")
                }
                textData.append(totalPrice)

                textData.append("Dibayar")
                val paidPrice = print?.paid ?: ""
                val spaceCountPaidPrice = 27 - paidPrice.length
                for (i in 1..spaceCountPaidPrice) {
                    textData.append(" ")
                }
                textData.append(paidPrice)

                textData.append("Kembali")
                val changePrice = print?.change ?: ""
                val spaceCountChangePrice = 27 - changePrice.length
                for (i in 1..spaceCountChangePrice) {
                    textData.append(" ")
                }
                textData.append(changePrice)

                textData.append("------------------------------\n")

                textData.append("\n")
                textData.append("Terima Kasih\n")
                textData.append("Atas Kunjungan Anda\n")
                textData.append("------------------------------\n")
                textData.append("          Scan Here!          \n")
                epsonPrinter?.addTextSize(1, 1)
                epsonPrinter?.addText(textData.toString())

                val qrCode = generateQrCode(print?.transactionCode ?: "---")
                epsonPrinter?.addTextAlign(Printer.ALIGN_CENTER)
                epsonPrinter?.addImage(
                    qrCode, 0, 0,
                    qrCode.width,
                    qrCode.height,
                    Printer.COLOR_1,
                    Printer.MODE_MONO,
                    Printer.HALFTONE_DITHER,
                    Printer.PARAM_DEFAULT.toDouble(),
                    Printer.COMPRESS_AUTO
                )

                epsonPrinter?.addFeedLine(2)
                epsonPrinter?.addCut(Printer.CUT_FEED)
                textData.clear()
            } catch (e: java.lang.Exception) {
                epsonPrinter?.clearCommandBuffer()
                e.printStackTrace()
            }

            val errorMsg = epsonPrintData()
            withContext(Dispatchers.Main) {
//                setProgressIndicator(false)
                errorMsg?.let {
                    Toast.makeText(applicationContext, "Error cetak struk", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun printTicket(print: Print?, connection: DeviceConnection) {
        var errorMsg: String? = null
//        setProgressIndicator(true)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                initGlobalPrinter(connection)

                /*

                    Layout tiket:

                    1.          <Logo>
                    2.       Merchant Name
                    3.        Merchant Address
                    4.-------------------------------
                    5.No Transaksi        00000000000
                    6.Tanggal             28 Feb 2020
                    7.Jam                       09:24
                    8._______________________________
                    9.Nama Produk 1         Rp 10.000
                    10.Nama Produk 2        Rp 15.000
                    11.------------------------------
                    12.Total                Rp 35.000
                    13.Dibayar              Rp 40.000
                    14.Kembali               Rp 5.000
                    15.------------------------------
                    16.      Terima Kasih
                    17.  Atas Kunjungan Anda
                    18.-------------------------------
                    19.        Scan Here
                    20.          Qrcode


                */

                val textData: StringBuilder = StringBuilder()
                textData.append(
                    "[C]<img>" + PrinterTextParserImg.bitmapToHexadecimalString(
                        globalPrinter,
                        print?.logoBitmap
                    ) + "</img>\n\n" +
                            "[C]${print?.merchantName}\n" +
                            "[C]${print?.merchantAddress}\n" +
                            "[C]-------------------------------\n" +
                            "[L]No Transaksi[R]${print?.transactionCode}\n" +
                            "[L]Tanggal[R]${print?.trxDate}\n" +
                            "[L]Jam[R]${print?.trxTime}\n"
                )

                textData.append("[C]-------------------------------\n")

                print?.transactionDetails?.let { details ->
                    for (detail in details) {
                        textData.append("[L]${detail.name}[R]${detail.price}\n")
                    }
                }

                textData.append("[C]-------------------------------\n")

                textData.append(
                    "[L]Total[R]${print?.total}\n" +
                            "[L]Dibayar[R]${print?.paid}\n" +
                            "[L]Kembali[R]${print?.change}\n"
                )

                textData.append("[C]-------------------------------\n")

                textData.append(
                    "[C]Terima Kasih\n" +
                            "[C]Atas Kunjungan Anda\n" +
                            "[C]-------------------------------\n\n" +
                            "[C]Scan Here!\n\n" +
                            "[C]<qrcode size='20'>${print?.transactionCode}</qrcode>\n" +
                            "[C]"
                )

                globalPrinter!!.printFormattedTextAndCut(textData.toString())
            } catch (e: EscPosConnectionException) {
                errorMsg = e.toString()+"\nTerjadi kesalahan pada koneksi bluetooth. Pastikan printer bluetooth sudah tersambung dengan device"
            } catch (e: EscPosBarcodeException) {
                errorMsg = e.toString()+"\nTerjadi kesalahan saat mencetak QR Code"
            } catch (e: Exception) {
                errorMsg = e.toString()+"\nMohon maaf, terjadi kesalahan saat mencetak tiket."
            }
            withContext(Dispatchers.Main) {
//                setProgressIndicator(false)
                if(errorMsg == null) Toast.makeText(applicationContext, "Berhasil cetak struk", Toast.LENGTH_LONG).show()
                else Toast.makeText(applicationContext, "Error cetak struk", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun epsonPrintData(): String? {
        var errorMsg: String? = null
        try {
            epsonPrinter?.connect(
                printerConfig.getString(PRINTER_ADDRESS),
                Printer.PARAM_DEFAULT
            )

            epsonPrinter?.sendData(Printer.PARAM_DEFAULT)
        }
        catch (e: Exception){
            e.printStackTrace()
            errorMsg = "Error cetak tiket epson\n\n$e"
        }

        return errorMsg
    }

    override fun onPtrReceive(printerObj: Printer?, code: Int, status: PrinterStatusInfo?, printJobId: String?) {
        runOnUiThread {
            if (code == Epos2CallbackCode.CODE_SUCCESS) {
                Toast.makeText(applicationContext, "Printing Success", Toast.LENGTH_LONG).show()
            } else {
                status?.let {
                    Toast.makeText(applicationContext, "Printing failed: $it", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun releaseEpsonPrinterConnection() {
        try {
            epsonPrinter?.clearCommandBuffer()
            epsonPrinter?.setReceiveEventListener(null)
            epsonPrinter?.disconnect()
            epsonPrinter = null
        }
        catch(e: Exception){
            e.printStackTrace()
        }
    }

    private fun makeErrorMessage(status: PrinterStatusInfo): String {
        var msg = ""
        if (status.online == Printer.FALSE) {
            msg += getString(R.string.handlingmsg_err_offline)
        }
        if (status.connection == Printer.FALSE) {
            msg += getString(R.string.handlingmsg_err_no_response)
        }
        if (status.coverOpen == Printer.TRUE) {
            msg += getString(R.string.handlingmsg_err_cover_open)
        }
        if (status.paper == Printer.PAPER_EMPTY) {
            msg += getString(R.string.handlingmsg_err_receipt_end)
        }
        if (status.paperFeed == Printer.TRUE || status.panelSwitch == Printer.SWITCH_ON) {
            msg += getString(R.string.handlingmsg_err_paper_feed)
        }
        if (status.errorStatus == Printer.MECHANICAL_ERR || status.errorStatus == Printer.AUTOCUTTER_ERR) {
            msg += getString(R.string.handlingmsg_err_autocutter)
            msg += getString(R.string.handlingmsg_err_need_recover)
        }
        if (status.errorStatus == Printer.UNRECOVER_ERR) {
            msg += getString(R.string.handlingmsg_err_unrecover)
        }
        if (status.errorStatus == Printer.AUTORECOVER_ERR) {
            if (status.autoRecoverError == Printer.HEAD_OVERHEAT) {
                msg += getString(R.string.handlingmsg_err_overheat)
                msg += getString(R.string.handlingmsg_err_head)
            }
            if (status.autoRecoverError == Printer.MOTOR_OVERHEAT) {
                msg += getString(R.string.handlingmsg_err_overheat)
                msg += getString(R.string.handlingmsg_err_motor)
            }
            if (status.autoRecoverError == Printer.BATTERY_OVERHEAT) {
                msg += getString(R.string.handlingmsg_err_overheat)
                msg += getString(R.string.handlingmsg_err_battery)
            }
            if (status.autoRecoverError == Printer.WRONG_PAPER) {
                msg += getString(R.string.handlingmsg_err_wrong_paper)
            }
        }
        if (status.batteryLevel == Printer.BATTERY_LEVEL_0) {
            msg += getString(R.string.handlingmsg_err_battery_real_end)
        }
        if (status.removalWaiting == Printer.REMOVAL_WAIT_PAPER) {
            msg += getString(R.string.handlingmsg_err_wait_removal)
        }
        if (status.unrecoverError == Printer.HIGH_VOLTAGE_ERR ||
            status.unrecoverError == Printer.LOW_VOLTAGE_ERR
        ) {
            msg += getString(R.string.handlingmsg_err_voltage)
        }
        return msg
    }

    private fun generateQrCode(data: String): Bitmap {
        val width = 178

        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(data, BarcodeFormat.QR_CODE, width, width)
        val bitmap = Bitmap.createBitmap(width, width, Bitmap.Config.RGB_565)
        val color = ContextCompat.getColor(this, R.color.black)
        for (x in 0 until width) {
            for (y in 0 until width) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) color else Color.WHITE)
            }
        }
        return bitmap
    }

}