package com.example.honk.recorder

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.honk.R
import com.example.honk.data.CustomSound
import java.text.SimpleDateFormat
import java.util.*

class RecordingAdapter(
    private val onDeleteClick: (CustomSound) -> Unit
) : ListAdapter<CustomSound, RecordingAdapter.ViewHolder>(DIFF_CALLBACK) {

    class ViewHolder(
        view: View,
        private val onDeleteClick: (CustomSound) -> Unit
    ) : RecyclerView.ViewHolder(view) {
        private val nameText: TextView = view.findViewById(R.id.soundName)
        private val dateText: TextView = view.findViewById(R.id.recordDate)
        private val deleteButton: View = view.findViewById(R.id.deleteButton)
        private val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

        fun bind(sound: CustomSound) {
            nameText.text = sound.name
            dateText.text = dateFormat.format(Date(sound.createdAt))
            deleteButton.setOnClickListener { onDeleteClick(sound) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recording, parent, false)
        return ViewHolder(view, onDeleteClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val sound = getItem(position)
        holder.bind(sound)
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<CustomSound>() {
            override fun areItemsTheSame(oldItem: CustomSound, newItem: CustomSound): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: CustomSound, newItem: CustomSound): Boolean {
                return oldItem == newItem
            }
        }
    }
}
