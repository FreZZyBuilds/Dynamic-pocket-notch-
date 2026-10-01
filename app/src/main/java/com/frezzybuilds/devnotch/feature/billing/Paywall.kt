package com.frezzybuilds.devnotch.feature.billing

import android.content.Context
import android.content.Intent
import com.frezzybuilds.devnotch.MainActivity

/** Öffnet die Paywall in der MainActivity – auch aus dem Overlay heraus. */
object Paywall {
    const val EXTRA_FEATURE = "com.frezzybuilds.devnotch.extra.PAYWALL_FEATURE"

    fun open(context: Context, feature: ProFeature) {
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_FEATURE, feature.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }

    fun featureFrom(intent: Intent?): ProFeature? =
        intent?.getStringExtra(EXTRA_FEATURE)?.let { name -> ProFeature.entries.firstOrNull { it.name == name } }
}
