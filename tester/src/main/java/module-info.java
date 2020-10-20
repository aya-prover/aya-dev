module org.mzi.test {
  requires static org.jetbrains.annotations;

  requires org.antlr.antlr4.runtime;
  requires transitive org.junit.jupiter.api;
  requires transitive org.mzi;
  requires transitive org.mzi.parser;

  exports org.mzi.test;
}
