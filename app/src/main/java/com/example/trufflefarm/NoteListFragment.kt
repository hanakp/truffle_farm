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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NoteListFragment : Fragment() {

    private val PREFS_NAME = "TruffleFarmPrefs"
    private val MARKERS_KEY = "Markers"

    interface OnNoteSelectedListener {
        fun onNoteSelected(lat: Double, lng: Double)
    }

    private var listener: OnNoteSelectedListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnNoteSelectedListener) {
            listener = context
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_notes, container, false)
        view.findViewById<TextView>(R.id.list_title).text = getString(R.string.title_all_notes)
        
        val listView = view.findViewById<ListView>(R.id.list_view_notes)
        val notes = loadNotes()
        
        val customAdapter = object : ArrayAdapter<Note>(requireContext(), android.R.layout.simple_list_item_2, android.R.id.text1, notes) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = super.getView(position, convertView, parent)
                val note = getItem(position)
                row.findViewById<TextView>(android.R.id.text1).text = note?.note
                row.findViewById<TextView>(android.R.id.text2).text = note?.date
                return row
            }
        }
        listView.adapter = customAdapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val selectedNote = notes[position]
            listener?.onNoteSelected(selectedNote.lat, selectedNote.lng)
        }

        return view
    }

    private fun loadNotes(): List<Note> {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val markersSet = prefs.getStringSet(MARKERS_KEY, emptySet()) ?: emptySet()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        
        return markersSet.mapNotNull {
            val parts = it.split("|")
            if (parts.size >= 3) {
                val lat = parts[0].toDoubleOrNull()
                val lng = parts[1].toDoubleOrNull()
                val noteStr = parts[2]
                val dateStr = if (parts.size >= 4) {
                    val timestamp = parts[3].toLongOrNull()
                    if (timestamp != null) {
                        getString(R.string.created_at, dateFormat.format(Date(timestamp)))
                    } else getString(R.string.old_note)
                } else getString(R.string.old_note)
                
                if (lat != null && lng != null) {
                    Note(noteStr, lat, lng, dateStr)
                } else null
            } else null
        }
    }

    data class Note(val note: String, val lat: Double, val lng: Double, val date: String)
}
