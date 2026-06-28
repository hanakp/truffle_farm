package com.example.trufflefarm

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class ProfileFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)
        
        val emailText = view.findViewById<TextView>(R.id.profile_email)
        val btnLogout = view.findViewById<Button>(R.id.btn_logout)
        
        val user = Firebase.auth.currentUser
        emailText.text = user?.email
        
        btnLogout.setOnClickListener {
            (activity as? MainActivity)?.logout()
        }
        
        return view
    }
}
