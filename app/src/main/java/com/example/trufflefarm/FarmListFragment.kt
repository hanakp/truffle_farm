package com.example.trufflefarm

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.Fragment

class FarmListFragment : Fragment() {

    private val PREFS_NAME = "TruffleFarmPrefs"
    private val FARMS_KEY = "Farms"

    interface OnFarmSelectedListener {
        fun onFarmSelected(lat: Double, lng: Double)
    }

    private var listener: OnFarmSelectedListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnFarmSelectedListener) {
            listener = context
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_list, container, false)
        view.findViewById<TextView>(R.id.list_title).text = "My Farms"
        
        val listView = view.findViewById<ListView>(R.id.list_view)
        val farms = loadFarms()
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, farms.map { it.name })
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val selectedFarm = farms[position]
            listener?.onFarmSelected(selectedFarm.lat, selectedFarm.lng)
        }

        return view
    }

    private fun loadFarms(): List<Farm> {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val farmsSet = prefs.getStringSet(FARMS_KEY, emptySet()) ?: emptySet()
        return farmsSet.mapNotNull {
            val parts = it.split("|")
            if (parts.size >= 2) {
                val name = parts[0]
                val pointsStr = parts[1]
                val firstPoint = pointsStr.split(";").firstOrNull()?.split(",")
                if (firstPoint?.size == 2) {
                    val lat = firstPoint[0].toDoubleOrNull()
                    val lng = firstPoint[1].toDoubleOrNull()
                    if (lat != null && lng != null) {
                        Farm(name, lat, lng)
                    } else null
                } else null
            } else null
        }
    }

    data class Farm(val name: String, val lat: Double, val lng: Double)
}
