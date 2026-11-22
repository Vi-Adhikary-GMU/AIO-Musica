package com.example.aio_musica.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.aio_musica.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import kotlin.math.roundToInt

/**
 * Shows one box per stem in the mixer list: icon, name, export button, volume slider and
 * mute button. It never changes data itself; taps are reported through the three callbacks.
 */
class StemAdapter(
    private val onVolumeChange: (String, Float) -> Unit,
    private val onMuteClick: (String) -> Unit,
    private val onExportClick: (StemState) -> Unit
) : ListAdapter<StemState, StemAdapter.Holder>(DIFF) {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.stemIcon)
        val name: TextView = view.findViewById(R.id.stemName)
        val export: ImageButton = view.findViewById(R.id.stemExport)
        val mute: MaterialButton = view.findViewById(R.id.stemMute)
        val slider: Slider = view.findViewById(R.id.stemVolume)

        /** Which stem this box currently shows (boxes get reused). */
        var stemId: String = ""

        /** True while the user's finger is on the slider. */
        var dragging: Boolean = false
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.stem_box, parent, false)
        val holder = Holder(view)

        // Set up the slider once per box, not on every bind.
        holder.slider.setLabelFormatter { value -> "${value.toInt()}%" }
        holder.slider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) onVolumeChange(holder.stemId, value / 100f)
        }
        holder.slider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                holder.dragging = true
            }

            override fun onStopTrackingTouch(slider: Slider) {
                holder.dragging = false
            }
        })
        return holder
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val stem = getItem(position)
        val context = holder.itemView.context

        holder.stemId = stem.id
        holder.name.text = stem.name
        holder.icon.setImageResource(iconFor(stem.name))
        holder.mute.text = context.getString(
            if (stem.muted) R.string.stem_unmute else R.string.stem_mute
        )
        holder.mute.setOnClickListener { onMuteClick(stem.id) }
        holder.export.contentDescription = context.getString(R.string.stem_export_description, stem.name)
        holder.export.setOnClickListener { onExportClick(stem) }

        // Muted stems can't be adjusted; the volume value is kept so unmuting restores it.
        holder.slider.isEnabled = !stem.muted
        holder.slider.alpha = if (stem.muted) 0.4f else 1f

        // If the stem got muted mid-drag, the touch is dropped, so reset the flag.
        if (stem.muted) holder.dragging = false

        if (!holder.dragging) {
            val target = (stem.volume * 100f).roundToInt().toFloat()
            if (holder.slider.value != target) {
                holder.slider.value = target
            }
        }
    }

    /** Picks the icon by matching the stem's name */
    private fun iconFor(stemName: String): Int {
        val n = stemName.lowercase()
        return when {
            "vocal" in n -> R.drawable.vocals_icon
            "instrum" in n -> R.drawable.instrumentals_icon
            "guitar" in n -> R.drawable.guitar_icon
            "bass" in n -> R.drawable.bass_icon
            "drum" in n -> R.drawable.drums_icon
            "piano" in n -> R.drawable.piano_icon
            else -> R.drawable.other_icon
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<StemState>() {
            override fun areItemsTheSame(old: StemState, new: StemState) = old.id == new.id

            // Ignore volume changes: the slider already shows the value the user just set,
            // so re-binding the box on every drag step only risks snapping the thumb back.
            override fun areContentsTheSame(old: StemState, new: StemState) =
                old.copy(volume = new.volume) == new
        }
    }
}