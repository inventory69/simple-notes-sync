package dev.dettmer.simplenotes.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.SyncStatus
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.toReadableTime
import dev.dettmer.simplenotes.utils.truncate

class NotesAdapter(
    private val onNoteClick: (Note) -> Unit
) : ListAdapter<Note, NotesAdapter.NoteViewHolder>(NoteDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_note, parent, false)
        return NoteViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class NoteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textViewTitle: TextView = itemView.findViewById(R.id.textViewTitle)
        private val textViewContent: TextView = itemView.findViewById(R.id.textViewContent)
        private val textViewTimestamp: TextView = itemView.findViewById(R.id.textViewTimestamp)
        private val imageViewSyncStatus: ImageView = itemView.findViewById(R.id.imageViewSyncStatus)
        
        fun bind(note: Note) {
            textViewTitle.text = note.title.ifEmpty { "Ohne Titel" }
            textViewContent.text = note.content.truncate(100)
            textViewTimestamp.text = note.updatedAt.toReadableTime()
            
            // Sync Icon nur zeigen wenn Sync konfiguriert ist
            val prefs = itemView.context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            val serverUrl = prefs.getString(Constants.KEY_SERVER_URL, null)
            val isSyncConfigured = !serverUrl.isNullOrEmpty()
            
            if (isSyncConfigured) {
                // Sync status icon
                val syncIcon = when (note.syncStatus) {
                    SyncStatus.SYNCED -> android.R.drawable.ic_menu_upload
                    SyncStatus.PENDING -> android.R.drawable.ic_popup_sync
                    SyncStatus.CONFLICT -> android.R.drawable.ic_dialog_alert
                    SyncStatus.LOCAL_ONLY -> android.R.drawable.ic_menu_save
                }
                imageViewSyncStatus.setImageResource(syncIcon)
                imageViewSyncStatus.visibility = View.VISIBLE
            } else {
                // Sync nicht konfiguriert → Icon verstecken
                imageViewSyncStatus.visibility = View.GONE
            }
            
            itemView.setOnClickListener {
                onNoteClick(note)
            }
        }
    }
    
    private class NoteDiffCallback : DiffUtil.ItemCallback<Note>() {
        override fun areItemsTheSame(oldItem: Note, newItem: Note): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: Note, newItem: Note): Boolean {
            return oldItem == newItem
        }
    }
}
