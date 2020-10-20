tasks.register<org.mzi.gradle.PreprocessZhihuTask>("zhihu") {
  from(file("src"))
  into(file("zhihu"))
}
