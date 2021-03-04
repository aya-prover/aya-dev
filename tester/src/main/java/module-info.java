module org.aya.test {
  requires static org.jetbrains.annotations;

  requires transitive org.junit.jupiter.api;
  requires transitive org.aya;
  requires transitive org.aya.parser;
  requires transitive org.aya.pretty;

  exports org.aya.test;
}
