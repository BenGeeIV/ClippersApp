package com.example.barbershopapplab4

import android.app.DatePickerDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private var currentRole = "customer"
    private var selectedDate = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val sharedPref = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val isDarkMode = sharedPref.getBoolean("dark_mode", false)

        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        setContentView(R.layout.activity_main)

        val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        val navigationView = findViewById<NavigationView>(R.id.navigationView)

        val loginSection = findViewById<View>(R.id.loginSection)
        val bookingSection = findViewById<View>(R.id.bookingSection)

        val etUsername = findViewById<EditText>(R.id.etUsername)
        val etPassword = findViewById<EditText>(R.id.etPassword)

        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnRegister = findViewById<Button>(R.id.btnRegister)

        val btnSelectDate = findViewById<Button>(R.id.btnSelectDate)
        val tvSelectedDate = findViewById<TextView>(R.id.tvSelectedDate)

        val spinnerTimeSlots = findViewById<Spinner>(R.id.spinnerTimeSlots)
        val btnBook = findViewById<Button>(R.id.btnBook)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)

        // TOOLBAR AND DRAWER
        val toggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )

        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        val typedValue = TypedValue()

        if (theme.resolveAttribute(
                com.google.android.material.R.attr.colorOnPrimary,
                typedValue,
                true
            )
        ) {
            toggle.drawerArrowDrawable.color = typedValue.data
        } else {
            toggle.drawerArrowDrawable.color = Color.WHITE
        }

        // LOGIN SCREEN VISIBILITY
        fun updateLoginStateUI(isLoggedIn: Boolean) {
            if (isLoggedIn) {
                loginSection.visibility = View.GONE
                bookingSection.visibility = View.VISIBLE
                toolbar.visibility = View.VISIBLE

                drawerLayout.setDrawerLockMode(
                    DrawerLayout.LOCK_MODE_UNLOCKED
                )
            } else {
                loginSection.visibility = View.VISIBLE
                bookingSection.visibility = View.GONE
                toolbar.visibility = View.GONE

                drawerLayout.setDrawerLockMode(
                    DrawerLayout.LOCK_MODE_LOCKED_CLOSED
                )
            }
        }

        updateLoginStateUI(false)

        // LOAD USER PROFILE
        fun loadUserProfile(uid: String) {
            db.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener { document ->

                    if (!document.exists()) {
                        auth.signOut()
                        updateLoginStateUI(false)

                        Toast.makeText(
                            this,
                            "User profile not found.",
                            Toast.LENGTH_LONG
                        ).show()

                        return@addOnSuccessListener
                    }

                    currentRole = document.getString("role") ?: "customer"

                    if (currentRole != "customer" && currentRole != "admin") {
                        auth.signOut()
                        updateLoginStateUI(false)

                        Toast.makeText(
                            this,
                            "Invalid account role.",
                            Toast.LENGTH_LONG
                        ).show()

                        return@addOnSuccessListener
                    }

                    updateLoginStateUI(true)

                    Toast.makeText(
                        this,
                        if (currentRole == "admin")
                            "Admin login successful!"
                        else
                            "Welcome to StyleSlot!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .addOnFailureListener { exception ->
                    auth.signOut()
                    updateLoginStateUI(false)

                    Toast.makeText(
                        this,
                        "Unable to load profile: ${exception.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }

        // RESTORE LOGIN SESSION
        val currentUser = auth.currentUser

        if (currentUser != null) {
            loadUserProfile(currentUser.uid)
        }

        // LOGIN BUTTON
        btnLogin.setOnClickListener {

            val email = etUsername.text.toString().trim()
            val password = etPassword.text.toString()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(
                    this,
                    "Please enter your email and password.",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener { result ->

                    val uid = result.user?.uid

                    if (uid != null) {
                        loadUserProfile(uid)
                    }
                }
                .addOnFailureListener { exception ->

                    Toast.makeText(
                        this,
                        "Login failed: ${exception.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }

        // REGISTER BUTTON
        btnRegister.setOnClickListener {

            val email = etUsername.text.toString().trim()
            val password = etPassword.text.toString()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(
                    this,
                    "Enter your email and password first.",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            if (password.length < 6) {
                Toast.makeText(
                    this,
                    "Password must contain at least 6 characters.",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            val nameInput = EditText(this).apply {
                hint = "Enter your full name"
                inputType = InputType.TYPE_CLASS_TEXT or
                        InputType.TYPE_TEXT_FLAG_CAP_WORDS
                setPadding(40, 30, 40, 30)
            }

            AlertDialog.Builder(this)
                .setTitle("Create StyleSlot Account")
                .setMessage("Please enter your full name.")
                .setView(nameInput)
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("REGISTER") { _, _ ->

                    val name = nameInput.text.toString().trim()

                    if (name.isEmpty()) {
                        Toast.makeText(
                            this,
                            "Please enter your full name.",
                            Toast.LENGTH_SHORT
                        ).show()

                        return@setPositiveButton
                    }

                    auth.createUserWithEmailAndPassword(email, password)
                        .addOnSuccessListener { result ->

                            val uid = result.user?.uid

                            if (uid == null) {
                                Toast.makeText(
                                    this,
                                    "Unable to create account.",
                                    Toast.LENGTH_LONG
                                ).show()

                                return@addOnSuccessListener
                            }

                            val userProfile = hashMapOf(
                                "name" to name,
                                "email" to email,
                                "role" to "customer",
                                "createdAt" to FieldValue.serverTimestamp()
                            )

                            db.collection("users")
                                .document(uid)
                                .set(userProfile)
                                .addOnSuccessListener {

                                    currentRole = "customer"
                                    updateLoginStateUI(true)

                                    Toast.makeText(
                                        this,
                                        "Account created successfully!",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                .addOnFailureListener { exception ->

                                    auth.signOut()

                                    Toast.makeText(
                                        this,
                                        "Profile saving failed: ${exception.localizedMessage}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                        }
                        .addOnFailureListener { exception ->

                            Toast.makeText(
                                this,
                                "Registration failed: ${exception.localizedMessage}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                }
                .show()
        }

        // LOGOUT
        fun performLogout() {

            auth.signOut()
            currentRole = "customer"
            selectedDate = ""

            tvSelectedDate.text = "No date selected"
            tvStatus.text = ""

            updateLoginStateUI(false)

            etUsername.text.clear()
            etPassword.text.clear()

            drawerLayout.closeDrawer(GravityCompat.START)

            Toast.makeText(
                this,
                "Logged out successfully.",
                Toast.LENGTH_SHORT
            ).show()
        }

        // ANDROID BACK BUTTON
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {

                override fun handleOnBackPressed() {

                    if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                        drawerLayout.closeDrawer(GravityCompat.START)
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        )

        // NOTIFICATION CHANNEL
        createNotificationChannel()

        // DARK MODE
        val menuItemDarkMode = navigationView.menu.findItem(
            R.id.nav_dark_mode
        )

        val actionViewDarkMode = menuItemDarkMode.actionView

        val switchDarkMode = actionViewDarkMode?.findViewById<MaterialSwitch>(
            R.id.switchDarkMode
        )

        switchDarkMode?.isChecked = isDarkMode

        switchDarkMode?.setOnCheckedChangeListener { _, isChecked ->

            val mode = if (isChecked) {
                AppCompatDelegate.MODE_NIGHT_YES
            } else {
                AppCompatDelegate.MODE_NIGHT_NO
            }

            sharedPref.edit()
                .putBoolean("dark_mode", isChecked)
                .apply()

            AppCompatDelegate.setDefaultNightMode(mode)
        }

        // NAVIGATION DRAWER
        navigationView.setNavigationItemSelectedListener { item ->

            when (item.itemId) {

                R.id.nav_admin_appointments -> {

                    drawerLayout.closeDrawer(GravityCompat.START)

                    if (currentRole == "admin") {

                        val intent = Intent(
                            this,
                            AdminAppointmentsActivity::class.java
                        )

                        startActivity(intent)

                    } else {

                        Toast.makeText(
                            this,
                            "Admin access only.",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    true
                }

                R.id.nav_dark_mode -> {

                    if (switchDarkMode != null) {
                        switchDarkMode.isChecked =
                            !switchDarkMode.isChecked
                    }

                    true
                }

                R.id.nav_logout -> {

                    performLogout()
                    true
                }

                else -> false
            }
        }

        // TIME SLOTS
        val slots = arrayOf(
            "8:00 - 9:00 AM",
            "9:00 - 10:00 AM",
            "10:00 - 11:00 AM",
            "1:00 - 2:00 PM"
        )

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            slots
        )

        spinnerTimeSlots.adapter = adapter

        // DATE PICKER
        btnSelectDate.setOnClickListener {

            val calendar = Calendar.getInstance()

            val datePicker = DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->

                    val selectedCalendar = Calendar.getInstance()

                    selectedCalendar.set(
                        year,
                        month,
                        dayOfMonth
                    )

                    val dateFormat = SimpleDateFormat(
                        "MMMM dd, yyyy",
                        Locale.getDefault()
                    )

                    selectedDate = dateFormat.format(
                        selectedCalendar.time
                    )

                    tvSelectedDate.text = selectedDate
                    tvStatus.text = ""

                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )

            datePicker.datePicker.minDate = calendar.timeInMillis

            datePicker.setTitle("Choose Appointment Date")
            datePicker.show()
        }

        // FIRESTORE BOOKING LOGIC
        val appointmentRepository = AppointmentRepository()

        btnBook.setOnClickListener {

            if (selectedDate.isEmpty()) {

                Toast.makeText(
                    this,
                    "Please select an appointment date first.",
                    Toast.LENGTH_SHORT
                ).show()

                tvStatus.setTextColor(Color.RED)
                tvStatus.text = "Please select an appointment date."

                return@setOnClickListener
            }

            val user = auth.currentUser

            if (user == null) {
                Toast.makeText(
                    this,
                    "Please log in first.",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            val selectedSlot = spinnerTimeSlots.selectedItem.toString()

            btnBook.isEnabled = false
            tvStatus.setTextColor(Color.BLACK)
            tvStatus.text = "Checking availability..."

            db.collection("users")
                .document(user.uid)
                .get()
                .addOnSuccessListener { document ->

                    if (!document.exists()) {
                        btnBook.isEnabled = true
                        tvStatus.setTextColor(Color.RED)
                        tvStatus.text = "Customer profile not found."

                        return@addOnSuccessListener
                    }

                    val customerName =
                        document.getString("name") ?: ""

                    val customerEmail =
                        document.getString("email") ?: user.email.orEmpty()

                    if (customerName.isBlank() || customerEmail.isBlank()) {
                        btnBook.isEnabled = true
                        tvStatus.setTextColor(Color.RED)
                        tvStatus.text = "Customer information is incomplete."

                        return@addOnSuccessListener
                    }

                    appointmentRepository.bookAppointment(
                        customerName = customerName,
                        customerEmail = customerEmail,
                        date = selectedDate,
                        timeSlot = selectedSlot
                    ) { result ->

                        btnBook.isEnabled = true

                        if (result.isSuccess) {

                            val message =
                                "SUCCESS: Appointment confirmed!\n" +
                                        "Date: $selectedDate\n" +
                                        "Time: $selectedSlot\n" +
                                        "Barber: Dan"

                            tvStatus.setTextColor(
                                Color.parseColor("#006400")
                            )

                            tvStatus.text = message

                            Toast.makeText(
                                this,
                                "Appointment confirmed!",
                                Toast.LENGTH_SHORT
                            ).show()

                            sendNotification(selectedDate, selectedSlot)

                        } else {

                            val errorMessage =
                                result.exceptionOrNull()?.localizedMessage
                                    ?: "Unable to book appointment."

                            tvStatus.setTextColor(Color.RED)
                            tvStatus.text = errorMessage

                            Toast.makeText(
                                this,
                                errorMessage,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
                .addOnFailureListener { exception ->

                    btnBook.isEnabled = true

                    tvStatus.setTextColor(Color.RED)
                    tvStatus.text =
                        "Unable to retrieve customer information."

                    Toast.makeText(
                        this,
                        exception.localizedMessage
                            ?: "Something went wrong.",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }

    // SEND NOTIFICATION
    private fun sendNotification(date: String, slot: String) {

        val builder = NotificationCompat.Builder(
            this,
            "STYLE_SLOT_CHANNEL"
        )
            .setSmallIcon(android.R.drawable.ic_menu_today)
            .setContentTitle("StyleSlot Appointment Confirmed!")
            .setContentText(
                "You are booked with Dan on $date at $slot."
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.notify(1, builder.build())
    }

    // CREATE NOTIFICATION CHANNEL
    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val name = "StyleSlot Notifications"
            val descriptionText = "Channel for booking reminders"

            val importance = NotificationManager.IMPORTANCE_HIGH

            val channel = NotificationChannel(
                "STYLE_SLOT_CHANNEL",
                name,
                importance
            ).apply {
                description = descriptionText
            }

            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            notificationManager.createNotificationChannel(channel)
        }
    }
}
