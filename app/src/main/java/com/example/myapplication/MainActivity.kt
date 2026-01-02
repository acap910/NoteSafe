package com.example.myapplication // ⚠️ ENSURE THIS MATCHES YOUR PACKAGE NAME

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
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
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. INITIALIZE DATABASE & PREFERENCES
        db = NoteDatabase.getInstance(this)
        val prefs = getSharedPreferences("SecureVaultPrefs", Context.MODE_PRIVATE)

        // 2. BIND UI COMPONENTS
        val topAppBar = findViewById<MaterialToolbar>(R.id.topAppBar)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        val btnScan = findViewById<FloatingActionButton>(R.id.btnScan)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val etTitle = findViewById<EditText>(R.id.etTitle)
        val etContent = findViewById<EditText>(R.id.etContent)
        val rvNotes = findViewById<RecyclerView>(R.id.rvNotes)
        val homeLayout = findViewById<View>(R.id.homeLayout)
        val infoLayout = findViewById<View>(R.id.infoLayout)

        // Bind Profile Components
        val etName = findViewById<EditText>(R.id.etProfileName)
        val etID = findViewById<EditText>(R.id.etProfileID)
        val etCourse = findViewById<EditText>(R.id.etProfileCourse)
        val btnUpdateProfile = findViewById<Button>(R.id.btnUpdateProfile)

        // 3. SETUP APP BAR MENU (For Unlocking Vault)
        topAppBar.inflateMenu(R.menu.top_app_bar_menu)
        topAppBar.setOnMenuItemClickListener { menuItem ->
            if (menuItem.itemId == R.id.action_unlock) {
                toggleVaultSecurity(menuItem)
                true
            } else false
        }

        // 4. SETUP RECYCLERVIEW (With Biometric Item Access)
        adapter = NoteAdapter(mutableListOf()) { note ->
            authenticateUser("View Secure Content") {
                showNoteDetail(note)
            }
        }
        rvNotes.layoutManager = LinearLayoutManager(this)
        rvNotes.adapter = adapter

        // 5. SECURE SWIPE-TO-DELETE (Biometric Required)
        setupSwipeToDelete(rvNotes)

        // 6. NAVIGATION LOGIC
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    homeLayout.visibility = View.VISIBLE
                    infoLayout.visibility = View.GONE
                    btnScan.show()
                    topAppBar.title = "Notes Vault"
                    true
                }
                R.id.nav_info -> {
                    homeLayout.visibility = View.GONE
                    infoLayout.visibility = View.VISIBLE
                    btnScan.hide()
                    topAppBar.title = "User Profile"
                    true
                }
                else -> false
            }
        }

        // 7. PROFILE PERSONALIZATION (SharedPreferences)
        etName.setText(prefs.getString("userName", ""))
        etID.setText(prefs.getString("studentID", ""))
        etCourse.setText(prefs.getString("courseCode", "CSC661"))

        btnUpdateProfile.setOnClickListener {
            prefs.edit().apply {
                putString("userName", etName.text.toString())
                putString("studentID", etID.text.toString())
                putString("courseCode", etCourse.text.toString())
                apply()
            }
            Toast.makeText(this, "Profile Configuration Saved", Toast.LENGTH_SHORT).show()
        }

        // 8. DATA FLOW & MOBILE VISION OCR
        lifecycleScope.launch {
            db.noteDao().getAllNotes().collect { adapter.updateData(it) }
        }

        val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            if (res.resultCode == RESULT_OK) {
                val bitmap = res.data?.extras?.get("data") as Bitmap
                recognizer.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { etContent.setText(it.text) }
            }
        }
        btnScan.setOnClickListener { cameraLauncher.launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE)) }

        btnSave.setOnClickListener {
            val title = etTitle.text.toString()
            if (title.isNotEmpty()) {
                lifecycleScope.launch(Dispatchers.IO) {
                    db.noteDao().insert(Note(title = title, content = etContent.text.toString(), location = "SafeKeep Vault"))
                    withContext(Dispatchers.Main) {
                        etTitle.text.clear()
                        etContent.text.clear()
                        Toast.makeText(this@MainActivity, "Encrypted & Saved", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // --- SECURITY FUNCTIONS ---

    private fun toggleVaultSecurity(menuItem: android.view.MenuItem) {
        if (!adapter.isUnlocked()) {
            authenticateUser("Reveal Censored Notes") {
                adapter.setVaultState(true)
                menuItem.setIcon(android.R.drawable.ic_partial_secure) // Unlocked Icon
                Toast.makeText(this, "Vault Unlocked", Toast.LENGTH_SHORT).show()
            }
        } else {
            adapter.setVaultState(false)
            menuItem.setIcon(android.R.drawable.ic_lock_idle_lock) // Locked Icon
            Toast.makeText(this, "Vault Locked", Toast.LENGTH_SHORT).show()
        }
    }

    private fun authenticateUser(subtitle: String, onSuccess: () -> Unit) {
        val biometricPrompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Identity Verification")
            .setSubtitle(subtitle)
            .setNegativeButtonText("Cancel")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun setupSwipeToDelete(recyclerView: RecyclerView) {
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val note = adapter.getNoteAt(position)

                authenticateUser("Authorize Permanent Deletion") {
                    lifecycleScope.launch(Dispatchers.IO) {
                        db.noteDao().delete(note)
                    }
                }
                adapter.notifyItemChanged(position) // Return item if auth cancelled
            }
        }).attachToRecyclerView(recyclerView)
    }

    private fun showNoteDetail(note: Note) {
        MaterialAlertDialogBuilder(this)
            .setTitle(note.title)
            .setMessage(note.content)
            .setPositiveButton("Close", null)
            .show()
    }
}