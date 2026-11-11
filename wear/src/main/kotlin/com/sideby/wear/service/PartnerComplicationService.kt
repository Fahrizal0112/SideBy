package com.sideby.wear.service

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.sideby.shared.model.Mood
import com.sideby.shared.model.PartnerStatus
import com.sideby.wear.data.WearDataManager

class PartnerComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        val preview = PartnerStatus(
            name = "Sideby",
            mood = Mood.MISSING_YOU
        )
        return buildComplicationData(type, preview)
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val partnerStatus = WearDataManager.getPartnerStatus(applicationContext)
        return buildComplicationData(request.complicationType, partnerStatus) ?: NoDataComplicationData()
    }

    private fun buildComplicationData(type: ComplicationType, partner: PartnerStatus): ComplicationData? {
        val text = "${partner.mood.emoji} ${partner.phoneBattery.percentage}%"
        val title = partner.name

        return when (type) {
            ComplicationType.SHORT_TEXT -> {
                ShortTextComplicationData.Builder(
                    PlainComplicationText.Builder(text).build(),
                    PlainComplicationText.Builder("Baterai & Mood Pasangan").build()
                )
                .setTitle(PlainComplicationText.Builder(title).build())
                .build()
            }
            ComplicationType.RANGED_VALUE -> {
                RangedValueComplicationData.Builder(
                    value = partner.phoneBattery.percentage.toFloat(),
                    min = 0f,
                    max = 100f,
                    contentDescription = PlainComplicationText.Builder("Baterai ${partner.phoneBattery.percentage}%").build()
                )
                .setText(PlainComplicationText.Builder(partner.mood.emoji).build())
                .setTitle(PlainComplicationText.Builder("${partner.phoneBattery.percentage}%").build())
                .build()
            }
            ComplicationType.LONG_TEXT -> {
                LongTextComplicationData.Builder(
                    PlainComplicationText.Builder("${partner.name}: ${partner.mood.label}").build(),
                    PlainComplicationText.Builder("Status Pasangan").build()
                )
                .setTitle(PlainComplicationText.Builder("🔋 ${partner.phoneBattery.displayString} • ${partner.location.summary}").build())
                .build()
            }
            else -> null
        }
    }
}
