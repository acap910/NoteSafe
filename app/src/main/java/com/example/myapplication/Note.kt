package com.example.myapplication // Tukar ikut package anda
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes_table")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val content: String,
    val location: String // Menyimpan koordinat GPS (Context-Aware)
)