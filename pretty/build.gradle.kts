dependencies {
  val annotationsVersion: String by rootProject.ext
  api("org.jetbrains:annotations:$annotationsVersion")
  testImplementation("junit", "junit", "4.12")
}
