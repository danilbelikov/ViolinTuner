package com.violinjourney.app.feature.repertoire.scale

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.violinjourney.app.core.domain.repertoire.Accidental
import com.violinjourney.app.core.domain.repertoire.PieceStatus
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.domain.repertoire.Tonic
import com.violinjourney.app.core.domain.repertoire.scale.ScaleKind
import com.violinjourney.app.core.domain.repertoire.scale.ScaleSpec
import com.violinjourney.app.core.domain.repertoire.scale.Scales
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.core.ui.theme.ViolinTheme

// The form of a scale of R4 (spec 3.36.4; repertoire.html 5): a-moll harmonic in two octaves; F-dur, whose third octave would end over
// E7; F#-dur, where the sharp leaves two tonics; G-dur in three octaves that is there already, and its edit.

private val Config = RepertoireConfig()

/** The status bar of the phone over the screen of the preview. */
private val StatusBar = 24.dp

private fun state(
    draft: ScaleDraft,
    isNew: Boolean = true,
    twin: ScaleTwin? = null,
    loading: Boolean = false,
): ScaleFormState {
    val tonic = draft.tonic
    val scale = tonic?.let { Scales.build(ScaleSpec(it, draft.accidental, draft.kind, draft.octaves), Config.scaleLowestMidi, Config.scaleHighestMidi) }
    return ScaleFormState(
        loading = loading,
        isNew = isNew,
        draft = draft,
        scale = scale,
        tonicsAllowed = Tonic.entries.filter { Scales.isKeyAllowed(it, draft.accidental, draft.kind) }.toSet(),
        octavesAllowed = if (tonic == null) {
            (1..Scales.MAX_OCTAVES).toSet()
        } else {
            (1..Scales.MAX_OCTAVES).filter { Scales.octavesFit(tonic, draft.accidental, it, Config.scaleLowestMidi, Config.scaleHighestMidi) }.toSet()
        },
        twin = twin,
        canSave = scale != null && twin == null,
        dialog = null,
        maxNotesLength = Config.maxNotesLength,
        savedTitle = if (isNew) null else "G-dur · 3 октавы",
        startNote = tonic?.let { ScaleWords.nameOf(Scales.lowestTonic(it, draft.accidental, Config.scaleLowestMidi)) },
    )
}

private val AMinor = ScaleDraft(tonic = Tonic.A, kind = ScaleKind.HARMONIC_MINOR, octaves = 2)
private val FMajor = ScaleDraft(tonic = Tonic.F, octaves = 2)
private val GMajor = ScaleDraft(tonic = Tonic.G, octaves = 3, tempoBpm = 80, status = PieceStatus.LEARNING)

@Composable
private fun Form(state: ScaleFormState) {
    ViolinTheme {
        CompositionLocalProvider(LocalReduceMotion provides true) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(top = StatusBar)) {
                ScaleFormScreen(state = state, onIntent = {})
            }
        }
    }
}

@Preview(name = "Гамма · новая: итог a-moll гармонический первым, тоники C … H, вид 2 × 2, октавы, «Добавить гамму»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun NewPreview() = Form(state(AMinor))

@Preview(name = "Гамма · без тоники: «Выберите тонику — ноты нарисуются здесь», «Сначала выберите тонику» над «Добавить гамму» 0,38", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun NoTonicPreview() = Form(state(ScaleDraft()))

@Preview(name = "Гамма · недоступная октава: F-dur, «3» 0,38 и «Три октавы от F4 не помещаются на скрипке»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun OctaveOffPreview() = Form(state(FMajor))

@Preview(name = "Гамма · серые тоники: ♯ — только F и C, строка «Серые тоники…»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun GreyTonicsPreview() = Form(state(ScaleDraft(tonic = Tonic.F, accidental = Accidental.SHARP, octaves = 2)))

@Preview(name = "Гамма · такая уже есть: плашка «в «Гаммах», статус «Учу», 2 дубля» над «Открыть её»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun TwinPreview() = Form(state(GMajor, twin = ScaleTwin(id = 3, status = PieceStatus.LEARNING, takes = 2)))

@Preview(name = "Гамма · правка: тоника, знак и вид 0,38 со строкой «Тональность и вид у гаммы не меняются…», «Удалить гамму»", locale = "ru", device = "spec:width=412dp,height=1400dp")
@Composable
private fun EditPreview() = Form(state(GMajor, isNew = false))

@Preview(name = "Гамма · 360 × 640: тоники в два ряда, «C D E F» и «G A H»", locale = "ru", device = "spec:width=360dp,height=640dp")
@Composable
private fun SmallPreview() = Form(state(AMinor))

@Preview(name = "Гамма · 360, шрифт 1,3: слова вида целиком — мельче вместе", locale = "ru", fontScale = 1.3f, device = "spec:width=360dp,height=640dp")
@Composable
private fun SmallLargePreview() = Form(state(AMinor))

@Preview(name = "Гамма · landscape 892 × 412: одна колонка 560, шапка 48", locale = "ru", device = "spec:width=892dp,height=412dp")
@Composable
private fun LandscapePreview() = Form(state(AMinor))

@Preview(name = "Гамма · de, 360, шрифт 1,3: «Moll harmonisch», «Drei Oktaven ab F4 …»", locale = "de", fontScale = 1.3f, device = "spec:width=360dp,height=640dp")
@Composable
private fun GermanPreview() = Form(state(FMajor))
