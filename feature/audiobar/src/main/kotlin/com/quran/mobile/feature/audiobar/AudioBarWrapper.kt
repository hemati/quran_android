package com.quran.mobile.feature.audiobar

import android.content.Context
import android.util.AttributeSet
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.feature.audiobar.presenter.AudioBarPresenter
import com.quran.mobile.feature.audiobar.ui.AudioBar
import dev.zacsweers.metro.Inject

class AudioBarWrapper @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : AbstractComposeView(context, attrs, defStyleAttr) {

  @Inject
  lateinit var audioBarPresenter: AudioBarPresenter

  init {
    (context as? AudioBarInjector)?.inject(this)
  }

  @Composable
  override fun Content() {
    QuranTheme {
      val scope = rememberCoroutineScope()
      val flow = remember {
        scope.launchMolecule(mode = RecompositionMode.ContextClock) {
          audioBarPresenter.audioBarPresenter()
        }
      }
      val eventListeners = remember(audioBarPresenter) { audioBarPresenter.eventListeners() }

      Card(
        shape = CardDefaults.shape.bottomOnly(),
        modifier = Modifier.fillMaxWidth()
      ) {
        AudioBar(
          flow,
          eventListeners,
          modifier = Modifier
            .padding(
              WindowInsets.displayCutout
                  .only(WindowInsetsSides.Horizontal)
                .asPaddingValues()
            )
            .padding(horizontal = 16.dp)
            .heightIn(min = dimensionResource(id = R.dimen.audiobar_height))
        )
      }
    }
  }

  private fun Shape.bottomOnly(): Shape {
    return if (this is RoundedCornerShape) {
      this.copy(
        topStart = CornerSize(0.dp),
        topEnd = CornerSize(0.dp)
      )
    } else {
      this
    }
  }
}
