package com.trandz123.hotronguoikhiemthi.data.settings

/**
 * Snapshot toan bo cau hinh nguoi dung. Reactive qua [PreferencesRepository.flow].
 *
 * Default value dat sao cho khi user moi cai app → ngay lap tuc dung duoc:
 *  - TTS toc do 1.0 binh thuong
 *  - Tu dong chup: TAT (user moi quen feel app bang nut bam truoc, sau bat de hands-free)
 *  - High contrast: theo he thong (dark theme → on)
 *  - Rung: BAT (haptic feedback rat quan trong cho nguoi khiem thi)
 *  - Voice engine: AUTO (FPT neu co key + online, ko thi Android)
 */
data class UserPreferences(
    val ttsRate: Float = 1.0f,
    val ttsVoice: TtsVoice = TtsVoice.AUTO,
    val contrastMode: ContrastMode = ContrastMode.SYSTEM,
    val vibrationEnabled: Boolean = true,
    val autoCaptureEnabled: Boolean = false,
    val voiceCommandEnabled: Boolean = true,
)

enum class TtsVoice(val displayName: String, val fptCode: String?) {
    AUTO("Tự động", null),
    FPT_FEMALE_NORTH("Nữ Bắc (FPT)", "banmai"),
    FPT_MALE_NORTH("Nam Bắc (FPT)", "leminh"),
    FPT_FEMALE_SOUTH("Nữ Nam (FPT)", "linhsan"),
    ANDROID_DEFAULT("Android mặc định", null);

    val usesFpt: Boolean get() = fptCode != null || this == AUTO
}

enum class ContrastMode(val displayName: String) {
    SYSTEM("Theo hệ thống"),
    ALWAYS_DARK("Luôn nền đen, chữ vàng"),
    ALWAYS_LIGHT("Luôn nền trắng, chữ đen"),
}
