package com.example.myapplication // ⚠️ PASTIKAN PACKAGE NAME BETUL

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class NoteAdapter(
    private var notes: MutableList<Note>,
    private val onItemClick: (Note) -> Unit // TAMBAH INI: Untuk kesan tekanan (click)
) : RecyclerView.Adapter<NoteAdapter.NoteViewHolder>() {

    class NoteViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvContent: TextView = view.findViewById(R.id.tvContent)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_note, parent, false)
        return NoteViewHolder(view)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        val currentNote = notes[position]
        holder.tvTitle.text = currentNote.title
        holder.tvContent.text = currentNote.content

        // TAMBAH INI: Apabila item ditekan, panggil fungsi onItemClick
        holder.itemView.setOnClickListener {
            onItemClick(currentNote)
        }
    }

    override fun getItemCount(): Int = notes.size

    fun getNoteAt(position: Int): Note = notes[position]

    fun updateData(newNotes: List<Note>) {
        notes = newNotes.toMutableList()
        notifyDataSetChanged()
    }
}