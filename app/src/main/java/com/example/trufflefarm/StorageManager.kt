package com.example.trufflefarm

import android.net.Uri
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import java.io.File

class StorageManager {

    private val storage = Firebase.storage
    private val auth = Firebase.auth

    fun uploadPhoto(localPath: String, onComplete: (String?) -> Unit) {
        val userId = auth.currentUser?.uid ?: return
        val file = Uri.fromFile(File(localPath))
        val ref = storage.reference.child("users/$userId/photos/${file.lastPathSegment}")

        ref.putFile(file)
            .addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { uri ->
                    onComplete(uri.toString())
                }
            }
            .addOnFailureListener {
                onComplete(null)
            }
    }
}
