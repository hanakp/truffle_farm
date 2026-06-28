package com.example.trufflefarm

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import java.io.File

class GalleryFragment : Fragment() {

    private val firestoreManager = FirestoreManager()

    interface OnPhotoSelectedListener {
        fun onPhotoLocationSelected(lat: Double, lng: Double)
    }

    private var listener: OnPhotoSelectedListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnPhotoSelectedListener) {
            listener = context
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_gallery, container, false)
        val gridView = view.findViewById<GridView>(R.id.grid_view_gallery)
        
        loadPhotosFromCloud { photoItems ->
            if (isAdded) {
                gridView.adapter = PhotoAdapter(requireContext(), photoItems) { item ->
                    showPhotoDetailDialog(item)
                }
            }
        }
        
        return view
    }

    private fun loadPhotosFromCloud(onComplete: (List<PhotoItem>) -> Unit) {
        val photos = mutableListOf<PhotoItem>()
        
        firestoreManager.getMarkers { markers ->
            for (data in markers) {
                val path = data["photoPath"] as? String ?: ""
                val lat = data["lat"] as? Double
                val lng = data["lng"] as? Double
                val note = data["note"] as? String ?: ""
                if (path.isNotEmpty() && lat != null && lng != null) {
                    photos.add(PhotoItem(path, note, lat, lng))
                }
            }
            
            firestoreManager.getAreas { areas ->
                for (data in areas) {
                    val path = data["photoPath"] as? String ?: ""
                    val name = data["name"] as? String ?: ""
                    val type = data["type"] as? String ?: ""
                    val pointsStr = data["points"] as? String ?: ""
                    val coords = pointsStr.split(";").firstOrNull()?.split(",")
                    if (path.isNotEmpty() && coords?.size == 2) {
                        val lat = coords[0].toDoubleOrNull()
                        val lng = coords[1].toDoubleOrNull()
                        if (lat != null && lng != null) {
                            photos.add(PhotoItem(path, "$name ($type)", lat, lng))
                        }
                    }
                }
                onComplete(photos)
            }
        }
    }

    private fun showPhotoDetailDialog(item: PhotoItem) {
        val imageView = ImageView(requireContext())
        Glide.with(this).load(item.path).into(imageView)
        imageView.adjustViewBounds = true
        
        AlertDialog.Builder(requireContext())
            .setTitle(item.title)
            .setView(imageView)
            .setPositiveButton(R.string.nav_map) { _, _ ->
                listener?.onPhotoLocationSelected(item.lat, item.lng)
            }
            .setNegativeButton(android.R.string.ok, null)
            .show()
    }

    data class PhotoItem(val path: String, val title: String, val lat: Double, val lng: Double)

    class PhotoAdapter(
        private val context: Context,
        private val items: List<PhotoItem>,
        private val onClick: (PhotoItem) -> Unit
    ) : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val imageView = (convertView as? ImageView) ?: ImageView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    300
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
                setPadding(4, 4, 4, 4)
            }
            
            val item = getItem(position)
            Glide.with(context)
                .load(item.path)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_report_image)
                .into(imageView)

            imageView.setOnClickListener { onClick(item) }
            
            return imageView
        }
    }
}
