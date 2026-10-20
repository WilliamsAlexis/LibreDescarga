package com.libredescarga.core.network.di

import com.libredescarga.core.domain.settings.AppUpdateRepository
import com.libredescarga.core.domain.settings.BlocklistUpdater
import com.libredescarga.core.network.GithubAppUpdater
import com.libredescarga.core.network.GithubBlocklistUpdater
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Conecta los contratos de actualización con las clases que consultan GitHub. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class NetworkModule {

    // Actualizador de la propia app.
    @Binds
    abstract fun bindAppUpdater(impl: GithubAppUpdater): AppUpdateRepository

    // Actualizador de la lista de sitios bloqueados.
    @Binds
    abstract fun bindBlocklistUpdater(impl: GithubBlocklistUpdater): BlocklistUpdater
}
