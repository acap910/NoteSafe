package com.example.myapplication // ⚠️ PASTIKAN PACKAGE NAME BETUL

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var db: NoteDatabase
    private lateinit var adapter: NoteAdapter
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = NoteDatabase.getInstance(this)
        val sharedPrefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)

        // Bind UI Nota
        val etTitle = findViewById<EditText>(R.id.etTitle)
        val etContent = findViewById<EditText>(R.id.etContent)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnScan = findViewById<FloatingActionButton>(R.id.btnScan)
        val rvNotes = findViewById<RecyclerView>(R.id.rvNotes)

        // Bind UI Profil (Navigation Tab)
        val homeLayout = findViewById<View>(R.id.homeLayout)
        val infoLayout = findViewById<View>(R.id.infoLayout)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)

        // Bind UI Info Pelajar
        val etProfileName = findViewById<EditText>(R.id.etProfileName)
        val etProfileID = findViewById<EditText>(R.id.etProfileID)
        val etProfileCourse = findViewById<EditText>(R.id.etProfileCourse)
        val btnUpdateProfile = findViewById<Button>(R.id.btnUpdateProfile)

        // Load Data Profil Tersimpan (Offline Personalization)
        etProfileName.setText(sharedPrefs.getString("name", ""))
        etProfileID.setText(sharedPrefs.getString("studentID", ""))
        etProfileCourse.setText(sharedPrefs.getString("course", "CSC661"))

        // Simpan Data Profil
        btnUpdateProfile.setOnClickListener {
            val editor = sharedPrefs.edit()
            editor.putString("name", etProfileName.text.toString())
            editor.putString("studentID", etProfileID.text.toString())
            editor.putString("course", etProfileCourse.text.toString())
            editor.apply()
            Toast.makeText(this, "Profil Dikemaskini!", Toast.LENGTH_SHORT).show()
        }

        // Setup RecyclerView & Navigation (Sama seperti kod sebelum ini)
        adapter = NoteAdapter(mutableListOf()) { note -> showNoteDetail(note) }
        rvNotes.layoutManager = LinearLayoutManager(this)
        rvNotes.adapter = adapter

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { homeLayout.visibility = View.VISIBLE; infoLayout.visibility = View.GONE; btnScan.show(); true }
                R.id.nav_info -> { homeLayout.visibility = View.GONE; infoLayout.visibility = View.VISIBLE; btnScan.hide(); true }
                else -> false
            }
        }

        lifecycleScope.launch { db.noteDao().getAllNotes().collect { list -> adapter.updateData(list) } }

        val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val bitmap = result.data?.extras?.get("data") as Bitmap
                recognizer.process(InputImage.fromBitmap(bitmap, 0)).addOnSuccessListener { etContent.setText(it.text) }
            }
        }

        btnScan.setOnClickListener { cameraLauncher.launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE)) }

        btnSave.setOnClickListener {
            val title = etTitle.text.toString()
            if (title.isNotEmpty()) {
                val newNote = Note(title = title, content = etContent.text.toString(), location = "Scan")
                lifecycleScope.launch(Dispatchers.IO) {
                    db.noteDao().insert(newNote)
                    withContext(Dispatchers.Main) {
                        etTitle.text.clear(); etContent.text.clear()
                        Toast.makeText(this@MainActivity, "Berjaya Simpan!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false
            override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) {
                lifecycleScope.launch(Dispatchers.IO) { db.noteDao().delete(adapter.getNoteAt(vh.adapterPosition)) }
            }
        }).attachToRecyclerView(rvNotes)
    }

    private fun showNoteDetail(note: Note) {
        MaterialAlertDialogBuilder(this).setTitle(note.title).setMessage(note.content).setPositiveButton("Tutup", null).show()
    }
}