module org.aya.cli {
  requires info.picocli;
  requires com.google.gson;
  requires org.jetbrains.annotations;
  requires static ice1000.jimgui;
  requires transitive org.aya;

  exports org.aya.cli;
  exports org.aya.cli.library to com.google.gson;
  exports org.aya.cli.single;
  exports org.aya.cli.utils;
}
