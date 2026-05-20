package com.example.trufflefarm

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Geocoder
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polygon
import com.google.android.gms.maps.model.PolygonOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MapFragment : Fragment(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private val PREFS_NAME = "TruffleFarmPrefs"
    private val MARKERS_KEY = "Markers"
    private val AREAS_KEY = "Areas"

    private enum class DrawingMode { NONE, FARM, SOIL, PLANTS }
    private var currentMode = DrawingMode.NONE
    
    private val currentAreaPoints = mutableListOf<LatLng>()
    private var currentPolygon: Polygon? = null
    private val tempMarkers = mutableListOf<Marker>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_map, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val mapFragment = childFragmentManager.findFragmentById(R.id.map_view) as SupportMapFragment
        mapFragment.getMapAsync(this)

        setupSearch(view)
        setupDrawingControls(view)
    }

    private fun setupSearch(view: View) {
        val searchInput = view.findViewById<EditText>(R.id.search_input)
        val searchButton = view.findViewById<ImageButton>(R.id.search_button)

        searchButton.setOnClickListener {
            val query = searchInput.text.toString()
            if (query.isNotBlank()) {
                searchLocation(query)
            }
        }
    }

    private fun searchLocation(location: String) {
        val geocoder = Geocoder(requireContext())
        try {
            @Suppress("DEPRECATION")
            val addressList = geocoder.getFromLocationName(location, 1)
            if (addressList != null && addressList.isNotEmpty()) {
                val address = addressList[0]
                val latLng = LatLng(address.latitude, address.longitude)
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 14f))
                
                val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(view?.windowToken, 0)
            } else {
                Toast.makeText(requireContext(), getString(R.string.loc_not_found), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), getString(R.string.err_search, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupDrawingControls(view: View) {
        val btnUndo = view.findViewById<Button>(R.id.btn_undo)
        val btnClear = view.findViewById<Button>(R.id.btn_clear)
        val btnFinish = view.findViewById<Button>(R.id.btn_finish_area)

        btnUndo.setOnClickListener {
            if (currentAreaPoints.isNotEmpty()) {
                currentAreaPoints.removeAt(currentAreaPoints.size - 1)
                val lastMarker = tempMarkers.removeAt(tempMarkers.size - 1)
                lastMarker.remove()
                updatePolygon()
            }
        }

        btnClear.setOnClickListener {
            exitDrawingMode()
            loadAllData()
        }

        btnFinish.setOnClickListener {
            if (currentAreaPoints.size >= 3) {
                showSaveAreaDialog()
            } else {
                Toast.makeText(requireContext(), getString(R.string.msg_select_points), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.mapType = GoogleMap.MAP_TYPE_HYBRID
        enableMyLocation()
        loadAllData()

        mMap.setOnMapLongClickListener { latLng ->
            if (currentMode == DrawingMode.NONE) {
                showAddOptionsDialog(latLng)
            }
        }

        mMap.setOnMapClickListener { latLng ->
            if (currentMode != DrawingMode.NONE) {
                addPointToArea(latLng)
            }
        }

        arguments?.let {
            val lat = it.getDouble("lat", 0.0)
            val lng = it.getDouble("lng", 0.0)
            if (lat != 0.0 && lng != 0.0) {
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 18f))
            }
        }
    }

    private fun addPointToArea(latLng: LatLng) {
        currentAreaPoints.add(latLng)
        
        val marker = mMap.addMarker(MarkerOptions()
            .position(latLng)
            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            .anchor(0.5f, 0.5f)
            .alpha(0.7f))
        if (marker != null) tempMarkers.add(marker)
        
        updatePolygon()
    }

    private fun updatePolygon() {
        currentPolygon?.remove()
        if (currentAreaPoints.isNotEmpty()) {
            val color = getModeColor(currentMode)
            currentPolygon = mMap.addPolygon(PolygonOptions()
                .addAll(currentAreaPoints)
                .strokeColor(color)
                .fillColor(Color.argb(70, Color.red(color), Color.green(color), Color.blue(color)))
                .zIndex(10f))
        }
    }

    private fun getModeColor(mode: DrawingMode): Int {
        return when (mode) {
            DrawingMode.FARM -> Color.GREEN
            DrawingMode.SOIL -> Color.parseColor("#8B4513")
            DrawingMode.PLANTS -> Color.parseColor("#006400")
            else -> Color.RED
        }
    }

    private fun startDrawingMode(mode: DrawingMode) {
        currentMode = mode
        currentAreaPoints.clear()
        tempMarkers.forEach { it.remove() }
        tempMarkers.clear()
        
        mMap.uiSettings.isZoomGesturesEnabled = false 
        
        view?.findViewById<View>(R.id.drawing_controls)?.visibility = View.VISIBLE
        val msg = when(mode) {
            DrawingMode.FARM -> getString(R.string.msg_draw_farm)
            DrawingMode.SOIL -> getString(R.string.msg_draw_soil)
            DrawingMode.PLANTS -> getString(R.string.msg_draw_plants)
            else -> ""
        }
        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
    }

    private fun showAddOptionsDialog(latLng: LatLng) {
        val options = arrayOf(
            getString(R.string.add_note_marker),
            getString(R.string.define_farm_boundary),
            getString(R.string.define_soil_area),
            getString(R.string.define_planting_area)
        )
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.select_action))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showAddMarkerDialog(latLng)
                    1 -> startDrawingMode(DrawingMode.FARM)
                    2 -> startDrawingMode(DrawingMode.SOIL)
                    3 -> startDrawingMode(DrawingMode.PLANTS)
                }
            }
            .show()
    }

    private fun showSaveAreaDialog() {
        val layout = LinearLayout(requireContext())
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(50, 20, 50, 20)

        val nameInput = EditText(requireContext())
        nameInput.hint = getString(R.string.name_hint)
        layout.addView(nameInput)

        val notesInput = EditText(requireContext())
        notesInput.hint = when(currentMode) {
            DrawingMode.SOIL -> getString(R.string.soil_notes_hint)
            DrawingMode.PLANTS -> getString(R.string.plants_notes_hint)
            else -> getString(R.string.general_notes_hint)
        }
        layout.addView(notesInput)

        val modeName = when(currentMode) {
            DrawingMode.FARM -> getString(R.string.nav_farms)
            DrawingMode.SOIL -> getString(R.string.define_soil_area)
            DrawingMode.PLANTS -> getString(R.string.define_planting_area)
            else -> ""
        }

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.new_area_title, modeName))
            .setView(layout)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val name = nameInput.text.toString()
                val notes = notesInput.text.toString()
                if (name.isNotBlank()) {
                    saveArea(currentMode, name, notes, currentAreaPoints)
                    exitDrawingMode()
                    loadAllData()
                } else {
                    Toast.makeText(requireContext(), getString(R.string.name_required), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun exitDrawingMode() {
        currentMode = DrawingMode.NONE
        mMap.uiSettings.isZoomGesturesEnabled = true
        view?.findViewById<View>(R.id.drawing_controls)?.visibility = View.GONE
        currentAreaPoints.clear()
        currentPolygon?.remove()
        currentPolygon = null
        tempMarkers.forEach { it.remove() }
        tempMarkers.clear()
    }

    private fun saveArea(mode: DrawingMode, name: String, notes: String, points: List<LatLng>) {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val areasSet = prefs.getStringSet(AREAS_KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
        val pointsString = points.joinToString(";") { "${it.latitude},${it.longitude}" }
        val timestamp = System.currentTimeMillis()
        val areaData = "${mode.name}|$name|$notes|$pointsString|$timestamp"
        areasSet.add(areaData)
        prefs.edit().putStringSet(AREAS_KEY, areasSet).apply()
    }

    private fun loadAllData() {
        mMap.clear()
        loadMarkers()
        
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val areasSet = prefs.getStringSet(AREAS_KEY, emptySet()) ?: emptySet()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        
        val oldFarms = prefs.getStringSet("Farms", emptySet()) ?: emptySet()
        for (farmData in oldFarms) {
            val parts = farmData.split("|")
            if (parts.size >= 2) {
                drawPolygonOnMap("FARM", parts[0], "", parts[1], "")
            }
        }

        for (areaData in areasSet) {
            val parts = areaData.split("|")
            if (parts.size >= 4) {
                val type = parts[0]
                val name = parts[1]
                val notes = parts[2]
                val pointsStr = parts[3]
                val dateStr = if (parts.size >= 5) {
                    val ts = parts[4].toLongOrNull()
                    if (ts != null) dateFormat.format(Date(ts)) else ""
                } else ""
                drawPolygonOnMap(type, name, notes, pointsStr, dateStr)
            }
        }
    }

    private fun drawPolygonOnMap(type: String, name: String, notes: String, pointsStr: String, dateStr: String) {
        val points = pointsStr.split(";").mapNotNull {
            val coords = it.split(",")
            if (coords.size == 2) {
                val lat = coords[0].toDoubleOrNull()
                val lng = coords[1].toDoubleOrNull()
                if (lat != null && lng != null) LatLng(lat, lng) else null
            } else null
        }
        
        if (points.isNotEmpty()) {
            val color = when (type) {
                "FARM" -> Color.GREEN
                "SOIL" -> Color.parseColor("#8B4513")
                "PLANTS" -> Color.parseColor("#006400")
                else -> Color.GRAY
            }
            
            mMap.addPolygon(PolygonOptions()
                .addAll(points)
                .strokeColor(color)
                .fillColor(Color.argb(50, Color.red(color), Color.green(color), Color.blue(color)))
                .clickable(false))
            
            val snippetText = if (dateStr.isNotEmpty()) getString(R.string.created_at, dateStr) + "\n$notes" else notes
            mMap.addMarker(MarkerOptions()
                .position(points[0])
                .title("$name ($type)")
                .snippet(snippetText))
        }
    }

    private fun loadMarkers() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val markersSet = prefs.getStringSet(MARKERS_KEY, emptySet()) ?: emptySet()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        
        for (markerData in markersSet) {
            val parts = markerData.split("|")
            if (parts.size >= 3) {
                val lat = parts[0].toDoubleOrNull()
                val lng = parts[1].toDoubleOrNull()
                val note = parts[2]
                val dateStr = if (parts.size >= 4) {
                    val timestamp = parts[3].toLongOrNull()
                    if (timestamp != null) dateFormat.format(Date(timestamp)) else ""
                } else ""
                
                if (lat != null && lng != null) {
                    mMap.addMarker(MarkerOptions()
                        .position(LatLng(lat, lng))
                        .title(note)
                        .snippet(if (dateStr.isNotEmpty()) getString(R.string.created_at, dateStr) else ""))
                }
            }
        }
    }

    private fun showAddMarkerDialog(latLng: LatLng) {
        val input = EditText(requireContext())
        input.hint = getString(R.string.note_hint)
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.add_farm_note))
            .setView(input)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val note = input.text.toString()
                if (note.isNotBlank()) {
                    saveMarker(latLng, note)
                    loadAllData()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun saveMarker(latLng: LatLng, note: String) {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val markersSet = prefs.getStringSet(MARKERS_KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
        val timestamp = System.currentTimeMillis()
        val markerData = "${latLng.latitude}|${latLng.longitude}|$note|$timestamp"
        markersSet.add(markerData)
        prefs.edit().putStringSet(MARKERS_KEY, markersSet).apply()
    }

    private fun enableMyLocation() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        mMap.isMyLocationEnabled = true
    }
}
