{
  lib,
  mkDerivation,
  fetchFromGitHub,
  version ? "0-unstable-SNAPSHOT",
}:

# Aya standard library

mkDerivation rec {
  pname = "standard-library";
  inherit version;

  src = lib.sources.cleanSource ../../cli-impl/src/test/resources/shared;

  installPhase = ''
    runHook preInstall

    mkdir -p $out
    cp -R $src/{src/,aya.json} $out/

    runHook postInstall
  '';

  meta = {
    homepage = "https://www.aya-prover.org";
    description = "Standard library for Aya Prover";
    licence = lib.licenses.mit;
    maintainers = with lib.maintainers; [ definfo ];
    platforms = [
      "aarch64-windows"
      "x86_64-windows"
      "aarch64-linux"
      "x86_64-linux"
      "riscv64-linux"
      "aarch64-darwin"
      "x86_64-darwin"
    ];
  };
}
