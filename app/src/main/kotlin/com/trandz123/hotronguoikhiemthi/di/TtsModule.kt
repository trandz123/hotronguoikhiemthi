package com.trandz123.hotronguoikhiemthi.di

import android.content.Context
import com.trandz123.hotronguoikhiemthi.tts.AndroidTtsEngine
import com.trandz123.hotronguoikhiemthi.tts.TtsManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hien tai chi co [AndroidTtsEngine]. Khi them `FptTtsEngine` (cloud, sau khi co API key),
 * doi day thanh:
 *   - 2 @Provides @Named voi key "android" va "fpt"
 *   - 1 @Provides chinh tra `RoutedTtsEngine` tu chon engine theo Settings/network
 *
 * Hien tai inject TtsManager se nhan Android engine. Caller (Activity/ViewModel)
 * goi shutdown() trong onDestroy de tranh leak engine TTS.
 */
@Module
@InstallIn(SingletonComponent::class)
object TtsModule {

    @Provides
    @Singleton
    fun provideTtsManager(@ApplicationContext ctx: Context): TtsManager =
        AndroidTtsEngine(ctx)
}
