package com.trandz123.hotronguoikhiemthi.di

import android.content.Context
import android.util.Log
import com.trandz123.hotronguoikhiemthi.ml.FakeMoneyClassifier
import com.trandz123.hotronguoikhiemthi.ml.MoneyClassifier
import com.trandz123.hotronguoikhiemthi.ml.TfliteMoneyClassifier
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MlModule {

    /**
     * Thu init [TfliteMoneyClassifier] truoc. Neu file model thieu hoac corrupted
     * → fallback [FakeMoneyClassifier]. Cach nay cho phep app van build/run trong
     * tuan 1-4 (chua co model) ma flow van demo duoc.
     */
    @Provides
    @Singleton
    fun provideMoneyClassifier(@ApplicationContext ctx: Context): MoneyClassifier {
        return runCatching { TfliteMoneyClassifier(ctx) as MoneyClassifier }
            .getOrElse { e ->
                Log.w("MlModule", "TFLite init failed, using FakeMoneyClassifier: ${e.message}")
                FakeMoneyClassifier()
            }
    }
}
