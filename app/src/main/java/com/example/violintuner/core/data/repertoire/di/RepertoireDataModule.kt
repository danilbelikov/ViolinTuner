package com.example.violintuner.core.data.repertoire.di

import com.example.violintuner.core.data.repertoire.AppSheetFiles
import com.example.violintuner.core.data.repertoire.RoomRepertoireRepository
import com.example.violintuner.core.data.repertoire.SheetFiles
import com.example.violintuner.core.domain.repertoire.RepertoireRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepertoireDataModule {
    @Binds
    @Singleton
    abstract fun bindSheetFiles(impl: AppSheetFiles): SheetFiles

    @Binds
    @Singleton
    abstract fun bindRepertoireRepository(impl: RoomRepertoireRepository): RepertoireRepository
}
