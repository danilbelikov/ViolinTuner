package com.violinjourney.app.feature.camera.di

import android.content.Context
import com.violinjourney.app.feature.camera.CameraXShotCamera
import com.violinjourney.app.feature.camera.ShotCameraFactory
import com.violinjourney.app.core.recording.video.VideoMux
import com.violinjourney.app.core.recording.video.VideoMuxer
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

    /** The picture and the sound of a shot into one `.mp4` by the platform's muxer (spec 5.25). */
    @Provides
    fun provideVideoMux(): VideoMux = VideoMux { picture, sound, target, shiftUs -> VideoMuxer.mux(picture, sound, target, shiftUs) }
}
