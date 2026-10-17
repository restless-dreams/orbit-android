package com.hyuchia.orbit.data

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import com.hyuchia.orbit.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A map to start from, in the app's assets under `templates/`, with its name and what it's for, all in the app's language. */
data class MapTemplate(val file: String, @StringRes val titleResource: Int, @StringRes val descriptionResource: Int) {
    val title: String
        @Composable @ReadOnlyComposable get() = stringResource(titleResource)

    val description: String
        @Composable @ReadOnlyComposable get() = stringResource(descriptionResource)
}

val MapTemplates = listOf(
    MapTemplate("brainstorm.md", R.string.data_template_brainstorm_title, R.string.data_template_brainstorm_description),
    MapTemplate("project-plan.md", R.string.data_template_project_plan_title, R.string.data_template_project_plan_description),
    MapTemplate("study-notes.md", R.string.data_template_study_notes_title, R.string.data_template_study_notes_description),
    MapTemplate("talk.md", R.string.data_template_talk_outline_title, R.string.data_template_talk_outline_description),
    MapTemplate("meeting.md", R.string.data_template_meeting_notes_title, R.string.data_template_meeting_notes_description),
    MapTemplate("decision.md", R.string.data_template_decision_title, R.string.data_template_decision_description),
    MapTemplate("goals.md", R.string.data_template_goals_title, R.string.data_template_goals_description),
    MapTemplate("swot.md", R.string.data_template_swot_analysis_title, R.string.data_template_swot_analysis_description),
    MapTemplate("book.md", R.string.data_template_book_notes_title, R.string.data_template_book_notes_description),
    MapTemplate("trip.md", R.string.data_template_trip_planner_title, R.string.data_template_trip_planner_description),
    MapTemplate("week.md", R.string.data_template_week_plan_title, R.string.data_template_week_plan_description),
)

/** A template's text, in the language the app is in when that language has its own (see [openAsset]). */
suspend fun Context.readTemplate(template: MapTemplate): String = withContext(Dispatchers.IO) {
    openAsset("templates/${template.file}").bufferedReader().use { it.readText() }
}
