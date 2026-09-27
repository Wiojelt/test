import com.android.build.api.dsl.LibraryExtension
import com.lagradost.cloudstream3.gradle.CloudstreamExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

buildscript {
    repositories { google(); mavenCentral(); maven("https://jitpack.io") }
    dependencies {
        classpath("com.android.tools.build:gradle:9.1.1")
        classpath("com.github.recloudstream.gradle:gradle:32895aedb6366f5075cb99bbd2e6ce0a7cac325d")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.0")
    }
}
allprojects { repositories { google(); mavenCentral(); maven("https://jitpack.io") } }
fun Project.cloudstream(configuration: CloudstreamExtension.() -> Unit) = extensions.configure("cloudstream", configuration)
subprojects {
    apply(plugin = "com.android.library")
    apply(plugin = "com.lagradost.cloudstream3.gradle")
    extensions.configure<CloudstreamExtension>("cloudstream") {
        setRepo("Wiojelt/TurkSpor")
        authors = listOf("Wiojelt")
    }
    extensions.configure<LibraryExtension>("android") {
        namespace = "io.github.viollje.turkspor"
        compileSdk = 36
        defaultConfig { minSdk = 23 }
        if (project.name in setOf("MacKeyfi", "ZbahisTV", "InterSporTV", "BeyazElma", "BetmatikTV", "InatBox", "AslanTV", "TRGoals", "PapazSports", "NetVGold", "JestYayin", "GolgeTV", "PatronHD")) {
            sourceSets.getByName("main").kotlin.directories += rootProject.file("shared/src/main/kotlin").path
        }
        if (project.name in setOf("AslanTV")) {
            sourceSets.getByName("main").kotlin.directories += rootProject.file("shared-filter/src/main/kotlin").path
        }

        sourceSets.getByName("main").kotlin.directories += rootProject.file("common/src/main/kotlin").path
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }
    }
    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
            freeCompilerArgs.addAll("-Xno-call-assertions", "-Xno-param-assertions", "-Xno-receiver-assertions", "-opt-in=com.lagradost.cloudstream3.Prerelease")
        }
    }
    dependencies {
        add("cloudstream", "com.lagradost:cloudstream3:pre-release")
        add("implementation", kotlin("stdlib"))
        add("implementation", "com.github.Blatzar:NiceHttp:0.4.18")
        add("implementation", "org.jsoup:jsoup:1.22.2")
        add("implementation", "com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")
        add("implementation", "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
        add("compileOnly", "com.google.android.material:material:1.12.0")
        add("testImplementation", "junit:junit:4.13.2")
    }
}
