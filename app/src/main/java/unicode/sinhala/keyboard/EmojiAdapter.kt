package unicode.sinhala.keyboard

import android.R
import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class EmojiAdapter(
    private val context: Context,
    private val clickListener: KeyboardView.ClickListener,
    private val darkTheme: Boolean,
    emojis: List<String>,
    private val textSize: Int
) : RecyclerView.Adapter<EmojiAdapter.EmojiViewHolder>() {

    private val items = ArrayList<String>(emojis)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmojiViewHolder {
        val tv = TextView(context)
        val density = context.resources.displayMetrics.density
        val itemSize = (44 * density).toInt()
        tv.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, itemSize)
        tv.gravity = Gravity.CENTER
        tv.textAlignment = TextView.TEXT_ALIGNMENT_CENTER
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, (textSize * 0.9f).coerceIn(20f, 32f))
        tv.includeFontPadding = false
        tv.setTextColor(if (darkTheme) Color.WHITE else Color.BLACK)
        
        val typedValue = TypedValue()
        context.theme.resolveAttribute(R.attr.selectableItemBackgroundBorderless, typedValue, true)
        tv.setBackgroundResource(typedValue.resourceId)

        return EmojiViewHolder(tv)
    }

    override fun onBindViewHolder(holder: EmojiViewHolder, position: Int) {
        val emoji = items[position]
        holder.textView.text = emoji
        holder.textView.setOnClickListener {
            clickListener.emojiClick(emoji)
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateEmojis(newEmojis: List<String>) {
        items.clear()
        items.addAll(newEmojis)
        notifyDataSetChanged()
    }

    class EmojiViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textView: TextView = itemView as TextView
    }
}
