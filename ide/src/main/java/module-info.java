module aya.ide {
  requires aya.base;
  requires aya.cli.impl;

  requires static org.jetbrains.annotations;

  exports org.aya.ide;
  exports org.aya.ide.action;
  exports org.aya.ide.syntax;
  exports org.aya.ide.util;
}
