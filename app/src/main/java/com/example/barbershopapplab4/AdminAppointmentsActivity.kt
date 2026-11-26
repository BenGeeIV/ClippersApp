package com.example.barbershopapplab4

import android.app.DatePickerDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AdminAppointmentsActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var repository: AppointmentRepository

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmptyAppointments: TextView
    private lateinit var appointmentAdapter: AppointmentAdapter

    private lateinit var btnRefreshAppointments: Button
    private lateinit var btnCreateAppointment: Button
    private lateinit var btnAppointmentHistory: Button

    private lateinit var txtTotalActive: TextView
    private lateinit var txtOnlineBookings: TextView
    private lateinit var txtWalkInBookings: TextView
    private lateinit var txtCancelledBookings: TextView
    private lateinit var txtAdminCreatedBookings: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        repository = AppointmentRepository()

        val user = auth.currentUser

        if (user == null) {
            finish()
            return
        }

        db.collection("users")
            .document(user.uid)
            .get()
            .addOnSuccessListener { document ->

                val role = document.getString("role")

                if (role != "admin") {
                    Toast.makeText(
                        this,
                        "Admin access only.",
                        Toast.LENGTH_LONG
                    ).show()

                    finish()
                    return@addOnSuccessListener
                }

                setContentView(R.layout.activity_admin_appointments)

                initializeViews()
                loadAppointments()
                loadDashboardStats()
            }
            .addOnFailureListener {
                Toast.makeText(
                    this,
                    "Unable to verify admin account.",
                    Toast.LENGTH_LONG
                ).show()

                finish()
            }
    }

    // INITIALIZE DASHBOARD
    private fun initializeViews() {

        recyclerView = findViewById(R.id.recyclerViewAppointments)
        tvEmptyAppointments = findViewById(R.id.tvEmptyAppointments)

        btnRefreshAppointments = findViewById(R.id.btnRefreshAppointments)
        btnCreateAppointment = findViewById(R.id.btnCreateAppointment)
        btnAppointmentHistory = findViewById(R.id.btnAppointmentHistory)

        txtTotalActive = findViewById(R.id.txtTotalActive)
        txtOnlineBookings = findViewById(R.id.txtOnlineBookings)
        txtWalkInBookings = findViewById(R.id.txtWalkInBookings)
        txtCancelledBookings = findViewById(R.id.txtCancelledBookings)
        txtAdminCreatedBookings = findViewById(R.id.txtAdminCreatedBookings)

        appointmentAdapter = AppointmentAdapter()

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = appointmentAdapter

        btnRefreshAppointments.setOnClickListener {
            loadAppointments()
            loadDashboardStats()

            Toast.makeText(
                this,
                "Dashboard refreshed.",
                Toast.LENGTH_SHORT
            ).show()
        }

        btnCreateAppointment.setOnClickListener {
            showCreateAppointmentDialog()
        }

        btnAppointmentHistory.setOnClickListener {
            showCancelledAppointmentHistory()
        }
    }

    // LOAD DASHBOARD STATISTICS
    private fun loadDashboardStats() {

        repository.getAppointmentStats { result ->

            result.onSuccess { stats ->

                runOnUiThread {

                    txtTotalActive.text =
                        "Active\n${stats.totalActive}"

                    txtOnlineBookings.text =
                        "Online\n${stats.onlineBookings}"

                    txtWalkInBookings.text =
                        "Walk-in\n${stats.walkInBookings}"

                    txtCancelledBookings.text =
                        "Cancelled\n${stats.cancelledAppointments}"

                    txtAdminCreatedBookings.text =
                        "Admin-created bookings: ${stats.adminCreatedBookings}"
                }
            }

            result.onFailure { error ->

                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Failed to load statistics: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // CREATE APPOINTMENT DIALOG
    private fun showCreateAppointmentDialog() {

        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 8, 48, 8)
        }

        fun addInput(
            hintText: String,
            inputTypeValue: Int
        ): EditText {

            val input = EditText(this).apply {
                hint = hintText
                inputType = inputTypeValue
            }

            form.addView(
                input,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )

            return input
        }

        val customerUid = addInput(
            "Customer UID (optional - leave blank for walk-in)",
            InputType.TYPE_CLASS_TEXT
        )

        val customerName = addInput(
            "Customer Name *",
            InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_CAP_WORDS
        )

        val customerEmail = addInput(
            "Customer Email (optional)",
            InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        )

        val phoneNumber = addInput(
            "Phone Number (required for walk-in)",
            InputType.TYPE_CLASS_PHONE
        )

        val date = addInput(
            "Select Appointment Date *",
            InputType.TYPE_CLASS_TEXT
        ).apply {
            isFocusable = false
            isClickable = true
        }

        date.setOnClickListener {

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

                    val formatter = SimpleDateFormat(
                        "MMMM dd, yyyy",
                        Locale.US
                    )

                    date.setText(
                        formatter.format(selectedCalendar.time)
                    )
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )

            datePicker.datePicker.minDate =
                System.currentTimeMillis() - 1000

            datePicker.show()
        }

        val timeSlot = Spinner(this)

        val timeOptions = listOf(
            "Select Appointment Time *",
            "8:00 - 9:00 AM",
            "9:00 - 10:00 AM",
            "10:00 - 11:00 AM",
            "1:00 - 2:00 PM"
        )

        val timeAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            timeOptions
        )

        timeAdapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )

        timeSlot.adapter = timeAdapter

        form.addView(
            timeSlot,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val dialog = AlertDialog.Builder(this)
            .setTitle("Create Appointment")
            .setMessage(
                "Enter a registered customer's UID or leave it blank for a walk-in customer."
            )
            .setView(form)
            .setPositiveButton("Create", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {

            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {

                    val uid = customerUid.text.toString().trim()
                    val name = customerName.text.toString().trim()
                    val email = customerEmail.text.toString().trim()
                    val phone = phoneNumber.text.toString().trim()

                    val appointmentDate =
                        date.text.toString().trim()

                    val appointmentTime =
                        timeSlot.selectedItem?.toString()?.trim() ?: ""

                    if (
                        name.isBlank() ||
                        appointmentDate.isBlank() ||
                        timeSlot.selectedItemPosition == 0
                    ) {
                        Toast.makeText(
                            this,
                            "Please enter the customer name, date, and time.",
                            Toast.LENGTH_SHORT
                        ).show()

                        return@setOnClickListener
                    }

                    if (uid.isBlank() && phone.isBlank()) {
                        Toast.makeText(
                            this,
                            "Phone number is required for walk-in customers.",
                            Toast.LENGTH_SHORT
                        ).show()

                        return@setOnClickListener
                    }

                    if (
                        email.isNotBlank() &&
                        !android.util.Patterns.EMAIL_ADDRESS
                            .matcher(email)
                            .matches()
                    ) {
                        Toast.makeText(
                            this,
                            "Please enter a valid email address.",
                            Toast.LENGTH_SHORT
                        ).show()

                        return@setOnClickListener
                    }

                    dialog.dismiss()

                    if (uid.isBlank()) {

                        createAdminAppointment(
                            uid = null,
                            name = name,
                            email = email,
                            phone = phone,
                            appointmentDate = appointmentDate,
                            appointmentTime = appointmentTime
                        )

                    } else {

                        verifyCustomerAndCreateAppointment(
                            uid = uid,
                            name = name,
                            email = email,
                            phone = phone,
                            date = appointmentDate,
                            timeSlot = appointmentTime
                        )
                    }
                }
        }

        dialog.show()
    }

    // VERIFY REGISTERED CUSTOMER
    private fun verifyCustomerAndCreateAppointment(
        uid: String,
        name: String,
        email: String,
        phone: String,
        date: String,
        timeSlot: String
    ) {

        db.collection("users")
            .document(uid)
            .get()
            .addOnSuccessListener { document ->

                if (!document.exists()) {
                    Toast.makeText(
                        this,
                        "Customer account not found.",
                        Toast.LENGTH_LONG
                    ).show()

                    return@addOnSuccessListener
                }

                if (document.getString("role") == "admin") {
                    Toast.makeText(
                        this,
                        "Please enter a customer UID, not an admin UID.",
                        Toast.LENGTH_LONG
                    ).show()

                    return@addOnSuccessListener
                }

                createAdminAppointment(
                    uid = uid,
                    name = name,
                    email = email,
                    phone = phone,
                    appointmentDate = date,
                    appointmentTime = timeSlot
                )
            }
            .addOnFailureListener { error ->

                Toast.makeText(
                    this,
                    "Customer verification failed: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // CREATE REGISTERED OR WALK-IN APPOINTMENT
    private fun createAdminAppointment(
        uid: String?,
        name: String,
        email: String,
        phone: String,
        appointmentDate: String,
        appointmentTime: String
    ) {

        repository.createAppointmentByAdmin(
            customerUid = uid,
            customerName = name,
            customerEmail = email,
            date = appointmentDate,
            timeSlot = appointmentTime,
            phoneNumber = phone
        ) { result ->

            result.onSuccess {

                val bookingType = if (uid.isNullOrBlank()) {
                    "Walk-in"
                } else {
                    "Registered customer"
                }

                Toast.makeText(
                    this,
                    "$bookingType appointment created successfully!",
                    Toast.LENGTH_LONG
                ).show()

                loadAppointments()
                loadDashboardStats()
            }

            result.onFailure { error ->

                Toast.makeText(
                    this,
                    "Failed to create appointment: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // LOAD ACTIVE APPOINTMENTS ONLY
    private fun loadAppointments() {

        tvEmptyAppointments.visibility = View.GONE

        repository.getAllAppointments { result ->

            result.onSuccess { appointments ->

                val activeAppointments = appointments.filter { appointment ->
                    appointment.getString("status")
                        .equals("booked", ignoreCase = true)
                }

                runOnUiThread {

                    appointmentAdapter.updateAppointments(
                        activeAppointments
                    )

                    if (activeAppointments.isEmpty()) {

                        tvEmptyAppointments.text =
                            "No active appointments available."

                        tvEmptyAppointments.visibility = View.VISIBLE
                        recyclerView.visibility = View.GONE

                    } else {

                        tvEmptyAppointments.visibility = View.GONE
                        recyclerView.visibility = View.VISIBLE
                    }
                }
            }

            result.onFailure { error ->

                runOnUiThread {

                    Toast.makeText(
                        this,
                        "Failed to load appointments: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()

                    tvEmptyAppointments.text =
                        "Unable to load appointments."

                    tvEmptyAppointments.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                }
            }
        }
    }

    // SHOW CANCELLED APPOINTMENT HISTORY
    private fun showCancelledAppointmentHistory() {

        Toast.makeText(
            this,
            "Loading cancelled appointments...",
            Toast.LENGTH_SHORT
        ).show()

        repository.getCancelledAppointments { result ->

            result.onSuccess { appointments ->

                runOnUiThread {

                    if (appointments.isEmpty()) {

                        AlertDialog.Builder(this)
                            .setTitle("Cancelled Appointment History")
                            .setMessage("There are no cancelled appointments yet.")
                            .setPositiveButton("Close", null)
                            .show()

                        return@runOnUiThread
                    }

                    val historyLayout = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(32, 16, 32, 16)
                    }

                    appointments.forEachIndexed { index, appointment ->

                        val name = appointment.getString("customerName")
                            ?: "Unknown Customer"

                        val date = appointment.getString("date")
                            ?: "No date"

                        val time = appointment.getString("timeSlot")
                            ?: "No time"

                        val barber = appointment.getString("barber")
                            ?: "Not specified"

                        val reason = appointment.getString("cancellationReason")
                            ?: "No reason provided"

                        val cancelledBy = appointment.getString("cancelledBy")
                            ?: "Not specified"

                        val details = TextView(this).apply {

                            text = """
                                CUSTOMER: $name
                                BARBER: $barber
                                DATE: $date
                                TIME: $time
                                CANCELLATION REASON: $reason
                                CANCELLED BY: $cancelledBy
                            """.trimIndent()

                            textSize = 14f
                            setTextColor(Color.BLACK)
                            setPadding(16, 16, 16, 16)

                            background = GradientDrawable().apply {
                                setColor(Color.WHITE)
                                cornerRadius = 12f
                                setStroke(1, Color.LTGRAY)
                            }
                        }

                        historyLayout.addView(
                            details,
                            LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            ).apply {
                                bottomMargin = 16
                            }
                        )
                    }

                    val scrollView = ScrollView(this).apply {
                        addView(historyLayout)
                    }

                    AlertDialog.Builder(this)
                        .setTitle(
                            "Cancelled Appointment History (${appointments.size})"
                        )
                        .setView(scrollView)
                        .setPositiveButton("Close", null)
                        .show()
                }
            }

            result.onFailure { error ->

                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Unable to load history: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // CANCEL APPOINTMENT DIALOG
    private fun showCancelDialog(appointment: DocumentSnapshot) {

        val appointmentId = appointment.id

        val input = EditText(this).apply {
            hint = "Enter cancellation reason"
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Cancel Appointment")
            .setMessage(
                "Are you sure you want to cancel this appointment?"
            )
            .setView(input)
            .setPositiveButton("Confirm", null)
            .setNegativeButton("Back", null)
            .create()

        dialog.setOnShowListener {

            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {

                    val reason = input.text.toString().trim()

                    if (reason.isBlank()) {
                        Toast.makeText(
                            this,
                            "Please enter a cancellation reason.",
                            Toast.LENGTH_SHORT
                        ).show()

                        return@setOnClickListener
                    }

                    dialog.dismiss()

                    repository.cancelAppointment(
                        appointmentId,
                        reason
                    ) { result ->

                        result.onSuccess {

                            Toast.makeText(
                                this,
                                "Appointment cancelled successfully.",
                                Toast.LENGTH_SHORT
                            ).show()

                            loadAppointments()
                            loadDashboardStats()
                        }

                        result.onFailure { error ->

                            Toast.makeText(
                                this,
                                "Cancellation failed: ${error.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
        }

        dialog.show()
    }

    // APPOINTMENT RECYCLER VIEW ADAPTER
    private inner class AppointmentAdapter :
        RecyclerView.Adapter<AppointmentAdapter.AppointmentViewHolder>() {

        private var appointments: List<DocumentSnapshot> = emptyList()

        fun updateAppointments(
            newAppointments: List<DocumentSnapshot>
        ) {
            appointments = newAppointments
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): AppointmentViewHolder {

            val container = LinearLayout(parent.context)

            container.orientation = LinearLayout.VERTICAL

            val density = resources.displayMetrics.density
            val padding = (16 * density).toInt()

            container.setPadding(
                padding,
                padding,
                padding,
                padding
            )

            container.background =
                GradientDrawable().apply {

                    setColor(Color.WHITE)
                    cornerRadius = 16 * density

                    setStroke(
                        (1 * density).toInt(),
                        Color.LTGRAY
                    )
                }

            val params = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            params.bottomMargin = (12 * density).toInt()

            container.layoutParams = params

            val details = TextView(parent.context).apply {
                textSize = 15f
                setTextColor(Color.BLACK)
            }

            container.addView(details)

            val cancelButton = Button(parent.context).apply {
                text = "Cancel Appointment"
            }

            container.addView(cancelButton)

            return AppointmentViewHolder(
                container,
                details,
                cancelButton
            )
        }

        override fun onBindViewHolder(
            holder: AppointmentViewHolder,
            position: Int
        ) {

            val appointment = appointments[position]

            val customerName =
                appointment.getString("customerName")
                    ?: "Unknown Customer"

            val customerEmail =
                appointment.getString("customerEmail")
                    ?.takeIf { it.isNotBlank() }
                    ?: "No email"

            val customerPhone =
                appointment.getString("customerPhone")
                    ?.takeIf { it.isNotBlank() }
                    ?: "No phone number"

            val customerUid =
                appointment.getString("customerUid")
                    ?.takeIf { it.isNotBlank() }
                    ?: "Walk-in customer"

            val bookingType =
                appointment.getString("bookingType")
                    ?: "online"

            val barber =
                appointment.getString("barber")
                    ?: "Not specified"

            val date =
                appointment.getString("date")
                    ?: "No date"

            val timeSlot =
                appointment.getString("timeSlot")
                    ?: "No time"

            val status =
                appointment.getString("status")
                    ?: "Unknown"

            holder.details.text = """
                CUSTOMER: $customerName

                CUSTOMER UID: $customerUid

                EMAIL: $customerEmail

                PHONE: $customerPhone

                BARBER: $barber

                DATE: $date

                TIME: $timeSlot

                BOOKING TYPE: ${bookingType.uppercase()}

                STATUS: ${status.replaceFirstChar { it.uppercase() }}
            """.trimIndent()

            if (status.equals("booked", ignoreCase = true)) {

                holder.cancelButton.visibility = View.VISIBLE

                holder.cancelButton.setOnClickListener {
                    showCancelDialog(appointment)
                }

            } else {

                holder.cancelButton.visibility = View.GONE
                holder.cancelButton.setOnClickListener(null)
            }
        }

        override fun getItemCount(): Int {
            return appointments.size
        }

        inner class AppointmentViewHolder(
            itemView: View,
            val details: TextView,
            val cancelButton: Button
        ) : RecyclerView.ViewHolder(itemView)
    }
}
