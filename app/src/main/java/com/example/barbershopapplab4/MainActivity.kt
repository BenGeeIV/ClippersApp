package com.example.barbershopapplab4

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat

class MainActivity : AppCompatActivity() {

    // Feature 1: In-memory database to hold booked slots
    private val bookedSlots = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Creating Notification Channel
        createNotificationChannel()

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
                tvStatus.setTextColor(android.graphics.Color.RED)
                tvStatus.text = "ERROR: $selectedSlot is already booked. Please choose another time."
            } else {
                // "Time is available! Save it."
                bookedSlots.add(selectedSlot)
                tvStatus.setTextColor(android.graphics.Color.parseColor("#006400")) // Dark Green
                tvStatus.text = "SUCCESS: Appointment confirmed for $selectedSlot."

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

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
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
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}