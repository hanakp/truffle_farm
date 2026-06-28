package com.example.trufflefarm

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

class SettingsFragment : Fragment() {

    private val PREFS_NAME = "TruffleFarmPrefs"
    private val COLOR_FARM = "color_farm"
    private val COLOR_SOIL = "color_soil"
    private val COLOR_PLANTS = "color_plants"

    private val colors = arrayOf(
        Color.GREEN, Color.parseColor("#006400"), Color.parseColor("#8B4513"),
        Color.RED, Color.BLUE, Color.YELLOW, Color.MAGENTA, Color.CYAN, Color.DKGRAY
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)
        
        setupColorRow(view.findViewById(R.id.color_row_farm), COLOR_FARM, Color.GREEN)
        setupColorRow(view.findViewById(R.id.color_row_soil), COLOR_SOIL, Color.parseColor("#8B4513"))
        setupColorRow(view.findViewById(R.id.color_row_plants), COLOR_PLANTS, Color.parseColor("#006400"))
        
        return view
    }

    private fun setupColorRow(row: LinearLayout, key: String, defaultColor: Int) {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentColor = prefs.getInt(key, defaultColor)
        
        for (color in colors) {
            val container = View(requireContext()).apply {
                val size = 120
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    setMargins(12, 12, 12, 12)
                }
                
                val drawable = ContextCompat.getDrawable(requireContext(), R.drawable.color_circle) as GradientDrawable
                drawable.setColor(color)
                if (color == currentColor) {
                    drawable.setStroke(6, Color.BLACK)
                    elevation = 12f
                } else {
                    drawable.setStroke(2, Color.LTGRAY)
                    elevation = 0f
                }
                background = drawable
                
                setOnClickListener {
                    prefs.edit().putInt(key, color).apply()
                    // Refresh row visually
                    for (i in 0 until row.childCount) {
                        val child = row.getChildAt(i)
                        val childColor = colors[i]
                        val d = ContextCompat.getDrawable(requireContext(), R.drawable.color_circle) as GradientDrawable
                        d.setColor(childColor)
                        d.setStroke(2, Color.LTGRAY)
                        child.background = d
                        child.elevation = 0f
                    }
                    (background as GradientDrawable).setStroke(6, Color.BLACK)
                    elevation = 12f
                }
            }
            row.addView(container)
        }
    }
}
