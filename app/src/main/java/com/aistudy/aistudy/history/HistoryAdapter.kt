package com.aistudy.aistudy.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.aistudy.aistudy.R
import com.aistudy.aistudy.database.QuestionAnswer

class HistoryAdapter(
    private val listener: Listener,
    private val labelProvider: SubjectLabelProvider,
    private val timeProvider: RelativeTimeProvider
) : RecyclerView.Adapter<HistoryAdapter.Holder>() {

    interface Listener {
        fun onItemClick(qa: QuestionAnswer)

        fun onStarClick(qa: QuestionAnswer)
    }

    private val items = mutableListOf<QuestionAnswer>()

    fun interface SubjectLabelProvider {
        fun label(subject: String?): String
    }

    fun interface RelativeTimeProvider {
        fun format(createdAt: Long): String
    }

    fun submitList(list: List<QuestionAnswer>?) {
        items.clear()
        if (list != null) {
            items.addAll(list)
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val qa = items[position]
        holder.tvQuestion.text = qa.question
        holder.tvSubject.text = labelProvider.label(qa.subject)
        holder.tvTime.text = timeProvider.format(qa.createdAt)
        val starColor = if (qa.starred) {
            ContextCompat.getColor(holder.itemView.context, R.color.streak)
        } else {
            ContextCompat.getColor(holder.itemView.context, R.color.auth_muted_foreground)
        }
        holder.tvStar.setTextColor(starColor)
        holder.itemView.setOnClickListener { listener.onItemClick(qa) }
        holder.tvStar.setOnClickListener { listener.onStarClick(qa) }
    }

    override fun getItemCount(): Int = items.size

    class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvQuestion: TextView = itemView.findViewById(R.id.tv_question)
        val tvStar: TextView = itemView.findViewById(R.id.tv_star)
        val tvSubject: TextView = itemView.findViewById(R.id.tv_subject)
        val tvTime: TextView = itemView.findViewById(R.id.tv_time)
    }
}
