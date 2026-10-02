package com.violinjourney.app.core.data.events.di

import com.violinjourney.app.core.analytics.Analytics
import com.violinjourney.app.core.data.events.EventDao
import com.violinjourney.app.core.data.events.RoomEventRepository
import com.violinjourney.app.core.di.IoDispatcher
import com.violinjourney.app.core.domain.events.EventRepository
import com.violinjourney.app.core.domain.events.EventsConfig
import com.violinjourney.app.core.time.WallClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher

@Module
@InstallIn(SingletonComponent::class)
object EventsDataModule {
    @Provides
    @Singleton
    fun provideEventRepository(
        dao: EventDao,
        config: EventsConfig,
        clock: WallClock,
        analytics: Analytics,
        @IoDispatcher io: CoroutineDispatcher,
    ): EventRepository = RoomEventRepository(dao, config, clock, analytics, io)
}
