package com.violinjourney.app.core.data.profile.di

import com.violinjourney.app.core.data.profile.AppAvatarFiles
import com.violinjourney.app.core.data.profile.AvatarFiles
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ProfileDataModule {
    @Binds
    @Singleton
    abstract fun bindAvatarFiles(impl: AppAvatarFiles): AvatarFiles
}
