package com.example.speedbreakerdetector

import android.location.Location
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

object FirestoreRepository {

    private val db = FirebaseFirestore.getInstance()

    // -------------------------------
    // Utility: distance between points
    // -------------------------------
    private fun distanceInMeters(
        lat1: Double,
        lng1: Double,
        lat2: Double,
        lng2: Double
    ): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lng1, lat2, lng2, results)
        return results[0]
    }

    // ----------------------------------------------------
    // 1️⃣ WRITE UNVALIDATED HAZARD (sensor / manual report)
    // ----------------------------------------------------
    fun writeUnvalidatedHazard(
        latitude: Double,
        longitude: Double,
        severity: String,
        type: String,
        source: String
    ) {
        val hazardsRef = db.collection("hazards")

        hazardsRef.get().addOnSuccessListener { snapshot ->

            val existing = snapshot.documents.firstOrNull { doc ->
                val lat = doc.getDouble("latitude") ?: return@firstOrNull false
                val lng = doc.getDouble("longitude") ?: return@firstOrNull false
                val t = doc.getString("type") ?: return@firstOrNull false

                t == type && distanceInMeters(latitude, longitude, lat, lng) <= 15f
            }

            if (existing != null) {
                // Increment report count
                hazardsRef.document(existing.id).update(
                    mapOf(
                        "reportCount" to FieldValue.increment(1),
                        "severity" to severity,
                        "lastUpdated" to System.currentTimeMillis()
                    )
                )
            } else {
                // First report
                hazardsRef.add(
                    hashMapOf(
                        "latitude" to latitude,
                        "longitude" to longitude,
                        "type" to type,
                        "severity" to severity,
                        "source" to source,
                        "reportCount" to 1,
                        "validated" to false,
                        "lastUpdated" to System.currentTimeMillis()
                    )
                )
            }
        }
    }

    // -----------------------------------------
    // 2️⃣ VALIDATE HAZARD AFTER ELEVATION CHECK
    // -----------------------------------------
    fun validateHazardWithElevation(
        latitude: Double,
        longitude: Double,
        type: String,
        severity: String
    ) {
        db.collection("hazards")
            .whereEqualTo("type", type)
            .get()
            .addOnSuccessListener { snapshot ->
                snapshot.documents.forEach { doc ->
                    val lat = doc.getDouble("latitude") ?: return@forEach
                    val lng = doc.getDouble("longitude") ?: return@forEach

                    if (distanceInMeters(latitude, longitude, lat, lng) <= 15f) {
                        doc.reference.update(
                            mapOf(
                                "validated" to true,
                                "severity" to severity
                            )
                        )
                    }
                }
            }
    }

    fun voteRemoveHazard(latitude: Double, longitude: Double, type: String) {

        val key = "${String.format("%.5f", latitude)}_${String.format("%.5f", longitude)}_$type"
        val ref = Firebase.firestore.collection("hazards").document(key)

        Firebase.firestore.runTransaction { tx ->
            val snap = tx.get(ref)
            val votes = snap.getLong("removeVotes") ?: 0

            tx.update(ref, "removeVotes", votes + 1)
        }
    }

    // -----------------------------------
    // 3️⃣ LISTEN ONLY TO VALIDATED HAZARDS
    // -----------------------------------
    fun listenToValidatedHazards(
        onHazardsReceived: (List<DetectionEvent>) -> Unit
    ) {
        db.collection("hazards")
            .addSnapshotListener { snapshot, error ->

                if (error != null || snapshot == null) return@addSnapshotListener

                val validatedHazards = mutableListOf<DetectionEvent>()

                for (doc in snapshot.documents) {

                    val reportCount = (doc.getLong("reportCount") ?: 0).toInt()
                    val validated = doc.getBoolean("validated") ?: false
                    if (!validated || reportCount < 3) continue
                    val removeVotes = doc.getLong("removeVotes") ?: 0
                    if (removeVotes >= 3) continue
                    val lat = doc.getDouble("latitude") ?: continue
                    val lng = doc.getDouble("longitude") ?: continue
                    val type = doc.getString("type") ?: continue
                    val severity = doc.getString("severity") ?: "LOW"
                    val source = doc.getString("source") ?: "REPORTED"

                    validatedHazards.add(
                        DetectionEvent(
                            timestamp = System.currentTimeMillis(),
                            force = 0f,
                            latitude = lat,
                            longitude = lng,
                            type = type,
                            severity = severity,
                            status = "ACTIVE",
                            source = source,
                            reportCount = reportCount
                        )
                    )
                }

                onHazardsReceived(validatedHazards)
            }
    }
}
