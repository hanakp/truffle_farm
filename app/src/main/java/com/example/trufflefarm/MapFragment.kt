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
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polygon
import com.google.android.gms.maps.model.PolygonOptions
import java.util.Locale

class MapFragment : Fragment(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private val PREFS_NAME = "TruffleFarmPrefs"
    private val MARKERS_KEY = "Markers"
    private val FARMS_KEY = "Farms"

    private var isDrawingArea = false
    private val currentAreaPoints = mutableListOf<LatLng>()
    private var currentPolygon: Polygon? = null

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
            val addressList = geocoder.getFromLocationName(location, 1)
            if (addressList != null && addressList.isNotEmpty()) {
                val address = addressList[0]
                val latLng = LatLng(address.latitude, address.longitude)
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 12f))
                
                val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(view?.windowToken, 0)
            } else {
                Toast.makeText(requireContext(), "Location not found", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error searching: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupDrawingControls(view: View) {
        val controls = view.findViewById<View>(R.id.drawing_controls)
        val btnUndo = view.findViewById<Button>(R.id.btn_undo)
        val btnClear = view.findViewById<Button>(R.id.btn_clear)
        val btnFinish = view.findViewById<Button>(R.id.btn_finish_area)

        btnUndo.setOnClickListener {
            if (currentAreaPoints.isNotEmpty()) {
                currentAreaPoints.removeAt(currentAreaPoints.size - 1)
                updatePolygon()
            }
        }

        btnClear.setOnClickListener {
            currentAreaPoints.clear()
            updatePolygon()
        }

        btnFinish.setOnClickListener {
            if (currentAreaPoints.size >= 3) {
                showSaveFarmDialog()
            } else {
                Toast.makeText(requireContext(), "Select at least 3 points", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.mapType = GoogleMap.MAP_TYPE_HYBRID
        enableMyLocation()
        loadMarkers()
        loadFarms()

        mMap.setOnMapLongClickListener { latLng ->
            if (!isDrawingArea) {
                showAddOptionsDialog(latLng)
            }
        }

        mMap.setOnMapClickListener { latLng ->
            if (isDrawingArea) {
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
        updatePolygon()
    }

    private fun updatePolygon() {
        currentPolygon?.remove()
        if (currentAreaPoints.isNotEmpty()) {
            currentPolygon = mMap.addPolygon(PolygonOptions()
                .addAll(currentAreaPoints)
                .strokeColor(Color.RED)
                .fillColor(Color.argb(70, 255, 0, 0)))
        }
    }

    private fun startDrawingMode() {
        isDrawingArea = true
        currentAreaPoints.clear()
        view?.findViewById<View>(R.id.drawing_controls)?.visibility = View.VISIBLE
        Toast.makeText(requireContext(), "Tap on map to draw farm boundaries", Toast.LENGTH_LONG).show()
    }

    private fun showAddOptionsDialog(latLng: LatLng) {
        val options = arrayOf("Add Note/Marker", "Define Farm Area (Draw)")
        AlertDialog.Builder(requireContext())
            .setTitle("Select Action")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showAddMarkerDialog(latLng)
                    1 -> startDrawingMode()
                }
            }
            .show()
    }

    private fun showSaveFarmDialog() {
        val input = EditText(requireContext())
        input.hint = "Farm Name (e.g., North Hill Farm)"
        AlertDialog.Builder(requireContext())
            .setTitle("New Farm Area")
            .setMessage("Name this area:")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString()
                if (name.isNotBlank()) {
                    saveFarm(name, currentAreaPoints)
                    exitDrawingMode()
                    loadFarms()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun exitDrawingMode() {
        isDrawingArea = false
        view?.findViewById<View>(R.id.drawing_controls)?.visibility = View.GONE
        currentAreaPoints.clear()
        currentPolygon?.remove()
        currentPolygon = null
    }

    private fun saveFarm(name: String, points: List<LatLng>) {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val farmsSet = prefs.getStringSet(FARMS_KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
        val pointsString = points.joinToString(";") { "${it.latitude},${it.longitude}" }
        val farmData = "$name|$pointsString"
        farmsSet.add(farmData)
        prefs.edit().putStringSet(FARMS_KEY, farmsSet).apply()
    }

    private fun loadFarms() {
        mMap.clear()
        loadMarkers()
        
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val farmsSet = prefs.getStringSet(FARMS_KEY, emptySet()) ?: emptySet()
        for (farmData in farmsSet) {
            val parts = farmData.split("|")
            if (parts.size >= 2) {
                val name = parts[0]
                val pointsStr = parts[1]
                val points = pointsStr.split(";").mapNotNull {
                    val coords = it.split(",")
                    if (coords.size == 2) {
                        val lat = coords[0].toDoubleOrNull()
                        val lng = coords[1].toDoubleOrNull()
                        if (lat != null && lng != null) LatLng(lat, lng) else null
                    } else null
                }
                if (points.isNotEmpty()) {
                    mMap.addPolygon(PolygonOptions()
                        .addAll(points)
                        .strokeColor(Color.GREEN)
                        .fillColor(Color.argb(50, 0, 255, 0))
                        .clickable(true))
                    
                    mMap.addMarker(MarkerOptions()
                        .position(points[0])
                        .title(name))
                }
            }
        }
    }

    private fun loadMarkers() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val markersSet = prefs.getStringSet(MARKERS_KEY, emptySet()) ?: emptySet()
        for (markerData in markersSet) {
            val parts = markerData.split("|")
            if (parts.size >= 3) {
                val lat = parts[0].toDoubleOrNull()
                val lng = parts[1].toDoubleOrNull()
                val note = parts[2]
                if (lat != null && lng != null) {
                    addMarkerToMap(LatLng(lat, lng), note, save = false)
                }
            }
        }
    }

    private fun showAddMarkerDialog(latLng: LatLng) {
        val input = EditText(requireContext())
        input.hint = "e.g., Oak tree with black truffles"
        AlertDialog.Builder(requireContext())
            .setTitle("Add Farm Note")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val note = input.text.toString()
                if (note.isNotBlank()) {
                    addMarkerToMap(latLng, note, save = true)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addMarkerToMap(latLng: LatLng, note: String, save: Boolean) {
        mMap.addMarker(MarkerOptions()
            .position(latLng)
            .title(note)
            .snippet("Lat: ${String.format(Locale.US, "%.5f", latLng.latitude)}, Lng: ${String.format(Locale.US, "%.5f", latLng.longitude)}")
        )
        if (save) {
            saveMarker(latLng, note)
        }
    }

    private fun saveMarker(latLng: LatLng, note: String) {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val markersSet = prefs.getStringSet(MARKERS_KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
        val markerData = "${latLng.latitude}|${latLng.longitude}|$note"
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
