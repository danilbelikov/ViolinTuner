package com.example.violintuner.core.data.profile.di

import com.example.violintuner.core.data.profile.AppAvatarFiles
import com.example.violintuner.core.data.profile.AvatarFiles
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
