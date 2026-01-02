package com.example.myapplication // ⚠️ ENSURE THIS MATCHES YOUR ACTUAL PACKAGE NAME

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
import com.google.android.material.appbar.MaterialToolbar
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

    // Initialize Mobile Vision without API (OCR)
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Initialize Database & SharedPreferences
        db = NoteDatabase.getInstance(this)
        val prefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)

        // 2. Bind UI Components (Modern Theme)
        val topAppBar = findViewById<MaterialToolbar>(R.id.topAppBar)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        val btnScan = findViewById<FloatingActionButton>(R.id.btnScan) // Corrected Type
        val btnSave = findViewById<Button>(R.id.btnSave)
        val etTitle = findViewById<EditText>(R.id.etTitle)
        val etContent = findViewById<EditText>(R.id.etContent)
        val rvNotes = findViewById<RecyclerView>(R.id.rvNotes)

        // Navigation Layouts
        val homeLayout = findViewById<View>(R.id.homeLayout)
        val infoLayout = findViewById<View>(R.id.infoLayout)

        // Profile Inputs
        val etName = findViewById<EditText>(R.id.etProfileName)
        val etID = findViewById<EditText>(R.id.etProfileID)
        val etCourse = findViewById<EditText>(R.id.etProfileCourse)
        val btnUpdate = findViewById<Button>(R.id.btnUpdateProfile)

        // 3. Setup RecyclerView with Detail View Listener
        adapter = NoteAdapter(mutableListOf()) { note ->
            showNoteDetail(note) // View note in a larger dialog
        }
        rvNotes.layoutManager = LinearLayoutManager(this)
        rvNotes.adapter = adapter

        // 4. Bottom Navigation Logic (English)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    homeLayout.visibility = View.VISIBLE
                    infoLayout.visibility = View.GONE
                    btnScan.show()
                    topAppBar.title = "Notes Vault" // English Title
                    true
                }
                R.id.nav_info -> {
                    homeLayout.visibility = View.GONE
                    infoLayout.visibility = View.VISIBLE
                    btnScan.hide()
                    topAppBar.title = "User Profile" // English Title
                    true
                }
                else -> false
            }
        }

        // 5. Load Profile Data (Mobile Personalization)
        etName.setText(prefs.getString("n", ""))
        etID.setText(prefs.getString("i", ""))
        etCourse.setText(prefs.getString("c", "CSC661"))

        btnUpdate.setOnClickListener {
            prefs.edit().apply {
                putString("n", etName.text.toString())
                putString("i", etID.text.toString())
                putString("c", etCourse.text.toString())
                apply()
            }
            Toast.makeText(this, "Profile Updated Successfully!", Toast.LENGTH_SHORT).show()
        }

        // 6. Offline Room Database Flow (Real-time updates)
        lifecycleScope.launch {
            db.noteDao().getAllNotes().collect { list ->
                adapter.updateData(list)
            }
        }

        // 7. Mobile Vision Logic (OCR via Camera)
        val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            if (res.resultCode == RESULT_OK) {
                val bitmap = res.data?.extras?.get("data") as Bitmap
                val image = InputImage.fromBitmap(bitmap, 0)

                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        etContent.setText(visionText.text)
                        Toast.makeText(this, "Text Scanned!", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Scan Failed", Toast.LENGTH_SHORT).show()
                    }
            }
        }

        btnScan.setOnClickListener {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            cameraLauncher.launch(intent)
        }

        // 8. Save Note Logic (Offline Room DB)
        btnSave.setOnClickListener {
            val title = etTitle.text.toString()
            if (title.isNotEmpty()) {
                val newNote = Note(title = title, content = etContent.text.toString(), location = "Offline")
                lifecycleScope.launch(Dispatchers.IO) {
                    db.noteDao().insert(newNote)
                    withContext(Dispatchers.Main) {
                        etTitle.text.clear()
                        etContent.text.clear()
                        Toast.makeText(this@MainActivity, "Note Saved Offline!", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, "Please enter a title", Toast.LENGTH_SHORT).show()
            }
        }

        // 9. Swipe-to-Delete Functionality
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val note = adapter.getNoteAt(viewHolder.adapterPosition)
                lifecycleScope.launch(Dispatchers.IO) {
                    db.noteDao().delete(note)
                }
            }
        }).attachToRecyclerView(rvNotes)
    }

    // Larger View for Notes (Material Dialog)
    private fun showNoteDetail(note: Note) {
        MaterialAlertDialogBuilder(this)
            .setTitle(note.title)
            .setMessage(note.content)
            .setPositiveButton("Close", null)
            .show()
    }
}