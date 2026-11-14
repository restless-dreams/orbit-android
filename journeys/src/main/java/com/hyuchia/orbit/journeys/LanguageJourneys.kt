package com.hyuchia.orbit.journeys

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import com.hyuchia.orbit.journeys.Orbit.addIdea
import com.hyuchia.orbit.journeys.Orbit.clear
import com.hyuchia.orbit.journeys.Orbit.start
import com.hyuchia.orbit.journeys.Phone.shell
import com.hyuchia.orbit.journeys.Phone.shows
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Orbit in the language picked for it in the phone's settings (Android 13 and later: App languages), on the build
 * installed, from a fresh install: Home says it, the welcome map comes in it, and the map's page speaks it too, its
 * + in the orbit and the planet it opens, where an idea is added. The other journeys find things by what Orbit says
 * in English, as the emulators speak it; these by what it says in Spanish and Brazilian Portuguese.
 */
@RunWith(AndroidJUnit4::class)
class LanguageJourneys {
    @get:Rule
    val captures = CapturesFailures { shell("cmd locale set-app-locales $ORBIT --locales en-US") }

    @Before
    fun appLanguages() {
        assumeTrue("App languages come with Android 13", Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
    }

    /**
     * Orbit, fresh, in the language `tag`: Home shows `search`, and the welcome map, `welcome` (its card has its
     * emoji), is drawn with its + reading `addTo` and its name; `idea` is added on it.
     */
    private fun speaks(tag: String, search: String, welcome: String, addTo: String, idea: String) {
        clear()
        shell("cmd locale set-app-locales $ORBIT --locales $tag")
        start()

        shows(By.text(search).pkg(ORBIT), "Home, in $tag")
        shows(By.text("👋 $welcome").pkg(ORBIT), "the welcome map's card, in $tag").click()
        addIdea(welcome, idea, addTo)
    }

    @Test
    fun inSpanish() = speaks("es-419", "Buscar en tus mapas", "Te damos la bienvenida a Orbit", "Agregar una idea a", "Pastel de tres leches")

    @Test
    fun inBrazilianPortuguese() = speaks("pt-BR", "Pesquisar nos seus mapas", "Boas-vindas ao Orbit", "Adicionar uma ideia a", "Pão de queijo")
}
