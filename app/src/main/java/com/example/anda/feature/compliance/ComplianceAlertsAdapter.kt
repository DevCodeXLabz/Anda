package com.example.anda.feature.compliance

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.anda.R
import com.example.anda.data.repository.ComplianceAlert
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

class ComplianceAlertsAdapter(
    private val onItemClick: (ComplianceAlert) -> Unit
) : ListAdapter<ComplianceAlert, ComplianceAlertsAdapter.AlertViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlertViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_compliance_alert, parent, false)
        return AlertViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlertViewHolder, position: Int) {
        holder.bind(getItem(position), onItemClick)
    }

    class AlertViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val severityIndicator: View = itemView.findViewById(R.id.severity_indicator)
        private val titleText: TextView = itemView.findViewById(R.id.alert_title)
        private val detailText: TextView = itemView.findViewById(R.id.alert_detail)
        private val metaText: TextView = itemView.findViewById(R.id.alert_meta)
        private val daysBadge: TextView = itemView.findViewById(R.id.alert_days_badge)

        fun bind(alert: ComplianceAlert, onItemClick: (ComplianceAlert) -> Unit) {
            val ctx = itemView.context
            titleText.text = alert.title
            detailText.text = ctx.getString(
                R.string.compliance_alerts_details_template,
                alert.documentType,
                alert.companyCnpj,
                ctx.getString(R.string.compliance_alerts_open_hint)
            )

            val severityLabel = when (alert.severity) {
                "EXPIRED"  -> ctx.getString(R.string.compliance_alerts_severity_expired)
                "CRITICAL" -> ctx.getString(R.string.compliance_alerts_severity_critical)
                "WARNING"  -> ctx.getString(R.string.compliance_alerts_severity_warning)
                else       -> ctx.getString(R.string.compliance_alerts_severity_info)
            }
            val validText = alert.validUntil?.let { DateFormat.getDateInstance().format(Date(it)) } ?: "N/A"
            metaText.text = ctx.getString(R.string.compliance_alerts_meta_template, severityLabel, validText)

            val colorRes = when (alert.severity) {
                "EXPIRED"  -> R.color.status_critical
                "CRITICAL" -> R.color.status_high
                "WARNING"  -> R.color.status_moderate
                else       -> R.color.status_neutral
            }
            val color = ContextCompat.getColor(ctx, colorRes)
            severityIndicator.setBackgroundColor(color)

            // Days badge: show days remaining or overdue
            val now = System.currentTimeMillis()
            val until = alert.validUntil
            if (until != null) {
                val diffMs = until - now
                val days = TimeUnit.MILLISECONDS.toDays(diffMs)
                daysBadge.visibility = View.VISIBLE
                daysBadge.text = when {
                    diffMs < 0 -> ctx.getString(R.string.compliance_alert_overdue, -days)
                    days == 0L -> ctx.getString(R.string.compliance_alert_expires_today)
                    else       -> ctx.getString(R.string.compliance_alert_days_remaining, days)
                }
                // Rounded background matching severity color
                val bg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 8f
                    setColor(color)
                }
                daysBadge.background = bg
            } else {
                daysBadge.visibility = View.GONE
            }

            itemView.setOnClickListener { onItemClick(alert) }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ComplianceAlert>() {
            override fun areItemsTheSame(oldItem: ComplianceAlert, newItem: ComplianceAlert) =
                oldItem.documentId == newItem.documentId
            override fun areContentsTheSame(oldItem: ComplianceAlert, newItem: ComplianceAlert) =
                oldItem == newItem
        }
    }
}
