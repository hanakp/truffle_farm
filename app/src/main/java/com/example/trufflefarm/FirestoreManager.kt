package com.example.trufflefarm

import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class FirestoreManager {

    private val db = Firebase.firestore
    private val auth = Firebase.auth

    fun saveArea(type: String, name: String, notes: String, pointsStr: String, timestamp: Long, photoPath: String, onComplete: (Boolean) -> Unit) {
        val userId = auth.currentUser?.uid ?: return
        val area = hashMapOf(
            "type" to type,
            "name" to name,
            "notes" to notes,
            "points" to pointsStr,
            "timestamp" to timestamp,
            "photoPath" to photoPath
        )

        db.collection("users").document(userId).collection("areas")
            .add(area)
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    fun saveMarker(lat: Double, lng: Double, note: String, timestamp: Long, photoPath: String, onComplete: (Boolean) -> Unit) {
        val userId = auth.currentUser?.uid ?: return
        val marker = hashMapOf(
            "lat" to lat,
            "lng" to lng,
            "note" to note,
            "timestamp" to timestamp,
            "photoPath" to photoPath
        )

        db.collection("users").document(userId).collection("markers")
            .add(marker)
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    fun getAreas(onResult: (List<Map<String, Any>>) -> Unit) {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).collection("areas")
            .get()
            .addOnSuccessListener { result ->
                onResult(result.map { it.data })
            }
    }

    fun getMarkers(onResult: (List<Map<String, Any>>) -> Unit) {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).collection("markers")
            .get()
            .addOnSuccessListener { result ->
                onResult(result.map { it.data })
            }
    }
}
