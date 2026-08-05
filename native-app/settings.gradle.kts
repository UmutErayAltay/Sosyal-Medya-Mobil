pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Grup sesli/görüntülü arama (LiveKit) — io.livekit:* paketleri context7
        // MCP ile doğrulanan resmi kurulum talimatına göre JitPack'ten çözülüyor
        // (bkz. app/build.gradle.kts dependencies bloğundaki LiveKit yorumu).
        // FAIL_ON_PROJECT_REPOS modu proje-seviyeli repo bildirimini
        // ENGELLEDİĞİ için bu depo BURADA (dependencyResolutionManagement)
        // tanımlanmak ZORUNDA — app modülünde tanımlansa build hemen patlar.
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "native-app"
include(":app")
