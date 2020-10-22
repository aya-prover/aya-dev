dependencies {
  val annotationsVersion: String by rootProject.ext
  val kalaVersion: String by rootProject.ext
  api("org.jetbrains:annotations:$annotationsVersion")
  api("asia.kala:kala-base:$kalaVersion")
  api("asia.kala:kala-collection:$kalaVersion")
  testImplementation(project(":tester"))
}
