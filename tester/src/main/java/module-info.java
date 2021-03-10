module org.aya.test {
  requires static org.jetbrains.annotations;

  requires transitive org.junit.jupiter.api;
  requires transitive org.aya;
  requires transitive org.glavo.kala.collection;

  exports org.aya.test;
}
