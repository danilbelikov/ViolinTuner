package com.example.violintuner.feature.camera.di

import android.content.Context
import com.example.violintuner.feature.camera.CameraXShotCamera
import com.example.violintuner.feature.camera.ShotCameraFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object CameraModule {
    @Provides
    fun provideShotCameraFactory(@ApplicationContext context: Context): ShotCameraFactory = ShotCameraFactory { CameraXShotCamera(context) }
}
