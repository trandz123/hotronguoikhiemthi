package com.trandz123.hotronguoikhiemthi.di

import android.content.Context
import android.util.Log
import com.trandz123.hotronguoikhiemthi.BuildConfig
import com.trandz123.hotronguoikhiemthi.ml.FakeMoneyClassifier
import com.trandz123.hotronguoikhiemthi.ml.GeminiMenuAnalyzer
import com.trandz123.hotronguoikhiemthi.ml.MoneyClassifier
import com.trandz123.hotronguoikhiemthi.ml.TfliteMoneyClassifier
import com.trandz123.hotronguoikhiemthi.ml.Yolov10MoneyDetector
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
     * Thu init theo thu tu uu tien:
     *  1. [Yolov10MoneyDetector] — production (tien gap/vo/che)
     *  2. [TfliteMoneyClassifier] — fallback v0.3 (MobileNetV3 classifier)
     *  3. [FakeMoneyClassifier] — UI test khi chua co model nao
     *
     * Cach nay cho phep ta dap YOLO model file vao asset sau ma khong can sua code.
     */
    @Provides
    @Singleton
    fun provideMoneyClassifier(@ApplicationContext ctx: Context): MoneyClassifier {
        runCatching { Yolov10MoneyDetector(ctx) as MoneyClassifier }
            .onSuccess { Log.i("MlModule", "Using Yolov10MoneyDetector"); return it }
            .onFailure { Log.w("MlModule", "YOLO unavailable: ${it.message}") }
        runCatching { TfliteMoneyClassifier(ctx) as MoneyClassifier }
            .onSuccess { Log.i("MlModule", "Using TfliteMoneyClassifier"); return it }
            .onFailure { Log.w("MlModule", "TFLite classifier unavailable: ${it.message}") }
        Log.w("MlModule", "Fallback to FakeMoneyClassifier")
        return FakeMoneyClassifier()
    }

    /**
     * GeminiMenuAnalyzer doc menu qua VLM cloud (Gemini 1.5 Flash).
     * Key load tu local.properties → BuildConfig.GEMINI_API_KEY. Neu rong, [isConfigured]=false
     * va MenuViewModel se TTS thong bao chua cau hinh.
     */
    @Provides
    @Singleton
    fun provideGeminiMenuAnalyzer(): GeminiMenuAnalyzer =
        GeminiMenuAnalyzer(BuildConfig.GEMINI_API_KEY)
}
