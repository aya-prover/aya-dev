module org.mzi.test {
  requires static org.jetbrains.annotations;

  requires transitive org.junit.jupiter.api;
  requires transitive org.mzi;
  requires transitive org.mzi.parser;
  requires transitive org.mzi.pretty;

  exports org.mzi.test;
}
