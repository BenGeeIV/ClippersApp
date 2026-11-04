package com.example.barbershopapplab4

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.navigation.NavigationView

class MainActivity : AppCompatActivity() {

    // Feature 1: In-memory database to hold booked slots
    private val bookedSlots = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load saved preference from SharedPreferences & apply night mode before setContentView
        val sharedPref = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val isDarkMode = sharedPref.getBoolean("dark_mode", false)
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        setContentView(R.layout.activity_main)

        val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        val navigationView = findViewById<NavigationView>(R.id.navigationView)

        // Setup Hamburger Menu & Toolbar
        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Fix burger icon color for high visibility in both light and dark mode
        val typedValue = TypedValue()
        if (theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimary, typedValue, true)) {
            toggle.drawerArrowDrawable.color = typedValue.data
        } else {
            toggle.drawerArrowDrawable.color = Color.WHITE
        }

        val loginSection = findViewById<View>(R.id.loginSection)
        val bookingSection = findViewById<View>(R.id.bookingSection)
        val etUsername = findViewById<EditText>(R.id.etUsername)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)

        // Function to update UI based on login state (Hamburger shows after login)
        fun updateLoginStateUI(isLoggedIn: Boolean) {
            if (isLoggedIn) {
                loginSection.visibility = View.GONE
                bookingSection.visibility = View.VISIBLE
                toolbar.visibility = View.VISIBLE
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
            } else {
                loginSection.visibility = View.VISIBLE
                bookingSection.visibility = View.GONE
                toolbar.visibility = View.GONE
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            }
        }

        // Restore login state
        var isLoggedIn = sharedPref.getBoolean("is_logged_in", false)
        updateLoginStateUI(isLoggedIn)

        // LOGOUT LOGIC (Inside Hamburger Menu)
        fun performLogout() {
            isLoggedIn = false
            sharedPref.edit().putBoolean("is_logged_in", false).apply()
            updateLoginStateUI(false)
            etUsername.text.clear()
            etPassword.text.clear()
            drawerLayout.closeDrawer(GravityCompat.START)
            Toast.makeText(this, "Logged Out", Toast.LENGTH_SHORT).show()
        }

        // LOGIN LOGIC
        btnLogin.setOnClickListener {
            if (etUsername.text.toString() == "admin" && etPassword.text.toString() == "123") {
                isLoggedIn = true
                sharedPref.edit().putBoolean("is_logged_in", true).apply()
                updateLoginStateUI(true)
                Toast.makeText(this, "Login Successful", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Invalid Credentials", Toast.LENGTH_SHORT).show()
            }
        }

        // Handle Back Press when Drawer is open
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        // Creating Notification Channel
        createNotificationChannel()

        // FEATURE 4: Dark Mode
        val menuItemDarkMode = navigationView.menu.findItem(R.id.nav_dark_mode)
        val actionViewDarkMode = menuItemDarkMode.actionView
        val switchDarkMode = actionViewDarkMode?.findViewById<MaterialSwitch>(R.id.switchDarkMode)

        switchDarkMode?.isChecked = isDarkMode

        switchDarkMode?.setOnCheckedChangeListener { _, isChecked ->
            val mode = if (isChecked) {
                AppCompatDelegate.MODE_NIGHT_YES
            } else {
                AppCompatDelegate.MODE_NIGHT_NO
            }
            AppCompatDelegate.setDefaultNightMode(mode)
            sharedPref.edit().putBoolean("dark_mode", isChecked).apply()
        }

        // HAMBURGER MENU NAVIGATION LISTENER
        navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dark_mode -> {
                    if (switchDarkMode != null) {
                        switchDarkMode.isChecked = !switchDarkMode.isChecked
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

        val spinnerTimeSlots = findViewById<Spinner>(R.id.spinnerTimeSlots)
        val btnBook = findViewById<Button>(R.id.btnBook)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)

        // Setup dummy timeslots based on Figma
        val slots = arrayOf("8:00 - 9:00 AM", "9:00 - 10:00 AM", "10:00 - 11:00 AM", "1:00 - 2:00 PM")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, slots)
        spinnerTimeSlots.adapter = adapter

        // Button Click Logic
        btnBook.setOnClickListener {
            val selectedSlot = spinnerTimeSlots.selectedItem.toString()

            // FEATURE 1: Conflict Validation Logic
            if (bookedSlots.contains(selectedSlot)) {
                // "Time is already booked"
                // FEATURE 5: Toast Notification
                val message = "ERROR: $selectedSlot is already booked. Please choose another time."
                tvStatus.setTextColor(Color.RED)
                tvStatus.text = message
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            } else {
                // "Time is available! Save it."
                bookedSlots.add(selectedSlot)
                val message = "SUCCESS: Appointment confirmed for $selectedSlot."
                tvStatus.setTextColor(Color.parseColor("#006400")) // Dark Green
                tvStatus.text = message
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

                // FEATURE 2: Trigger Notification
                sendNotification(selectedSlot)
            }
        }
    }

    // FEATURE 2: Notification Engine Function
    private fun sendNotification(slot: String) {
        val builder = NotificationCompat.Builder(this, "STYLE_SLOT_CHANNEL")
            .setSmallIcon(android.R.drawable.ic_menu_today)
            .setContentTitle("StyleSlot Appointment Confirmed!")
            .setContentText("You are booked with Dan for $slot. Don't be late!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1, builder.build())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "StyleSlot Notifications"
            val descriptionText = "Channel for booking reminders"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel("STYLE_SLOT_CHANNEL", name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
