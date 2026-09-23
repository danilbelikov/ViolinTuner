package com.violinjourney.app.feature.camera.di

import android.content.Context
import com.violinjourney.app.feature.camera.CameraXShotCamera
import com.violinjourney.app.feature.camera.ShotCameraFactory
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
