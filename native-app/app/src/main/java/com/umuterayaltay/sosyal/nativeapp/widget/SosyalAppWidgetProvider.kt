package com.umuterayaltay.sosyal.nativeapp.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.umuterayaltay.sosyal.nativeapp.MainActivity
import com.umuterayaltay.sosyal.nativeapp.R

/**
 * Ana ekran widget'ı (2026-08-21) — res/xml/app_widget_info.xml +
 * res/layout/widget_layout.xml ile birlikte. TEK sorumluluğu: "Yeni Gönderi"
 * butonuna tıklanınca MainActivity'yi App Shortcuts'la AYNI "shortcut_route"
 * extra'sıyla (bkz. MainActivity.consumeDeepLinkRoute() allowlist'i) açmak.
 * Dinamik veri/periyodik güncelleme YOK (bkz. app_widget_info.xml
 * updatePeriodMillis="0" yorumu) — bu yüzden onUpdate() dışında hiçbir
 * callback'e (onDeleted/onEnabled/onDisabled) ihtiyaç yok, varsayılan no-op
 * davranışları yeterli.
 */
class SosyalAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra("shortcut_route", "createPost")
                // FLAG_ACTIVITY_NEW_TASK — widget'lar bir Activity context'inden
                // DEĞİL, AppWidgetProvider (BroadcastReceiver) bağlamından
                // PendingIntent kuruyor, bu bayrak olmadan launchIntent
                // reddedilir/uyarı loglar.
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_new_post_button, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
