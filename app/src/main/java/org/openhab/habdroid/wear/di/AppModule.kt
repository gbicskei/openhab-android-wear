package org.openhab.habdroid.wear.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.openhab.habdroid.wear.BuildConfig
import org.openhab.habdroid.wear.data.api.AuthInterceptor
import org.openhab.habdroid.wear.data.api.OpenHabApiService
import org.openhab.habdroid.wear.data.repository.CredentialStore
import org.openhab.habdroid.wear.data.repository.ThemeStore
import org.openhab.habdroid.wear.data.repository.TilePreferenceStore
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "openhab_wear_prefs")
private val Context.tileDataStore: DataStore<Preferences> by preferencesDataStore(name = "tile_selection_prefs")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @Named("credentials")
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.dataStore
    }

    @Provides
    @Singleton
    @Named("tile")
    fun provideTileDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.tileDataStore
    }

    @Provides
    @Singleton
    fun provideCredentialStore(@Named("credentials") dataStore: DataStore<Preferences>): CredentialStore {
        return CredentialStore(dataStore)
    }

    @Provides
    @Singleton
    fun provideTilePreferenceStore(@Named("tile") dataStore: DataStore<Preferences>): TilePreferenceStore {
        return TilePreferenceStore(dataStore)
    }

    @Provides
    @Singleton
    fun provideThemeStore(@Named("tile") dataStore: DataStore<Preferences>): ThemeStore {
        return ThemeStore(dataStore)
    }

    @Provides
    @Singleton
    fun provideJson(): Json {
        return Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            isLenient = true
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BODY
                        }
                    )
                }
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit {
        // Base URL is dynamic (set per-request via interceptor), placeholder here
        return Retrofit.Builder()
            .baseUrl("https://placeholder.openhab.org/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideOpenHabApiService(retrofit: Retrofit): OpenHabApiService {
        return retrofit.create(OpenHabApiService::class.java)
    }
}
