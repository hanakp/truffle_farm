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
        val view = inflater.inflate(R.layout.fragment_list, container, false)
        view.findViewById<TextView>(R.id.list_title).text = "All Notes"
        
        val listView = view.findViewById<ListView>(R.id.list_view)
        val notes = loadNotes()
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, notes.map { it.note })
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val selectedNote = notes[position]
            listener?.onNoteSelected(selectedNote.lat, selectedNote.lng)
        }

        return view
    }

    private fun loadNotes(): List<Note> {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val markersSet = prefs.getStringSet(MARKERS_KEY, emptySet()) ?: emptySet()
        return markersSet.mapNotNull {
            val parts = it.split("|")
            if (parts.size >= 3) {
                val lat = parts[0].toDoubleOrNull()
                val lng = parts[1].toDoubleOrNull()
                val note = parts[2]
                if (lat != null && lng != null) {
                    Note(note, lat, lng)
                } else null
            } else null
        }
    }

    data class Note(val note: String, val lat: Double, val lng: Double)
}
