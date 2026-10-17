import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "KomikArta"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "id"
        baseUrl {
            custom("https://komikarta.com")
        }
        id = 1840561621689186637L
    }
}
