package com.libredescarga.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Crea el archivo donde se guardan los ajustes del usuario (tema, calidad, etc.). */
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    // Nombre del archivo de ajustes dentro del teléfono.
    private const val USER_PREFERENCES_FILE = "user_preferences"

    // Un solo almacén de ajustes para toda la app.
    @Provides
    @Singleton
    fun provideUserPreferencesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile(USER_PREFERENCES_FILE) },
    )
}
