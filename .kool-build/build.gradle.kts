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
subprojects {
 apply(plugin="com.android.library"); apply(plugin="com.lagradost.cloudstream3.gradle")
 extensions.configure<CloudstreamExtension>("cloudstream") { setRepo("Wiojelt/test"); authors=listOf("Wiojelt") }
 extensions.configure<LibraryExtension>("android") { namespace="dev.wiojelt.kooltv"; compileSdk=36; defaultConfig { minSdk=23 }; compileOptions { sourceCompatibility=JavaVersion.VERSION_11; targetCompatibility=JavaVersion.VERSION_11 } }
 tasks.withType<KotlinJvmCompile>().configureEach { compilerOptions { jvmTarget.set(JvmTarget.JVM_11); freeCompilerArgs.add("-opt-in=com.lagradost.cloudstream3.Prerelease") } }
 dependencies {
  add("cloudstream","com.lagradost:cloudstream3:pre-release"); add("implementation",kotlin("stdlib"))
  add("implementation","com.github.Blatzar:NiceHttp:0.4.18"); add("implementation","org.jsoup:jsoup:1.22.2")
  add("implementation","org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2"); add("compileOnly","com.google.android.material:material:1.12.0")
 }
}
