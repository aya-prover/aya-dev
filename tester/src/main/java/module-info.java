module org.aya.test {
  requires static org.jetbrains.annotations;

  requires transitive org.junit.jupiter.api;
  requires transitive org.aya;

  exports org.aya.test;
}
