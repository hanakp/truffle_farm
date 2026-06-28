package com.example.trufflefarm

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class MainActivity : AppCompatActivity(), 
    AreaListFragment.OnAreaSelectedListener, 
    NoteListFragment.OnNoteSelectedListener,
    HomeFragment.OnHomeNavigationListener,
    GalleryFragment.OnPhotoSelectedListener,
    AuthFragment.OnAuthListener {

    private lateinit var bottomNav: BottomNavigationView
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = Firebase.auth
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        bottomNav = findViewById(R.id.bottom_navigation)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    replaceFragment(HomeFragment(), false)
                    true
                }
                R.id.nav_map -> {
                    replaceFragment(MapFragment())
                    true
                }
                R.id.nav_farms -> {
                    replaceFragment(AreaListFragment())
                    true
                }
                R.id.nav_notes -> {
                    replaceFragment(NoteListFragment())
                    true
                }
                R.id.nav_gallery -> {
                    replaceFragment(GalleryFragment())
                    true
                }
                R.id.nav_help -> {
                    replaceFragment(HelpFragment())
                    true
                }
                else -> false
            }
        }

        if (savedInstanceState == null) {
            checkUserLoggedIn()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
                if (currentFragment is AuthFragment) {
                    finish()
                } else if (currentFragment !is HomeFragment) {
                    bottomNav.selectedItemId = R.id.nav_home
                } else {
                    finish()
                }
            }
        })
    }

    private fun checkUserLoggedIn() {
        if (auth.currentUser == null) {
            replaceFragment(AuthFragment(), false)
        } else {
            replaceFragment(HomeFragment(), false)
        }
    }

    private fun replaceFragment(fragment: Fragment, showBottomNav: Boolean = true) {
        bottomNav.visibility = if (showBottomNav) View.VISIBLE else View.GONE
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    override fun onAuthenticated() {
        replaceFragment(HomeFragment(), false)
    }

    fun logout() {
        auth.signOut()
        replaceFragment(AuthFragment(), false)
    }

    override fun onNavigateTo(itemId: Int) {
        if (itemId == R.id.nav_help) {
            bottomNav.visibility = View.VISIBLE
            replaceFragment(HelpFragment(), true)
        } else if (itemId == R.id.nav_settings) {
            bottomNav.visibility = View.VISIBLE
            replaceFragment(SettingsFragment(), true)
        } else if (itemId == R.id.nav_profile) {
            bottomNav.visibility = View.VISIBLE
            replaceFragment(ProfileFragment(), true)
        } else {
            bottomNav.selectedItemId = itemId
        }
    }

    override fun onAreaSelected(lat: Double, lng: Double) {
        navigateToMap(lat, lng)
    }

    override fun onAddNewRequested() {
        val mapFragment = MapFragment().apply {
            arguments = Bundle().apply {
                putBoolean("request_add", true)
            }
        }
        bottomNav.selectedItemId = R.id.nav_map
        replaceFragment(mapFragment)
    }

    override fun onNoteSelected(lat: Double, lng: Double) {
        navigateToMap(lat, lng)
    }

    override fun onPhotoLocationSelected(lat: Double, lng: Double) {
        navigateToMap(lat, lng)
    }

    private fun navigateToMap(lat: Double, lng: Double) {
        val mapFragment = MapFragment().apply {
            arguments = Bundle().apply {
                putDouble("lat", lat)
                putDouble("lng", lng)
            }
        }
        bottomNav.selectedItemId = R.id.nav_map
        replaceFragment(mapFragment)
    }
}
