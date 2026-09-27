rootProject.name = "KoolTVTest"
val buildTool = file(".build-tools/cloudstream-gradle")
check(buildTool.resolve("settings.gradle.kts").isFile)
includeBuild(buildTool) {
  name = "cloudstream-build-tool"
  dependencySubstitution { substitute(module("com.github.recloudstream.gradle:gradle")).using(project(":")) }
}
include("KoolTV")
