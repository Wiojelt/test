version = 3

cloudstream {
    authors     = listOf("wiojelt")
    language    = "tr"
    description = "Anizium 4K ve 2K Turkce Dublaj ve Altyazili Anime Saglayicisi. Token/giris hatasi alirsaniz eklentinin secenekler butonundan oturumu yenileyin."
    status  = 3
    tvTypes = listOf("Anime", "AnimeMovie", "OVA")
    iconUrl = "https://x.anizium.co/assets/index/img/embed-logo.png"
}

dependencies {
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
}
