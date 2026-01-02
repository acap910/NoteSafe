package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class NoteAdapter(
    private var notes: MutableList<Note>,
    private val onItemClick: (Note) -> Unit
) : RecyclerView.Adapter<NoteAdapter.NoteViewHolder>() {

    // SECURITY STATE: Default is locked (censored)
    private var isVaultUnlocked = false

    class NoteViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvContent: TextView = view.findViewById(R.id.tvContent)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_note, parent, false)
        return NoteViewHolder(view)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        val note = notes[position]
        holder.tvTitle.text = note.title

        // CENSOR LOGIC: Mask content if locked
        if (isVaultUnlocked) {
            holder.tvContent.text = note.content
            holder.tvContent.alpha = 1.0f
        } else {
            holder.tvContent.text = "••••••••••••••••••••" // Masked text
            holder.tvContent.alpha = 0.3f // Blurred effect
        }

        holder.itemView.setOnClickListener { onItemClick(note) }
    }

    override fun getItemCount() = notes.size

    fun getNoteAt(pos: Int) = notes[pos]

    fun updateData(newNotes: List<Note>) {
        notes = newNotes.toMutableList()
        notifyDataSetChanged()
    }

    // Toggle the security state
    fun setVaultState(unlocked: Boolean) {
        isVaultUnlocked = unlocked
        notifyDataSetChanged()
    }

    fun isUnlocked() = isVaultUnlocked
}