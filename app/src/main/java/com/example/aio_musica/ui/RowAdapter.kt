package com.example.aio_musica.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.aio_musica.R

/**
 * One line in a list: a title, a smaller subtitle under it, and an action button on the right.
 * @param key identifies the row (a playlist name or a song id) and is handed back in the callbacks
 */
data class Row(
    val key: String,
    val title: String,
    val subtitle: String
)

/**
 * Shows a list of [Row]s. Used for both the list of playlists and the songs inside one playlist.
 * @param actionLabel text of the button on the right of each row (e.g. "Delete" or "Remove")
 * @param onRowClick called with the row's key when the row itself is tapped, or null if rows aren't tappable
 * @param onActionClick called with the row's key when the action button is tapped
 */
class RowAdapter(
    private val actionLabel: String,
    private val onRowClick: ((String) -> Unit)?,
    private val onActionClick: (String) -> Unit
) : ListAdapter<Row, RowAdapter.Holder>(DIFF) {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.rowTitle)
        val subtitle: TextView = view.findViewById(R.id.rowSubtitle)
        val action: Button = view.findViewById(R.id.rowAction)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_row, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = getItem(position)

        holder.title.text = row.title
        holder.subtitle.text = row.subtitle
        holder.action.text = actionLabel
        holder.action.setOnClickListener { onActionClick(row.key) }

        // setOnClickListener always marks a view clickable, so set isClickable afterwards.
        holder.itemView.setOnClickListener(onRowClick?.let { callback ->
            View.OnClickListener { callback(row.key) }
        })
        holder.itemView.isClickable = onRowClick != null
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Row>() {
            override fun areItemsTheSame(oldItem: Row, newItem: Row) = oldItem.key == newItem.key
            override fun areContentsTheSame(oldItem: Row, newItem: Row) = oldItem == newItem
        }
    }
}
