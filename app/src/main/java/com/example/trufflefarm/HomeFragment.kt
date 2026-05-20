package com.example.trufflefarm

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView

class HomeFragment : Fragment() {

    interface OnHomeNavigationListener {
        fun onNavigateTo(itemId: Int)
    }

    private var navigationListener: OnHomeNavigationListener? = null

    override fun onAttach(context: android.content.Context) {
        super.onAttach(context)
        if (context is OnHomeNavigationListener) {
            navigationListener = context
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        view.findViewById<MaterialCardView>(R.id.card_map).setOnClickListener {
            navigationListener?.onNavigateTo(R.id.nav_map)
        }
        view.findViewById<MaterialCardView>(R.id.card_farms).setOnClickListener {
            navigationListener?.onNavigateTo(R.id.nav_farms)
        }
        view.findViewById<MaterialCardView>(R.id.card_notes).setOnClickListener {
            navigationListener?.onNavigateTo(R.id.nav_notes)
        }
        view.findViewById<MaterialCardView>(R.id.card_help).setOnClickListener {
            navigationListener?.onNavigateTo(R.id.nav_help)
        }

        view.findViewById<Button>(R.id.btn_home_en).setOnClickListener {
            changeLanguage("en")
        }
        view.findViewById<Button>(R.id.btn_home_sk).setOnClickListener {
            changeLanguage("sk")
        }

        return view
    }

    private fun changeLanguage(langCode: String) {
        val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(langCode)
        AppCompatDelegate.setApplicationLocales(appLocale)
    }
}
