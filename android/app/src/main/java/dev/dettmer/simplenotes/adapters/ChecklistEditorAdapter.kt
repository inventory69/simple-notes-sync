package dev.dettmer.simplenotes.adapters

import android.graphics.Paint
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.checkbox.MaterialCheckBox
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.models.ChecklistItem

/**
 * Adapter für die Bearbeitung von Checklist-Items im Editor
 * 
 * v1.4.0: Checklisten-Feature
 */
class ChecklistEditorAdapter(
    private val items: MutableList<ChecklistItem>,
    private val onItemCheckedChanged: (Int, Boolean) -> Unit,
    private val onItemTextChanged: (Int, String) -> Unit,
    private val onItemDeleted: (Int) -> Unit,
    private val onAddNewItem: (Int) -> Unit,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit
) : RecyclerView.Adapter<ChecklistEditorAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dragHandle: ImageView = view.findViewById(R.id.ivDragHandle)
        val checkbox: MaterialCheckBox = view.findViewById(R.id.cbItem)
        val editText: EditText = view.findViewById(R.id.etItemText)
        val deleteButton: ImageButton = view.findViewById(R.id.btnDeleteItem)
        
        private var textWatcher: TextWatcher? = null
        
        @Suppress("NestedBlockDepth", "UNUSED_PARAMETER")
        fun bind(item: ChecklistItem, position: Int) {
            // Vorherigen TextWatcher entfernen um Loops zu vermeiden
            textWatcher?.let { editText.removeTextChangedListener(it) }
            
            // Checkbox
            checkbox.isChecked = item.isChecked
            checkbox.setOnCheckedChangeListener { _, isChecked ->
                onItemCheckedChanged(bindingAdapterPosition, isChecked)
                updateStrikethrough(isChecked)
            }
            
            // Text
            editText.setText(item.text)
            updateStrikethrough(item.isChecked)
            
            // v1.4.1: TextWatcher für Änderungen + Enter-Erkennung für neues Item
            textWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val pos = bindingAdapterPosition
                    if (pos == RecyclerView.NO_POSITION) return
                    
                    val text = s?.toString() ?: ""
                    
                    // Prüfe ob ein Newline eingegeben wurde
                    if (text.contains("\n")) {
                        // Newline entfernen und neues Item erstellen
                        val cleanText = text.replace("\n", "")
                        editText.setText(cleanText)
                        editText.setSelection(cleanText.length)
                        onItemTextChanged(pos, cleanText)
                        onAddNewItem(pos + 1)
                    } else {
                        onItemTextChanged(pos, text)
                    }
                }
            }
            editText.addTextChangedListener(textWatcher)
            
            // Delete Button
            deleteButton.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onItemDeleted(pos)
                }
            }
            
            // Drag Handle Touch Listener
            dragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    onStartDrag(this)
                }
                false
            }
        }
        
        private fun updateStrikethrough(isChecked: Boolean) {
            if (isChecked) {
                editText.paintFlags = editText.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                editText.alpha = CHECKED_ITEM_ALPHA
            } else {
                editText.paintFlags = editText.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                editText.alpha = UNCHECKED_ITEM_ALPHA
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_checklist_editor, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size

    /**
     * Bewegt ein Item von einer Position zu einer anderen (für Drag & Drop)
     */
    fun moveItem(fromPosition: Int, toPosition: Int) {
        val item = items.removeAt(fromPosition)
        items.add(toPosition, item)
        notifyItemMoved(fromPosition, toPosition)
        
        // Order-Werte aktualisieren
        items.forEachIndexed { index, checklistItem ->
            checklistItem.order = index
        }
    }
    
    /**
     * Entfernt ein Item an der angegebenen Position
     */
    fun removeItem(position: Int) {
        if (position in items.indices) {
            items.removeAt(position)
            notifyItemRemoved(position)
            // Order-Werte aktualisieren
            items.forEachIndexed { index, checklistItem ->
                checklistItem.order = index
            }
        }
    }
    
    /**
     * Fügt ein neues Item an der angegebenen Position ein
     */
    fun insertItem(position: Int, item: ChecklistItem) {
        items.add(position, item)
        notifyItemInserted(position)
        // Order-Werte aktualisieren
        items.forEachIndexed { index, checklistItem ->
            checklistItem.order = index
        }
    }
    
    /**
     * Fokussiert das EditText des Items an der angegebenen Position
     */
    fun focusItem(recyclerView: RecyclerView, position: Int) {
        recyclerView.post {
            val viewHolder = recyclerView.findViewHolderForAdapterPosition(position) as? ViewHolder
            viewHolder?.editText?.requestFocus()
        }
    }

    companion object {
        /** Alpha-Wert für abgehakte Items (durchgestrichen) */
        private const val CHECKED_ITEM_ALPHA = 0.6f
        /** Alpha-Wert für nicht abgehakte Items */
        private const val UNCHECKED_ITEM_ALPHA = 1.0f
    }
}
