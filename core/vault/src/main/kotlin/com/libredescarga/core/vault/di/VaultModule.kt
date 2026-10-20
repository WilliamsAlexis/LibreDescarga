package com.libredescarga.core.vault.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.libredescarga.core.domain.vault.VaultAuthEngine
import com.libredescarga.core.domain.vault.VaultAuthRepository
import com.libredescarga.core.domain.vault.VaultAuthStore
import com.libredescarga.core.domain.vault.VaultRepository
import com.libredescarga.core.domain.vault.VaultSession
import com.libredescarga.core.vault.AutoLockVaultSession
import com.libredescarga.core.vault.DataStoreVaultAuthStore
import com.libredescarga.core.vault.LocalVaultRepository
import com.libredescarga.core.vault.VaultDataStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Conecta cada contrato de la bóveda con la clase que lo cumple (acceso, sesión y almacenamiento). */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class VaultBindingsModule {

    // Dónde se guardan los datos de acceso (PIN o contraseña, intentos).
    @Binds
    abstract fun bindAuthStore(impl: DataStoreVaultAuthStore): VaultAuthStore

    // Lógica de acceso: crear, verificar y recuperar el PIN o contraseña.
    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: VaultAuthEngine): VaultAuthRepository

    // Sesión de la bóveda: se bloquea sola al salir de la app.
    @Binds
    abstract fun bindSession(impl: AutoLockVaultSession): VaultSession

    // Almacén de archivos protegidos de la bóveda.
    @Binds
    abstract fun bindRepository(impl: LocalVaultRepository): VaultRepository
}

/** Crea el archivo de preferencias exclusivo de la bóveda. */
@Module
@InstallIn(SingletonComponent::class)
internal object VaultProvidersModule {

    // Archivo aparte ("vault_access") para que los datos de acceso no se mezclen con los ajustes normales.
    @Provides
    @Singleton
    @VaultDataStore
    fun provideVaultDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile("vault_access") })
}
