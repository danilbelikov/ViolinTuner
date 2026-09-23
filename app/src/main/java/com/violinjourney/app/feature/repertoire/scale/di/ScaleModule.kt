package com.violinjourney.app.feature.repertoire.scale.di

import com.violinjourney.app.feature.repertoire.scale.AppScaleTexts
import com.violinjourney.app.feature.repertoire.scale.ScaleTexts
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ScaleModule {
    @Binds
    abstract fun bindScaleTexts(impl: AppScaleTexts): ScaleTexts
}
