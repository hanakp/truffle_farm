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

class MainActivity : AppCompatActivity(), 
    AreaListFragment.OnAreaSelectedListener, 
    NoteListFragment.OnNoteSelectedListener,
    HomeFragment.OnHomeNavigationListener {

    private lateinit var bottomNav: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                R.id.nav_help -> {
                    replaceFragment(HelpFragment())
                    true
                }
                else -> false
            }
        }

        if (savedInstanceState == null) {
            replaceFragment(HomeFragment(), false)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
                if (currentFragment !is HomeFragment) {
                    bottomNav.selectedItemId = R.id.nav_home
                } else {
                    finish()
                }
            }
        })
    }

    private fun replaceFragment(fragment: Fragment, showBottomNav: Boolean = true) {
        bottomNav.visibility = if (showBottomNav) View.VISIBLE else View.GONE
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    override fun onNavigateTo(itemId: Int) {
        bottomNav.selectedItemId = itemId
    }

    override fun onAreaSelected(lat: Double, lng: Double) {
        navigateToMap(lat, lng)
    }

    override fun onNoteSelected(lat: Double, lng: Double) {
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
