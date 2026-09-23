package com.violinjourney.app.feature.share.di

import android.os.SystemClock
import com.violinjourney.app.core.audio.share.AppShareFiles
import com.violinjourney.app.core.audio.share.ShareFiles
import com.violinjourney.app.core.audio.share.SoundFileRenderer
import com.violinjourney.app.core.audio.share.SoundRenderer
import com.violinjourney.app.feature.share.AppShareTexts
import com.violinjourney.app.core.di.ElapsedClock
import com.violinjourney.app.feature.share.ShareTexts
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ShareModule {
    @Binds
    abstract fun bindShareFiles(impl: AppShareFiles): ShareFiles

    @Binds
    abstract fun bindSoundRenderer(impl: SoundFileRenderer): SoundRenderer

    @Binds
    abstract fun bindShareTexts(impl: AppShareTexts): ShareTexts

    companion object {
        @Provides
        fun provideElapsedClock(): ElapsedClock = ElapsedClock(SystemClock::elapsedRealtime)
    }
}
