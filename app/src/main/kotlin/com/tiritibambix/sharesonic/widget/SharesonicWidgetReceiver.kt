package com.tiritibambix.sharesonic.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * AppWidget provider entry point declared in the manifest. Glance handles the
 * rest — dispatching AppWidgetManager updates to [SharesonicWidget.provideGlance].
 */
class SharesonicWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SharesonicWidget()
}
