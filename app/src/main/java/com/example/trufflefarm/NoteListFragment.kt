package com.example.trufflefarm

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

class NoteListFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Just replace with AreaListFragment to show the tree
        val areaListFragment = AreaListFragment()
        parentFragmentManager.beginTransaction()
            .replace(id, areaListFragment)
            .commit()
        return null
    }

    interface OnNoteSelectedListener {
        fun onNoteSelected(lat: Double, lng: Double)
    }
}
