package it.finardi.schiena.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseGuideCatalogTest {
    private val guide = ExerciseGuide(
        exerciseId = "test_exercise", steps = listOf("Approved test step"),
        images = listOf(ExerciseGuideImage("guides/images/test_start.png", "Test starting position")),
        source = "Test source", license = "Test license", reviewedBy = "Test reviewer", verified = true,
    )

    @Test
    fun emptyCatalogIsSupportedUntilVerifiedMaterialsAreAvailable() {
        assertTrue(Json.decodeFromString<ExerciseGuideCatalog>("{\"guides\": []}").verifiedGuides().isEmpty())
    }

    @Test
    fun unverifiedContentIsNeverPublished() {
        assertTrue(ExerciseGuideCatalog(listOf(guide.copy(verified = false))).verifiedGuides().isEmpty())
    }

    @Test
    fun verifiedGuideIsIndexedByExerciseId() {
        assertEquals(mapOf(guide.exerciseId to guide), ExerciseGuideCatalog(listOf(guide)).verifiedGuides())
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateExerciseIdsAreRejected() {
        ExerciseGuideCatalog(listOf(guide, guide)).verifiedGuides()
    }

    @Test(expected = IllegalArgumentException::class)
    fun missingSourceIsRejected() {
        ExerciseGuideCatalog(listOf(guide.copy(source = ""))).verifiedGuides()
    }

    @Test(expected = IllegalArgumentException::class)
    fun missingLicenseIsRejected() {
        ExerciseGuideCatalog(listOf(guide.copy(license = ""))).verifiedGuides()
    }

    @Test(expected = IllegalArgumentException::class)
    fun missingReviewerIsRejected() {
        ExerciseGuideCatalog(listOf(guide.copy(reviewedBy = ""))).verifiedGuides()
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankInstructionsAreRejected() {
        ExerciseGuideCatalog(listOf(guide.copy(steps = listOf(" ")))).verifiedGuides()
    }

    @Test(expected = IllegalArgumentException::class)
    fun missingImagesAreRejected() {
        ExerciseGuideCatalog(listOf(guide.copy(images = emptyList()))).verifiedGuides()
    }

    @Test(expected = IllegalArgumentException::class)
    fun missingImageDescriptionIsRejected() {
        ExerciseGuideCatalog(listOf(guide.copy(images = listOf(guide.images.single().copy(description = ""))))).verifiedGuides()
    }

    @Test(expected = IllegalArgumentException::class)
    fun assetTraversalIsRejected() {
        ExerciseGuideCatalog(listOf(guide.copy(images = listOf(
            guide.images.single().copy(assetPath = "guides/images/../../seed/program_seed.json"),
        )))).verifiedGuides()
    }
}