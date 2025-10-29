# Builder for Aya packages.

{
  stdenv,
  lib,
  self,
  Aya,
  runCommand,
  makeWrapper,
}:

# Support for Nix `withPackages` expression:

# aya.withPackages [ ayaPackages.standard-library ]
# aya.withPackages (p: [ p.standard-library ])

# And define ayaPackages.mkDerivation to build Aya libraries

let
  withPackages' =
    { pkgs }:
    let
      pkgs' = if lib.isList pkgs then pkgs else pkgs self;
      pname = "ayaWithPackages";
      inherit (Aya) version;
    in
    runCommand "${pname}-${version}"
      {
        inherit pname version;
        nativeBuildInputs = [ makeWrapper ];
        passthru = {
          unwrapped = Aya;
          inherit withPackages;
          # TODO: tests
        };
      }
      ''
        mkdir -p $out/bin
        makeWrapper ${lib.getExe' Aya "aya"} $out/bin/aya \
          --add-flags "${lib.concatMapStringsSep " " (p: "--module-path=${p}/src") pkgs'}"
        ln -s ${lib.getExe' Aya "aya-lsp"} $out/bin/aya-lsp
      '';

  withPackages = arg: if lib.isAttrs arg then withPackages' arg else withPackages' { pkgs = arg; };

  defaults =
    {
      pname,
      meta,
      buildInputs ? [ ],
      buildPhase ? null,
      installPhase ? null,
      ...
    }:
    let
      ayaWithPkgs = withPackages (lib.filter (p: p ? isAyaDerivation) buildInputs);
    in
    {
      isAyaDerivation = true;

      buildInputs = buildInputs ++ [ ayaWithPkgs ];

      inherit buildPhase installPhase;

      # Disable Hydra build jobs when marked as broken
      meta = if meta.broken or false then meta // { hydraPlatforms = lib.platforms.none; } else meta;

      # Retrieve all packages from the finished package set that have the current package as a dependency and build them
      passthru.tests = lib.filterAttrs (
        _name: pkg:
        self.lib.isUnbrokenAgdaPackage pkg && lib.elem pname (map (pkg: pkg.pname) pkg.buildInputs)
      ) self;
    };

in

{
  mkDerivation = args: stdenv.mkDerivation (args // defaults args);

  inherit withPackages withPackages';
}
