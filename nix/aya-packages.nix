{
  # config,
  lib,
  newScope,
  Aya,
}:

# Scope for `ayaPackages`

let
  mkAyaPackages = Aya: lib.makeScope newScope (mkAyaPackages' Aya);
  mkAyaPackages' =
    Aya: self:
    let
      inherit (self) callPackage;
      inherit
        (callPackage ./build-support/aya {
          inherit Aya self;
        })
        withPackages
        mkDerivation
        ;
    in
    rec {
      inherit mkDerivation;

      standard-library = callPackage ./libraries/standard-library.nix {
        inherit (Aya) version;
      };

      # Include in-tree standard library by default
      aya = withPackages [ standard-library ];
      aya-minimal = withPackages [ ];
    };
in
mkAyaPackages Aya
