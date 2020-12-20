module org.mzi.test {
  requires static org.jetbrains.annotations;

  requires org.antlr.antlr4.runtime;
  requires transitive org.junit.jupiter.api;
  requires transitive lombok;
  requires transitive org.mzi;
  requires transitive org.mzi.parser;
  requires transitive org.mzi.pretty;

  exports org.mzi.test;
}
