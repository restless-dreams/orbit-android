package com.hyuchia.orbit.widget

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.res.XmlResourceParser
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hyuchia.orbit.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

/** The map widget as the launcher reads it from the app: how it's set up as it's placed. */
@RunWith(AndroidJUnit4::class)
class MapWidgetInfoTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    /** The widget's details, as built into the app, at their one element. */
    private fun details(): XmlResourceParser = context.resources.getXml(R.xml.map_widget_info).apply {
        while (next() != XmlPullParser.START_TAG) {
            continue
        }
    }

    @Test
    fun aWidgetAddedFromTheLaunchersListAsksWhichMapToShow() {
        val details = details()
        val features = details.getAttributeIntValue(ANDROID, "widgetFeatures", 0)

        assertEquals(MapWidgetConfigureActivity::class.java.name, details.getAttributeValue(ANDROID, "configure"))
        // Its choice of map isn't one the launcher may skip as the widget is placed (Android 12 and later would)...
        assertEquals(0, features and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL)
        // ...and its settings change it later.
        assertEquals(AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE, features and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE)
    }

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
    }
}
