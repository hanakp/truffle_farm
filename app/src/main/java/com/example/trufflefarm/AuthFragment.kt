package com.example.trufflefarm

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class AuthFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private var isLoginMode = true

    interface OnAuthListener {
        fun onAuthenticated()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = Firebase.auth
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_auth, container, false)
        
        val title = view.findViewById<TextView>(R.id.auth_title)
        val etEmail = view.findViewById<EditText>(R.id.et_email)
        val etPassword = view.findViewById<EditText>(R.id.et_password)
        val btnAuth = view.findViewById<Button>(R.id.btn_auth)
        val tvSwitch = view.findViewById<TextView>(R.id.tv_switch_auth)

        tvSwitch.setOnClickListener {
            isLoginMode = !isLoginMode
            if (isLoginMode) {
                title.text = getString(R.string.login)
                btnAuth.text = getString(R.string.login)
                tvSwitch.text = getString(R.string.no_account)
            } else {
                title.text = getString(R.string.register)
                btnAuth.text = getString(R.string.register)
                tvSwitch.text = getString(R.string.has_account)
            }
        }

        btnAuth.setOnClickListener {
            val email = etEmail.text.toString()
            val password = etPassword.text.toString()

            if (email.isBlank() || password.isBlank()) {
                Toast.makeText(requireContext(), "Fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (isLoginMode) {
                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(requireActivity()) { task ->
                        if (task.isSuccessful) {
                            (activity as? OnAuthListener)?.onAuthenticated()
                        } else {
                            Toast.makeText(requireContext(), getString(R.string.auth_failed), Toast.LENGTH_SHORT).show()
                        }
                    }
            } else {
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(requireActivity()) { task ->
                        if (task.isSuccessful) {
                            (activity as? OnAuthListener)?.onAuthenticated()
                        } else {
                            Toast.makeText(requireContext(), getString(R.string.auth_failed), Toast.LENGTH_SHORT).show()
                        }
                    }
            }
        }

        return view
    }
}
