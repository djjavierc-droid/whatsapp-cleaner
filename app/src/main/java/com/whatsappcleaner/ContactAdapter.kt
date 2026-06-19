package com.whatsappcleaner

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class ContactAdapter(
    private val onSelectionChanged: (selectedCount: Int) -> Unit
) : ListAdapter<Contact, ContactAdapter.ViewHolder>(DIFF_CALLBACK) {

    private val avatarColors = listOf(
        "#6366F1", "#EC4899", "#F59E0B",
        "#10B981", "#3B82F6", "#8B5CF6",
        "#EF4444", "#14B8A6", "#F97316"
    )

    private val selectedPhones = mutableSetOf<String>()

    fun selectAll() {
        selectedPhones.clear()
        currentList.forEach { selectedPhones.add(it.phone) }
        notifyDataSetChanged()
        onSelectionChanged(selectedPhones.size)
    }

    fun deselectAll() {
        selectedPhones.clear()
        notifyDataSetChanged()
        onSelectionChanged(0)
    }

    fun getSelectedContacts(): List<Contact> =
        currentList.filter { it.phone in selectedPhones }

    fun getSelectedCount(): Int = selectedPhones.size

    fun clearSelectionFor(phones: Set<String>) {
        selectedPhones.removeAll(phones)
        notifyDataSetChanged()
        onSelectionChanged(selectedPhones.size)
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val avatarFrame: FrameLayout = view.findViewById(R.id.avatarFrame)
        val tvInitial: TextView      = view.findViewById(R.id.tvInitial)
        val tvName: TextView         = view.findViewById(R.id.tvName)
        val tvPhone: TextView        = view.findViewById(R.id.tvPhone)
        val checkBox: CheckBox       = view.findViewById(R.id.checkBox)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contact, parent, false)
        val holder = ViewHolder(view)

        view.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_ID.toInt()) return@setOnClickListener
            val contact = getItem(pos)
            if (contact.phone in selectedPhones) {
                selectedPhones.remove(contact.phone)
            } else {
                selectedPhones.add(contact.phone)
            }
            notifyItemChanged(pos)
            onSelectionChanged(selectedPhones.size)
        }

        return holder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = getItem(position)

        holder.tvInitial.text  = contact.name.firstOrNull()?.uppercase() ?: "?"
        holder.tvName.text     = contact.name
        holder.tvPhone.text    = contact.phone
        holder.checkBox.isChecked = contact.phone in selectedPhones

        val colorHex = avatarColors[contact.name.length % avatarColors.size]
        val bg = holder.avatarFrame.background?.mutate()
        if (bg is GradientDrawable) {
            bg.setColor(Color.parseColor(colorHex))
            holder.avatarFrame.background = bg
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Contact>() {
            override fun areItemsTheSame(old: Contact, new: Contact) =
                old.phone == new.phone
            override fun areContentsTheSame(old: Contact, new: Contact) =
                old == new
        }
    }
}
