package com.hyuchia.orbit.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.hyuchia.orbit.AppContainer
import com.hyuchia.orbit.container

/** A view model for the screen it's called from, made from what the app shares. */
@Composable
inline fun <reified T : ViewModel> orbitViewModel(key: String? = null, crossinline create: (AppContainer) -> T): T {
    val container = LocalContext.current.container
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}
