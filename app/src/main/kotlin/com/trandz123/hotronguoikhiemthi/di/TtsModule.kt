package com.trandz123.hotronguoikhiemthi.di

import android.content.Context
import com.trandz123.hotronguoikhiemthi.BuildConfig
import com.trandz123.hotronguoikhiemthi.data.settings.PreferencesRepository
import com.trandz123.hotronguoikhiemthi.tts.AndroidTtsEngine
import com.trandz123.hotronguoikhiemthi.tts.FptTtsEngine
import com.trandz123.hotronguoikhiemthi.tts.RoutedTtsEngine
import com.trandz123.hotronguoikhiemthi.tts.TtsManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TtsModule {

    @Provides
    @Singleton
    fun provideAndroidEngine(@ApplicationContext ctx: Context): AndroidTtsEngine =
        AndroidTtsEngine(ctx)

    @Provides
    @Singleton
    fun provideFptEngine(@ApplicationContext ctx: Context): FptTtsEngine =
        FptTtsEngine(ctx, apiKey = BuildConfig.FPT_TTS_API_KEY)

    /**
     * [RoutedTtsEngine] tu chon engine theo settings + network. App-wide singleton.
     */
    @Provides
    @Singleton
    fun provideTtsManager(
        @ApplicationContext ctx: Context,
        fpt: FptTtsEngine,
        android: AndroidTtsEngine,
        prefsRepo: PreferencesRepository,
    ): TtsManager = RoutedTtsEngine(ctx, fpt, android, prefsRepo)
}
