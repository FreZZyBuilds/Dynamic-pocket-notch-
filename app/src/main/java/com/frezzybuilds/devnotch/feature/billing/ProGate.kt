package com.frezzybuilds.devnotch.feature.billing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frezzybuilds.devnotch.appContainer

/**
 * Zeigt [content] nur mit Pro; sonst eine Sperr-Karte, die die Paywall in der MainActivity
 * öffnet. [onLeave] klappt die Notch ein, damit die Activity nicht verdeckt wird.
 */
@Composable
fun ProGate(
    feature: ProFeature,
    onLeave: () -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val isPro by context.appContainer.proAccess.isPro.collectAsStateWithLifecycle()
    if (isPro) {
        content()
    } else {
        LockedFeatureCard(
            feature = feature,
            onUnlock = {
                Paywall.open(context, feature)
                onLeave()
            },
            modifier = modifier
        )
    }
}
