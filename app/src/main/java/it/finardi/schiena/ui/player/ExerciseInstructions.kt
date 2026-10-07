package it.finardi.schiena.ui.player

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import it.finardi.schiena.data.Exercise
import it.finardi.schiena.data.ExerciseGuide
import it.finardi.schiena.data.ExerciseGuideRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class RenderedGuide(val guide: ExerciseGuide, val images: List<ImageBitmap>)

@Composable
fun ExerciseInstructions(exercise: Exercise) {
    val context = LocalContext.current.applicationContext
    val rendered by produceState<RenderedGuide?>(null, context, exercise.id) {
        value = null
        try {
            val guide = ExerciseGuideRepository(context).load()[exercise.id]
            if (guide != null) {
                val images = withContext(Dispatchers.IO) {
                    guide.images.map { image ->
                        context.assets.open(image.assetPath).use { stream ->
                            requireNotNull(BitmapFactory.decodeStream(stream)).asImageBitmap()
                        }
                    }
                }
                value = RenderedGuide(guide, images)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            value = null
        }
    }
    val guide = rendered?.takeIf { it.guide.exerciseId == exercise.id }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (guide != null) {
            guide.images.forEachIndexed { index, image ->
                Image(bitmap = image, contentDescription = guide.guide.images[index].description,
                    contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp))
            }
        }
        (guide?.guide?.steps ?: exercise.cues).forEach { step -> Text(step) }
    }
}