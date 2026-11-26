package com.example.barbershopapplab4

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

// DASHBOARD STATISTICS
data class AppointmentStats(
    val totalActive: Int = 0,
    val onlineBookings: Int = 0,
    val walkInBookings: Int = 0,
    val cancelledAppointments: Int = 0,
    val adminCreatedBookings: Int = 0
)

class AppointmentRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    // GENERATE SLOT ID
    private fun createSlotId(date: String, timeSlot: String): String {
        val safeDate = date.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')

        val safeTime = timeSlot.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')

        return "dan_${safeDate}_${safeTime}"
    }

    // CUSTOMER ONLINE BOOKING
    fun bookAppointment(
        customerName: String,
        customerEmail: String,
        date: String,
        timeSlot: String,
        onResult: (Result<String>) -> Unit
    ) {
        val user = auth.currentUser

        if (user == null) {
            onResult(Result.failure(Exception("Please log in first.")))
            return
        }

        if (customerName.isBlank() ||
            customerEmail.isBlank() ||
            date.isBlank() ||
            timeSlot.isBlank()
        ) {
            onResult(
                Result.failure(Exception("Complete all appointment details."))
            )
            return
        }

        createAppointment(
            customerUid = user.uid,
            customerName = customerName,
            customerEmail = customerEmail,
            phoneNumber = "",
            date = date,
            timeSlot = timeSlot,
            bookingType = "online",
            onResult = onResult
        )
    }

    // ADMIN BOOKING - REGISTERED OR WALK-IN CUSTOMER
    fun createAppointmentByAdmin(
        customerUid: String?,
        customerName: String,
        customerEmail: String,
        date: String,
        timeSlot: String,
        phoneNumber: String = "",
        onResult: (Result<String>) -> Unit
    ) {
        if (auth.currentUser == null) {
            onResult(
                Result.failure(Exception("Please log in first."))
            )
            return
        }

        if (customerName.isBlank() ||
            date.isBlank() ||
            timeSlot.isBlank()
        ) {
            onResult(
                Result.failure(
                    Exception("Customer name, date, and time are required.")
                )
            )
            return
        }

        val cleanUid = customerUid?.trim().orEmpty()

        val bookingType = if (cleanUid.isBlank()) {
            "walk_in"
        } else {
            "admin"
        }

        createAppointment(
            customerUid = cleanUid,
            customerName = customerName,
            customerEmail = customerEmail,
            phoneNumber = phoneNumber,
            date = date,
            timeSlot = timeSlot,
            bookingType = bookingType,
            onResult = onResult
        )
    }

    // SHARED APPOINTMENT CREATION
    // ALL BOOKING TYPES USE THE SAME SLOT PROTECTION
    private fun createAppointment(
        customerUid: String,
        customerName: String,
        customerEmail: String,
        phoneNumber: String,
        date: String,
        timeSlot: String,
        bookingType: String,
        onResult: (Result<String>) -> Unit
    ) {

        val slotId = createSlotId(date, timeSlot)

        val slotRef = db.collection("slots").document(slotId)
        val appointmentRef = db.collection("appointments").document()

        db.runTransaction { transaction ->

            // CHECK SLOT AVAILABILITY
            val slotSnapshot = transaction.get(slotRef)

            if (slotSnapshot.exists() &&
                slotSnapshot.getString("status") == "booked"
            ) {
                throw Exception(
                    "This time slot is already booked. Please select another time."
                )
            }

            // CREATE APPOINTMENT
            val appointment = hashMapOf<String, Any>(
                "customerName" to customerName.trim(),
                "customerEmail" to customerEmail.trim(),
                "barber" to "Dan",
                "date" to date,
                "timeSlot" to timeSlot,
                "slotId" to slotId,
                "status" to "booked",
                "bookingType" to bookingType,
                "createdAt" to FieldValue.serverTimestamp()
            )

            if (customerUid.isNotBlank()) {
                appointment["customerUid"] = customerUid
            }

            if (phoneNumber.isNotBlank()) {
                appointment["customerPhone"] = phoneNumber.trim()
            }

            // RESERVE SLOT
            val slot = hashMapOf<String, Any>(
                "status" to "booked",
                "appointmentId" to appointmentRef.id,
                "date" to date,
                "timeSlot" to timeSlot,
                "barber" to "Dan"
            )

            transaction.set(appointmentRef, appointment)
            transaction.set(slotRef, slot)

            appointmentRef.id

        }.addOnSuccessListener { appointmentId ->
            onResult(Result.success(appointmentId))
        }.addOnFailureListener { error ->
            onResult(Result.failure(error))
        }
    }

    // CANCEL APPOINTMENT
    fun cancelAppointment(
        appointmentId: String,
        reason: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        val user = auth.currentUser

        if (user == null) {
            onResult(Result.failure(Exception("Please log in again.")))
            return
        }

        if (reason.isBlank()) {
            onResult(
                Result.failure(
                    Exception("Please provide a cancellation reason.")
                )
            )
            return
        }

        val appointmentRef = db.collection("appointments")
            .document(appointmentId)

        db.runTransaction { transaction ->

            val appointmentSnapshot = transaction.get(appointmentRef)

            if (!appointmentSnapshot.exists()) {
                throw Exception("Appointment not found.")
            }

            if (appointmentSnapshot.getString("status") != "booked") {
                throw Exception("This appointment is already cancelled.")
            }

            val slotId = appointmentSnapshot.getString("slotId")
                ?: throw Exception("Appointment slot information is missing.")

            val slotRef = db.collection("slots").document(slotId)

            val slotSnapshot = transaction.get(slotRef)

            if (!slotSnapshot.exists()) {
                throw Exception("Booking slot not found.")
            }

            // CHECK SLOT OWNERSHIP
            if (slotSnapshot.getString("appointmentId") != appointmentId) {
                throw Exception(
                    "Slot ownership mismatch. Please contact the administrator."
                )
            }

            // SAVE CANCELLATION HISTORY
            transaction.update(
                appointmentRef,
                mapOf(
                    "status" to "cancelled",
                    "cancellationReason" to reason.trim(),
                    "cancelledBy" to user.uid,
                    "cancelledAt" to FieldValue.serverTimestamp()
                )
            )

            // RELEASE SLOT
            transaction.update(
                slotRef,
                mapOf(
                    "status" to "available",
                    "appointmentId" to null
                )
            )

            Unit

        }.addOnSuccessListener {
            onResult(Result.success(Unit))
        }.addOnFailureListener { error ->
            onResult(Result.failure(error))
        }
    }

    // GET ALL APPOINTMENTS FOR ADMIN
    fun getAllAppointments(
        onResult: (Result<List<DocumentSnapshot>>) -> Unit
    ) {
        db.collection("appointments")
            .get()
            .addOnSuccessListener { snapshot ->
                onResult(Result.success(snapshot.documents))
            }
            .addOnFailureListener { error ->
                onResult(Result.failure(error))
            }
    }

    // GET ADMIN DASHBOARD STATISTICS
    fun getAppointmentStats(
        onResult: (Result<AppointmentStats>) -> Unit
    ) {
        db.collection("appointments")
            .get()
            .addOnSuccessListener { snapshot ->

                val appointments = snapshot.documents

                val active = appointments.filter {
                    it.getString("status") == "booked"
                }

                val cancelled = appointments.filter {
                    it.getString("status") == "cancelled"
                }

                val online = active.count {
                    it.getString("bookingType") == "online"
                }

                val walkIn = active.count {
                    it.getString("bookingType") == "walk_in"
                }

                val adminCreated = active.count {
                    it.getString("bookingType") == "admin"
                }

                val stats = AppointmentStats(
                    totalActive = active.size,
                    onlineBookings = online,
                    walkInBookings = walkIn,
                    cancelledAppointments = cancelled.size,
                    adminCreatedBookings = adminCreated
                )

                onResult(Result.success(stats))
            }
            .addOnFailureListener { error ->
                onResult(Result.failure(error))
            }
    }

    // GET CANCELLED APPOINTMENTS FOR ADMIN HISTORY
    fun getCancelledAppointments(
        onResult: (Result<List<DocumentSnapshot>>) -> Unit
    ) {
        db.collection("appointments")
            .whereEqualTo("status", "cancelled")
            .get()
            .addOnSuccessListener { snapshot ->
                onResult(Result.success(snapshot.documents))
            }
            .addOnFailureListener { error ->
                onResult(Result.failure(error))
            }
    }
}
