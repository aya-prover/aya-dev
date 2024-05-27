module aya.producer {
  requires aya.syntax;
  requires aya.parser.ij;

  requires static org.jetbrains.annotations;

  exports org.aya.producer.flcl;
  exports org.aya.producer;
}
