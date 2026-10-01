package unicode.sinhala.keyboard.clipboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import unicode.sinhala.com.R

class ClipboardAdapter(
    private val onItemClick: (ClipboardItem) -> Unit,
    private val onPinClick: (ClipboardItem) -> Unit,
    private val onDeleteClick: (ClipboardItem) -> Unit
) : RecyclerView.Adapter<ClipboardAdapter.ViewHolder>() {

    private val items = mutableListOf<ClipboardItem>()

    fun submitList(newList: List<ClipboardItem>) {
        items.clear()
        items.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_clipboard_entry, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvText: TextView = itemView.findViewById(R.id.tv_clip_text)
        private val btnPin: ImageView = itemView.findViewById(R.id.btn_pin)
        private val btnDelete: ImageView = itemView.findViewById(R.id.btn_delete)

        fun bind(item: ClipboardItem) {
            tvText.text = item.text

            if (item.isPinned) {
                btnPin.setImageResource(R.drawable.ic_pin_filled)
            } else {
                btnPin.setImageResource(R.drawable.ic_pin)
            }

            itemView.setOnClickListener { onItemClick(item) }
            btnPin.setOnClickListener { onPinClick(item) }
            btnDelete.setOnClickListener { onDeleteClick(item) }
        }
    }
}
