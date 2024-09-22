package com.gunawan.bluetoothprinter.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.get
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gunawan.bluetoothprinter.BluetoothPrinterApp
import com.gunawan.bluetoothprinter.R
import com.gunawan.bluetoothprinter.core.PrinterConfig
import com.gunawan.bluetoothprinter.core.PrinterConfig.Companion.PRINTER_ADDRESS
import com.gunawan.bluetoothprinter.core.PrinterConfig.Companion.PRINTER_NAME
import com.gunawan.bluetoothprinter.databinding.ActivitySettingPrinterBinding
import com.gunawan.bluetoothprinter.extension.getSpannedText
import com.gunawan.bluetoothprinter.extension.isPermissionGranted
import com.gunawan.bluetoothprinter.model.Device

class SettingPrinterActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingPrinterBinding
    private var pairedAdapter: DeviceAdapter? = null
    private var availableAdapter: DeviceAdapter? = null
    private var mBluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothIntentLauncher: ActivityResultLauncher<Intent>? = null
    private var bluetoothPermissionLauncher: ActivityResultLauncher<Array<String>>? = null
    private val printerConfig by lazy {
        PrinterConfig(this)
    }
    private val mBTReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            try {
                val action = intent.action
                if (BluetoothDevice.ACTION_FOUND == action) {
                    val device =
                        intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    var found = false
                    for (i in 0 until availableAdapter!!.itemCount) {
                        val p = availableAdapter!!.getItem(i)
                        if (p.macAddress == device!!.address) {
                            found = true
                            break
                        }
                    }
                    if (!found) {
                        if (ActivityCompat.checkSelfPermission(this@SettingPrinterActivity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                            Toast.makeText(applicationContext, "Izin akses Bluetooth gagal", Toast.LENGTH_LONG).show()
                            return
                        }
                        availableAdapter!!.addItem(Device(device!!.name, device.address))
                    }
                } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED == action) {
                    val device =
                        intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    if (device!!.bondState == BluetoothDevice.BOND_BONDED) {
                        //disini disimpan sebagai printer terpilih
                        printerConfig.saveString(PRINTER_NAME, device.name)
                        printerConfig.saveString(PRINTER_ADDRESS, device.address)
                        displayPrinterTerpilih()
                        Toast.makeText(applicationContext, "Printer berhasil dipilih", Toast.LENGTH_LONG).show()
                        setListDevices()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingPrinterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.tbMain)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.tbMain.inflateMenu(R.menu.menu_setting_printer)

        bluetoothIntentLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
                if (result.resultCode == RESULT_OK) {
                    setListDevices()
                }
            }
        bluetoothPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){
            if (isPermissionGranted(Manifest.permission.BLUETOOTH_SCAN)
                && isPermissionGranted(Manifest.permission.BLUETOOTH_CONNECT)
            ) {
                bluetoothIntentLauncher!!.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            } else MaterialAlertDialogBuilder(BluetoothPrinterApp.context)
                .setTitle("Permintaan Izin Akses")
                .setMessage((getString(R.string.app_name) + " memerlukan izin akses <b>Nearby Devices (Perangkat Terdekat)</b> " +
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
        displayPrinterTerpilih()
        pairedAdapter = DeviceAdapter()
        availableAdapter = DeviceAdapter()
        pairedAdapter!!.setOnCustomClickListener(object : DeviceAdapter.OnCustomClickListener {
            override fun onItemClicked(item: Device, position: Int, v: View) {
                printerConfig.saveString(PRINTER_NAME, item.name)
                printerConfig.saveString(PRINTER_ADDRESS, item.macAddress)
                displayPrinterTerpilih()
                Toast.makeText(applicationContext, "Printer berhasil dipilih", Toast.LENGTH_LONG).show()
            }

        })
        availableAdapter!!.setOnCustomClickListener(object : DeviceAdapter.OnCustomClickListener {
            override fun onItemClicked(item: Device, position: Int, v: View) {
                if (enableBluetooth()) {
                    val device = item as Device
                    val mBtDevice = mBluetoothAdapter!!.getRemoteDevice(device.macAddress)
                    if (ActivityCompat.checkSelfPermission(
                            this@SettingPrinterActivity,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        Toast.makeText(applicationContext, "Izin akses Bluetooth gagal", Toast.LENGTH_LONG).show()
                        return
                    }
                    mBtDevice.createBond()
                }
            }

        })
        val itemDecoration = DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        binding.rvPairedDevices.addItemDecoration(itemDecoration)
        binding.rvAvailableDevices.addItemDecoration(itemDecoration)
        binding.rvPairedDevices.isNestedScrollingEnabled = false
        binding.rvAvailableDevices.isNestedScrollingEnabled = false
        binding.rvPairedDevices.layoutManager = LinearLayoutManager(this)
        binding.rvAvailableDevices.layoutManager = LinearLayoutManager(this)
        binding.rvPairedDevices.adapter = pairedAdapter
        binding.rvAvailableDevices.adapter = availableAdapter

        val intentFilter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        intentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        registerReceiver(mBTReceiver, intentFilter)
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (mBluetoothAdapter == null) {
            Toast.makeText(applicationContext, "Maaf perangkat Anda tidak mendukung Bluetooth", Toast.LENGTH_LONG).show()
            finish()
        } else if (enableBluetooth()) setListDevices()
    }

    private fun displayPrinterTerpilih() {
        binding.selectedPrinter.text =
            if (printerConfig.getString(PRINTER_NAME).isNullOrEmpty()) "-" else printerConfig.getString(PRINTER_NAME)
    }

    private fun enableBluetooth(): Boolean {
        var isEnabled = mBluetoothAdapter!!.isEnabled

        //jika bt sudah aktif tapi os versi s ke atas, cek permissionnya
        if (isEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            isEnabled = (isPermissionGranted(Manifest.permission.BLUETOOTH_SCAN)
                    && isPermissionGranted(Manifest.permission.BLUETOOTH_CONNECT))
        }
        if (!isEnabled) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                bluetoothIntentLauncher!!.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            } else bluetoothPermissionLauncher!!.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            )
        }
        return isEnabled
    }

    private fun setListDevices() {
        try {
            availableAdapter!!.clearItems()
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(applicationContext, "Izin akses Bluetooth gagal", Toast.LENGTH_LONG).show()
                return
            }
            if (mBluetoothAdapter!!.isDiscovering) mBluetoothAdapter!!.cancelDiscovery()
            mBluetoothAdapter!!.startDiscovery()
            val btDeviceList = mBluetoothAdapter!!.bondedDevices
            pairedAdapter!!.clearItems()
            if (btDeviceList.size > 0) {
                val items = ArrayList<Device>()
                for (device in btDeviceList) {
                    items.add(Device(device.name, device.address))
                }
                pairedAdapter!!.addItems(items)
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            Toast.makeText(applicationContext, ex.message ?: "Error menampilkan daftar perangkat", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_setting_printer, menu)
        for (i in 0 until menu.size()) {
            menu[i].icon?.setTint(getColor(R.color.white))
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            R.id.refresh_devices -> {
                if (enableBluetooth()) {
                    setListDevices()
                    Toast.makeText(applicationContext, "Memperbarui daftar perangkat", Toast.LENGTH_LONG).show()
                }
                return true
            }
            android.R.id.home -> {
                finish()
                return true
            }
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(mBTReceiver)
        try {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(applicationContext, "Izin akses Bluetooth gagal", Toast.LENGTH_LONG).show()
                return
            }
            if (mBluetoothAdapter != null) mBluetoothAdapter!!.disable()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

}