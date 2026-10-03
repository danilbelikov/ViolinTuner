package com.violinjourney.app.feature.share.di

import android.content.Context
import android.os.SystemClock
import androidx.compose.ui.text.font.createFontFamilyResolver
import com.violinjourney.app.core.audio.share.AppShareFiles
import com.violinjourney.app.core.audio.share.ShareFiles
import com.violinjourney.app.core.audio.share.SoundFileRenderer
import com.violinjourney.app.core.audio.share.SoundRenderer
import com.violinjourney.app.core.recording.overlay.MediaNotesVideoRenderer
import com.violinjourney.app.core.recording.overlay.NotesVideoConfig
import com.violinjourney.app.core.recording.overlay.NotesVideoRenderer
import com.violinjourney.app.core.recording.overlay.OverlayText
import com.violinjourney.app.core.ui.theme.Manrope
import com.violinjourney.app.feature.share.AppShareTexts
import com.violinjourney.app.core.di.ElapsedClock
import com.violinjourney.app.core.di.NotesVideoSpeed
import com.violinjourney.app.feature.share.RenderSpeed
import com.violinjourney.app.feature.share.ShareTexts
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ShareModule {
    @Binds
    abstract fun bindShareFiles(impl: AppShareFiles): ShareFiles

    @Binds
    abstract fun bindSoundRenderer(impl: SoundFileRenderer): SoundRenderer

    @Binds
    abstract fun bindShareTexts(impl: AppShareTexts): ShareTexts

    /** «Видео с нотами» (spec 3.37): the picture encoded again by Media3 with the notes drawn over it. */
    @Binds
    abstract fun bindNotesVideoRenderer(impl: MediaNotesVideoRenderer): NotesVideoRenderer

    companion object {
        @Provides
        fun provideElapsedClock(): ElapsedClock = ElapsedClock(SystemClock::elapsedRealtime)

        @Provides
        @Singleton
        fun provideNotesVideoConfig(): NotesVideoConfig = NotesVideoConfig()

        /** One per app: it measures how fast «Видео с нотами» is made on this phone (spec 5.30). */
        @Provides
        @Singleton
        @NotesVideoSpeed
        fun provideNotesVideoSpeed(config: NotesVideoConfig): RenderSpeed = RenderSpeed(config.renderSpeedStart)

        /** The font of the notes drawn on a video, outside any composition: the app's own Manrope. */
        @Provides
        @Singleton
        fun provideOverlayText(@ApplicationContext context: Context): OverlayText = OverlayText(createFontFamilyResolver(context), Manrope)
    }
}
