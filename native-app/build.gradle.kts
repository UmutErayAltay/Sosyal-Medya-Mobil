// Top-level build file — TWA projesindeki (repo kökü) build.gradle'dan TAMAMEN
// bağımsız. Bu proje kendi Gradle wrapper'ını (8.11.1, root ile aynı — bu
// sandbox'ta zaten cache'li olduğu için ekstra indirme gerekmiyor) kullanır.
plugins {
    id("com.android.application") version "8.9.1" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
