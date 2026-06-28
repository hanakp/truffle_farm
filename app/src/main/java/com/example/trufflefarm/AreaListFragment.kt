package com.example.trufflefarm

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.maps.android.PolyUtil
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AreaListFragment : Fragment() {

    private val firestoreManager = FirestoreManager()

    interface OnAreaSelectedListener {
        fun onAreaSelected(lat: Double, lng: Double)
        fun onAddNewRequested()
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
        val view = inflater.inflate(R.layout.fragment_notes, container, false)
        view.findViewById<TextView>(R.id.list_title).text = getString(R.string.title_farms_zones)
        
        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view_tree)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        
        val fab = view.findViewById<FloatingActionButton>(R.id.fab_add)
        fab.setOnClickListener {
            listener?.onAddNewRequested()
        }

        loadDataAndBuildTree(recyclerView)

        return view
    }

    private fun loadDataAndBuildTree(recyclerView: RecyclerView) {
        firestoreManager.getAreas { areas ->
            if (!isAdded) return@getAreas
            firestoreManager.getMarkers { markers ->
                if (!isAdded) return@getMarkers
                
                val treeItems = buildTree(areas, markers)
                recyclerView.adapter = TreeAdapter(treeItems) { item ->
                    handleItemClick(item)
                }
            }
        }
    }

    private fun buildTree(rawAreas: List<Map<String, Any>>, rawMarkers: List<Map<String, Any>>): List<TreeAdapter.TreeItem> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        
        val allAreas = rawAreas.map { data ->
            val pointsStr = data["points"] as String
            val points = parsePoints(pointsStr)
            Area(
                data["name"] as String,
                data["type"] as String,
                data["notes"] as String,
                points,
                (data["timestamp"] as? Long)?.let { dateFormat.format(Date(it)) } ?: "",
                data["photoPath"] as? String ?: ""
            )
        }

        val allNotes = rawMarkers.map { data ->
            Note(
                data["note"] as String,
                data["lat"] as Double,
                data["lng"] as Double,
                (data["timestamp"] as? Long)?.let { dateFormat.format(Date(it)) } ?: "",
                data["photoPath"] as? String ?: ""
            )
        }

        val tree = mutableListOf<TreeAdapter.TreeItem>()
        
        val farms = allAreas.filter { it.type == "FARM" }
        val soilAreas = allAreas.filter { it.type == "SOIL" }
        val plantAreas = allAreas.filter { it.type == "PLANTS" }

        val usedSoil = mutableSetOf<Area>()
        val usedPlants = mutableSetOf<Area>()
        val usedNotes = mutableSetOf<Note>()

        for (farm in farms) {
            tree.add(farm.toTreeItem(0))
            
            // 1. Soil Areas in this Farm
            val farmSoil = soilAreas.filter { PolyUtil.containsLocation(it.points[0], farm.points, false) }
            for (soil in farmSoil) {
                tree.add(soil.toTreeItem(1))
                usedSoil.add(soil)
                
                // 1a. Plants in this Soil
                val soilPlants = plantAreas.filter { PolyUtil.containsLocation(it.points[0], soil.points, false) }
                for (plant in soilPlants) {
                    tree.add(plant.toTreeItem(2))
                    usedPlants.add(plant)
                    
                    // 1ai. Notes in this Plant Area
                    val plantNotes = allNotes.filter { PolyUtil.containsLocation(LatLng(it.lat, it.lng), plant.points, false) }
                    for (note in plantNotes) {
                        tree.add(note.toTreeItem(3))
                        usedNotes.add(note)
                    }
                }
                
                // 1b. Notes in this Soil (but not in a plant area)
                val soilNotes = allNotes.filter { 
                    it !in usedNotes && PolyUtil.containsLocation(LatLng(it.lat, it.lng), soil.points, false) 
                }
                for (note in soilNotes) {
                    tree.add(note.toTreeItem(2))
                    usedNotes.add(note)
                }
            }

            // 2. Plants in this Farm (but not in a specific soil area)
            val farmPlants = plantAreas.filter { 
                it !in usedPlants && PolyUtil.containsLocation(it.points[0], farm.points, false) 
            }
            for (plant in farmPlants) {
                tree.add(plant.toTreeItem(1))
                usedPlants.add(plant)
                
                // 2a. Notes in this Plant Area
                val plantNotes = allNotes.filter { PolyUtil.containsLocation(LatLng(it.lat, it.lng), plant.points, false) }
                for (note in plantNotes) {
                    tree.add(note.toTreeItem(2))
                    usedNotes.add(note)
                }
            }

            // 3. Notes in this Farm (not in soil or plants)
            val farmNotes = allNotes.filter { 
                it !in usedNotes && PolyUtil.containsLocation(LatLng(it.lat, it.lng), farm.points, false) 
            }
            for (note in farmNotes) {
                tree.add(note.toTreeItem(1))
                usedNotes.add(note)
            }
        }

        // Add "Orphaned" items
        val orphanedSoil = soilAreas.filter { it !in usedSoil }
        val orphanedPlants = plantAreas.filter { it !in usedPlants }
        val orphanedNotes = allNotes.filter { it !in usedNotes }
        
        if (orphanedSoil.isNotEmpty() || orphanedPlants.isNotEmpty() || orphanedNotes.isNotEmpty()) {
            tree.add(TreeAdapter.TreeItem(getString(R.string.other_zones), "NONE", "", 0.0, 0.0, "", "", 0))
            orphanedSoil.forEach { tree.add(it.toTreeItem(1)) }
            orphanedPlants.forEach { tree.add(it.toTreeItem(1)) }
            orphanedNotes.forEach { tree.add(it.toTreeItem(1)) }
        }

        return tree
    }

    private fun handleItemClick(item: TreeAdapter.TreeItem) {
        if (item.photoPath.isNotEmpty()) {
            val options = arrayOf(getString(R.string.nav_map), getString(R.string.view_photo))
            AlertDialog.Builder(requireContext())
                .setItems(options) { _, which ->
                    if (which == 0) listener?.onAreaSelected(item.lat, item.lng)
                    else showPhotoDialog(item.photoPath)
                }.show()
        } else {
            listener?.onAreaSelected(item.lat, item.lng)
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

    data class Area(val name: String, val type: String, val notes: String, val points: List<LatLng>, val date: String, val photoPath: String) {
        fun toTreeItem(level: Int) = TreeAdapter.TreeItem(
            name, type, notes, points.firstOrNull()?.latitude ?: 0.0, points.firstOrNull()?.longitude ?: 0.0, date, photoPath, level
        )
    }

    data class Note(val note: String, val lat: Double, val lng: Double, val date: String, val photoPath: String) {
        fun toTreeItem(level: Int) = TreeAdapter.TreeItem(
            note, "NOTE", "", lat, lng, date, photoPath, level
        )
    }
}
