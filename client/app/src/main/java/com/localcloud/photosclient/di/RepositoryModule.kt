package com.localcloud.photosclient.di

import com.localcloud.photosclient.data.repository.MediaSessionRepositoryImpl
import com.localcloud.photosclient.domain.repository.MediaSessionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindMediaSessionRepository(
        impl: MediaSessionRepositoryImpl
    ): MediaSessionRepository
}
