package com.gunawan.bluetoothprinter.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.gunawan.bluetoothprinter.databinding.RowDeviceBinding
import com.gunawan.bluetoothprinter.model.Device

class DeviceAdapter() : RecyclerView.Adapter<DeviceAdapter.ViewHolder>() {
    private var listDevice: MutableList<Device> = arrayListOf()
    private var listener: OnCustomClickListener? = null

    interface OnCustomClickListener {
        fun onItemClicked(item: Device, position: Int, v: View)
    }

    fun setOnCustomClickListener(listener: OnCustomClickListener) {
        this.listener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(RowDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    fun addItems(newItems: ArrayList<Device>) {
        val pos_start: Int = listDevice.size
        listDevice.addAll(newItems)
        notifyItemRangeInserted(pos_start, newItems.size)
    }

    fun addItem(item: Device) {
        listDevice.add(item)
        notifyItemInserted(listDevice.size - 1)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun clearItems() {
        listDevice.clear()
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
        return listDevice.size
    }

    fun getItem(position: Int): Device {
        return listDevice[position]
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind()
        holder.binding.tvDevice.text  = listDevice[position].name

        holder.binding.llRoot.setOnClickListener {
            listener?.onItemClicked(listDevice[position], position, holder.itemView)
        }
    }

    class ViewHolder(val binding: RowDeviceBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind() {}
    }

}