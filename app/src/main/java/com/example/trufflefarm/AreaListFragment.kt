package com.example.trufflefarm

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseExpandableListAdapter
import android.widget.ExpandableListView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AreaListFragment : Fragment() {

    private val PREFS_NAME = "TruffleFarmPrefs"
    private val AREAS_KEY = "Areas"
    private val OLD_FARMS_KEY = "Farms"

    interface OnAreaSelectedListener {
        fun onAreaSelected(lat: Double, lng: Double)
    }

    private var listener: OnAreaSelectedListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnAreaSelectedListener) {
            listener = context
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_list, container, false)
        view.findViewById<TextView>(R.id.list_title).text = getString(R.string.title_farms_zones)
        
        val expandableListView = view.findViewById<ExpandableListView>(R.id.expandable_list_view)
        val groupedData = loadAndGroupAreas()
        
        val adapter = AreaExpandableAdapter(requireContext(), groupedData) { area ->
            listener?.onAreaSelected(area.lat, area.lng)
        }
        expandableListView.setAdapter(adapter)

        for (i in 0 until adapter.groupCount) {
            expandableListView.expandGroup(i)
        }

        return view
    }

    private fun loadAndGroupAreas(): List<Pair<Area, List<Area>>> {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val allAreas = mutableListOf<Area>()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        
        val areasSet = prefs.getStringSet(AREAS_KEY, emptySet()) ?: emptySet()
        for (data in areasSet) {
            val parts = data.split("|")
            if (parts.size >= 4) {
                val type = parts[0]
                val name = parts[1]
                val notes = parts[2]
                val points = parsePoints(parts[3])
                val dateStr = if (parts.size >= 5) {
                    val ts = parts[4].toLongOrNull()
                    if (ts != null) getString(R.string.created_at, dateFormat.format(Date(ts))) else ""
                } else ""
                
                if (points.isNotEmpty()) {
                    allAreas.add(Area(name, type, notes, points, dateStr))
                }
            }
        }
        
        val oldFarmsSet = prefs.getStringSet(OLD_FARMS_KEY, emptySet()) ?: emptySet()
        for (data in oldFarmsSet) {
            val parts = data.split("|")
            if (parts.size >= 2) {
                val points = parsePoints(parts[1])
                if (points.isNotEmpty()) {
                    allAreas.add(Area(parts[0], "FARM", "", points, ""))
                }
            }
        }

        val farms = allAreas.filter { it.type == "FARM" }
        val subAreas = allAreas.filter { it.type != "FARM" }

        val result = mutableListOf<Pair<Area, List<Area>>>()
        
        val usedSubAreas = mutableSetOf<Area>()
        for (farm in farms) {
            val children = subAreas.filter { sub ->
                PolyUtil.containsLocation(sub.points[0], farm.points, false)
            }
            result.add(farm to children)
            usedSubAreas.addAll(children)
        }

        val orphaned = subAreas.filter { it !in usedSubAreas }
        if (orphaned.isNotEmpty()) {
            val dummyFarm = Area(getString(R.string.other_zones), "NONE", getString(R.string.zones_outside_farms), emptyList(), "")
            result.add(dummyFarm to orphaned)
        }

        return result
    }

    private fun parsePoints(pointsStr: String): List<LatLng> {
        return pointsStr.split(";").mapNotNull {
            val coords = it.split(",")
            if (coords.size == 2) {
                val lat = coords[0].toDoubleOrNull()
                val lng = coords[1].toDoubleOrNull()
                if (lat != null && lng != null) LatLng(lat, lng) else null
            } else null
        }
    }

    data class Area(val name: String, val type: String, val notes: String, val points: List<LatLng>, val date: String) {
        val lat: Double get() = points.firstOrNull()?.latitude ?: 0.0
        val lng: Double get() = points.firstOrNull()?.longitude ?: 0.0
    }

    class AreaExpandableAdapter(
        private val context: Context,
        private val data: List<Pair<Area, List<Area>>>,
        private val onClick: (Area) -> Unit
    ) : BaseExpandableListAdapter() {

        override fun getGroupCount() = data.size
        override fun getChildrenCount(groupPosition: Int) = data[groupPosition].second.size
        override fun getGroup(groupPosition: Int) = data[groupPosition].first
        override fun getChild(groupPosition: Int, childPosition: Int) = data[groupPosition].second[childPosition]
        override fun getGroupId(groupPosition: Int) = groupPosition.toLong()
        override fun getChildId(groupPosition: Int, childPosition: Int) = childPosition.toLong()
        override fun hasStableIds() = true

        override fun getGroupView(groupPosition: Int, isExpanded: Boolean, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(context).inflate(android.R.layout.simple_expandable_list_item_2, parent, false)
            val farm = getGroup(groupPosition)
            view.findViewById<TextView>(android.R.id.text1).apply {
                text = "🚜 ${farm.name}"
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            view.findViewById<TextView>(android.R.id.text2).apply {
                val details = mutableListOf<String>()
                if (farm.notes.isNotEmpty()) details.add(farm.notes)
                if (farm.date.isNotEmpty()) details.add(farm.date)
                text = details.joinToString(" | ")
            }
            view.setOnClickListener { onClick(farm) }
            return view
        }

        override fun getChildView(groupPosition: Int, childPosition: Int, isLastChild: Boolean, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(context).inflate(android.R.layout.simple_expandable_list_item_2, parent, false)
            val area = getChild(groupPosition, childPosition)
            val icon = if (area.type == "SOIL") "🟤" else "🌳"
            
            val typeName = when(area.type) {
                "SOIL" -> context.getString(R.string.type_soil)
                "PLANTS" -> context.getString(R.string.type_plants)
                else -> area.type
            }

            view.findViewById<TextView>(android.R.id.text1).apply {
                text = "      $icon ${area.name} ($typeName)"
            }
            view.findViewById<TextView>(android.R.id.text2).apply {
                val details = mutableListOf<String>()
                if (area.notes.isNotEmpty()) details.add(area.notes)
                if (area.date.isNotEmpty()) details.add(area.date)
                text = "            " + details.joinToString(" | ")
            }
            view.setOnClickListener { onClick(area) }
            return view
        }

        override fun isChildSelectable(groupPosition: Int, childPosition: Int) = true
    }
}
