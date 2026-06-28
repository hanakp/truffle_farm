package com.example.trufflefarm

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MapFragment : Fragment(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val firestoreManager = FirestoreManager()
    private val storageManager = StorageManager()
    
    private val PREFS_NAME = "TruffleFarmPrefs"
    private val COLOR_FARM = "color_farm"
    private val COLOR_SOIL = "color_soil"
    private val COLOR_PLANTS = "color_plants"

    private enum class DrawingMode { NONE, FARM, SOIL, PLANTS }
    private var currentMode = DrawingMode.NONE
    
    private val currentAreaPoints = mutableListOf<LatLng>()
    private var currentPolygon: Polygon? = null
    private val tempMarkers = mutableListOf<Marker>()

    private var currentPhotoPath: String? = null
    private var pendingPhotoLatLng: LatLng? = null
    private var isTakingPhotoForArea = false

    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            if (isTakingPhotoForArea) {
                showSaveAreaDialog(currentPhotoPath)
            } else {
                pendingPhotoLatLng?.let { showAddMarkerDialog(it, currentPhotoPath) }
            }
        }
        isTakingPhotoForArea = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
    }

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
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.select_action)
                    .setItems(arrayOf(getString(R.string.save), getString(R.string.take_photo))) { _, which ->
                        if (which == 0) showSaveAreaDialog()
                        else {
                            isTakingPhotoForArea = true
                            checkCameraPermissionAndTakePhoto(currentAreaPoints[0])
                        }
                    }
                    .show()
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
        
        mMap.setOnInfoWindowClickListener { marker ->
            val photoPath = marker.tag as? String
            if (!photoPath.isNullOrEmpty()) {
                showPhotoDialog(photoPath)
            }
        }

        val args = arguments
        if (args != null && args.containsKey("lat") && args.containsKey("lng")) {
            val lat = args.getDouble("lat")
            val lng = args.getDouble("lng")
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 18f))
        } else if (args != null && args.getBoolean("request_add", false)) {
            zoomToCurrentLocation(true)
        } else {
            zoomToCurrentLocation(false)
        }
    }

    private fun zoomToCurrentLocation(shouldShowDialog: Boolean) {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val currentLatLng = LatLng(location.latitude, location.longitude)
                    val zoomLevel = if (shouldShowDialog) 18f else 15f
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, zoomLevel))
                    if (shouldShowDialog) {
                        showAddOptionsDialog(currentLatLng)
                    }
                }
            }
        }
    }
    
    private fun showPhotoDialog(path: String) {
        val imageView = ImageView(requireContext())
        Glide.with(this).load(path).into(imageView)
        imageView.adjustViewBounds = true
        imageView.setPadding(32, 32, 32, 32)

        AlertDialog.Builder(requireContext())
            .setView(imageView)
            .setPositiveButton(android.R.string.ok, null)
            .show()
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
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return when (mode) {
            DrawingMode.FARM -> prefs.getInt(COLOR_FARM, Color.GREEN)
            DrawingMode.SOIL -> prefs.getInt(COLOR_SOIL, Color.parseColor("#8B4513"))
            DrawingMode.PLANTS -> prefs.getInt(COLOR_PLANTS, Color.parseColor("#006400"))
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
            getString(R.string.take_photo),
            getString(R.string.define_farm_boundary),
            getString(R.string.define_soil_area),
            getString(R.string.define_planting_area)
        )
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.select_action))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showAddMarkerDialog(latLng)
                    1 -> checkCameraPermissionAndTakePhoto(latLng)
                    2 -> startDrawingMode(DrawingMode.FARM)
                    3 -> startDrawingMode(DrawingMode.SOIL)
                    4 -> startDrawingMode(DrawingMode.PLANTS)
                }
            }
            .show()
    }
    
    private fun checkCameraPermissionAndTakePhoto(latLng: LatLng) {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 101)
            pendingPhotoLatLng = latLng
            return
        }
        dispatchTakePictureIntent(latLng)
    }
    
    private fun dispatchTakePictureIntent(latLng: LatLng) {
        val photoFile: File? = try {
            createImageFile()
        } catch (ex: Exception) {
            null
        }
        photoFile?.also {
            val photoURI: Uri = FileProvider.getUriForFile(
                requireContext(),
                "com.example.trufflefarm.fileprovider",
                it
            )
            val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
            pendingPhotoLatLng = latLng
            takePhotoLauncher.launch(takePictureIntent)
        }
    }

    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir: File? = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("TRUFFLE_${timeStamp}_", ".jpg", storageDir).apply {
            currentPhotoPath = absolutePath
        }
    }

    private fun showSaveAreaDialog(photoPath: String? = null) {
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

        val modeNameRes = when(currentMode) {
            DrawingMode.FARM -> R.string.nav_farms
            DrawingMode.SOIL -> R.string.define_soil_area
            DrawingMode.PLANTS -> R.string.define_planting_area
            else -> 0
        }
        val modeName = if (modeNameRes != 0) getString(modeNameRes) else currentMode.name

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.new_area_title, modeName))
            .setView(layout)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val name = nameInput.text.toString()
                val notes = notesInput.text.toString()
                if (name.isNotBlank()) {
                    saveAreaToCloud(currentMode, name, notes, currentAreaPoints, photoPath)
                    exitDrawingMode()
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

    private fun saveAreaToCloud(mode: DrawingMode, name: String, notes: String, points: List<LatLng>, photoPath: String?) {
        val pointsString = points.joinToString(";") { "${it.latitude},${it.longitude}" }
        val timestamp = System.currentTimeMillis()
        
        if (photoPath != null) {
            storageManager.uploadPhoto(photoPath) { url ->
                firestoreManager.saveArea(mode.name, name, notes, pointsString, timestamp, url ?: "", {
                    loadAllData()
                })
            }
        } else {
            firestoreManager.saveArea(mode.name, name, notes, pointsString, timestamp, "", {
                loadAllData()
            })
        }
    }

    private fun loadAllData() {
        mMap.clear()
        
        firestoreManager.getAreas { areas ->
            if (!isAdded) return@getAreas
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            for (data in areas) {
                val type = data["type"] as String
                val name = data["name"] as String
                val notes = data["notes"] as String
                val pointsStr = data["points"] as String
                val timestamp = data["timestamp"] as? Long
                val dateStr = if (timestamp != null) dateFormat.format(Date(timestamp)) else ""
                val photoPath = data["photoPath"] as? String ?: ""
                drawPolygonOnMap(type, name, notes, pointsStr, dateStr, photoPath)
            }
        }

        firestoreManager.getMarkers { markers ->
            if (!isAdded) return@getMarkers
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            for (data in markers) {
                val lat = data["lat"] as Double
                val lng = data["lng"] as Double
                val note = data["note"] as String
                val timestamp = data["timestamp"] as? Long
                val dateStr = if (timestamp != null) dateFormat.format(Date(timestamp)) else ""
                val photoPath = data["photoPath"] as? String ?: ""
                
                val marker = mMap.addMarker(MarkerOptions()
                    .position(LatLng(lat, lng))
                    .title(note)
                    .snippet(if (dateStr.isNotEmpty()) getString(R.string.created_at, dateStr) else "")
                    .icon(if (photoPath.isNotEmpty()) BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE) else null))
                marker?.tag = photoPath
            }
        }
    }

    private fun drawPolygonOnMap(type: String, name: String, notes: String, pointsStr: String, dateStr: String, photoPath: String) {
        val points = pointsStr.split(";").mapNotNull {
            val coords = it.split(",")
            if (coords.size == 2) {
                val lat = coords[0].toDoubleOrNull()
                val lng = coords[1].toDoubleOrNull()
                if (lat != null && lng != null) LatLng(lat, lng) else null
            } else null
        }
        
        if (points.isNotEmpty()) {
            val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val color = when (type) {
                "FARM" -> prefs.getInt(COLOR_FARM, Color.GREEN)
                "SOIL" -> prefs.getInt(COLOR_SOIL, Color.parseColor("#8B4513"))
                "PLANTS" -> prefs.getInt(COLOR_PLANTS, Color.parseColor("#006400"))
                else -> Color.GRAY
            }
            
            mMap.addPolygon(PolygonOptions()
                .addAll(points)
                .strokeColor(color)
                .fillColor(Color.argb(50, Color.red(color), Color.green(color), Color.blue(color)))
                .clickable(false))
        }
    }

    private fun showAddMarkerDialog(latLng: LatLng, photoPath: String? = null) {
        val input = EditText(requireContext())
        input.hint = getString(R.string.note_hint)
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.add_farm_note))
            .setView(input)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val note = input.text.toString()
                if (note.isNotBlank()) {
                    saveMarkerToCloud(latLng, note, photoPath)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun saveMarkerToCloud(latLng: LatLng, note: String, photoPath: String?) {
        val timestamp = System.currentTimeMillis()
        if (photoPath != null) {
            storageManager.uploadPhoto(photoPath) { url ->
                firestoreManager.saveMarker(latLng.latitude, latLng.longitude, note, timestamp, url ?: "", {
                    loadAllData()
                })
            }
        } else {
            firestoreManager.saveMarker(latLng.latitude, latLng.longitude, note, timestamp, "", {
                loadAllData()
            })
        }
    }

    private fun enableMyLocation() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 102)
            return
        }
        mMap.isMyLocationEnabled = true
    }
}
