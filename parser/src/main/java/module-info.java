module aya.parser.ij {
  requires transitive aya.ij.parsing.core;
  requires transitive aya.ij.parsing.wrapper;
  requires static org.jetbrains.annotations;

  exports org.aya.parser;
}
