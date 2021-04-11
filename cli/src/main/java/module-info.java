module org.aya.cli {
  requires com.beust.jcommander;
  requires static ice1000.jimgui;
  requires transitive org.aya;

  exports org.aya.cli;
}
