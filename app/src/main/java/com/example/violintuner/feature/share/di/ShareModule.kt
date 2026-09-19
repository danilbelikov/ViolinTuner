package com.example.violintuner.feature.share.di

import android.os.SystemClock
import com.example.violintuner.core.audio.share.AppShareFiles
import com.example.violintuner.core.audio.share.ShareFiles
import com.example.violintuner.core.audio.share.SoundFileRenderer
import com.example.violintuner.core.audio.share.SoundRenderer
import com.example.violintuner.feature.share.AppShareTexts
import com.example.violintuner.feature.share.ElapsedClock
import com.example.violintuner.feature.share.ShareTexts
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
