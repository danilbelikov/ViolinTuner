package com.violinjourney.app.core.data.repertoire.di

import com.violinjourney.app.core.analytics.Analytics
import com.violinjourney.app.core.data.repertoire.AppSheetFiles
import com.violinjourney.app.core.data.repertoire.RepertoireDao
import com.violinjourney.app.core.data.repertoire.RoomRepertoireRepository
import com.violinjourney.app.core.data.repertoire.SheetFiles
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.domain.repertoire.RepertoireRepository
import com.violinjourney.app.core.time.WallClock
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepertoireDataModule {
    @Binds
    @Singleton
    abstract fun bindSheetFiles(impl: AppSheetFiles): SheetFiles

    companion object {
        @Provides
        @Singleton
        fun provideRepertoireRepository(
            dao: RepertoireDao,
            files: SheetFiles,
            config: RepertoireConfig,
            clock: WallClock,
            analytics: Analytics,
        ): RepertoireRepository = RoomRepertoireRepository(dao, files, config, clock, analytics)
    }
}
