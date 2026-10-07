package it.finardi.schiena.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ExerciseGuideCatalog(val guides: List<ExerciseGuide> = emptyList()) {
    fun verifiedGuides(): Map<String, ExerciseGuide> {
        require(guides.map { it.exerciseId }.distinct().size == guides.size)
        return guides.filter { it.verified }.onEach { guide ->
            require(guide.exerciseId.isNotBlank() && guide.steps.isNotEmpty() && guide.steps.all { it.isNotBlank() })
            require(guide.source.isNotBlank() && guide.license.isNotBlank() && guide.reviewedBy.isNotBlank())
            require(guide.images.isNotEmpty())
            guide.images.forEach { image ->
                require(image.description.isNotBlank())
                require(image.assetPath.matches(Regex("guides/images/[a-zA-Z0-9_-]+\\.(png|jpg|webp)")))
            }
        }.associateBy { it.exerciseId }
    }
}

@Serializable
data class ExerciseGuide(
    val exerciseId: String,
    val steps: List<String>,
    val images: List<ExerciseGuideImage>,
    val source: String,
    val license: String,
    val reviewedBy: String,
    val verified: Boolean = false,
)

@Serializable
data class ExerciseGuideImage(val assetPath: String, val description: String)

class ExerciseGuideRepository(private val context: Context) {
    suspend fun load(): Map<String, ExerciseGuide> = withContext(Dispatchers.IO) {
        context.assets.open("guides/exercise_guides.json").bufferedReader(Charsets.UTF_8).use { reader ->
            Json.decodeFromString<ExerciseGuideCatalog>(reader.readText()).verifiedGuides()
        }
    }
}