package com.example.violintuner.feature.repertoire.scale.di

import com.example.violintuner.feature.repertoire.scale.AppScaleTexts
import com.example.violintuner.feature.repertoire.scale.ScaleTexts
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
