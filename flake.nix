{
  description = "Aya proof assistant";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs =
    {
      self,
      nixpkgs,
      flake-utils,
    }:
    flake-utils.lib.eachDefaultSystem (
      system:
      let
        pkgs = import nixpkgs {
          inherit system;
        };

        # Parse gradle/libs.versions.toml for required project/jdk versions
        inherit (builtins.fromTOML (builtins.readFile ./gradle/libs.versions.toml)) versions;

        jdk = pkgs."jdk${versions.java}";
        gradle = pkgs.gradle_9;
      in
      {
        devShells.default = pkgs.mkShell {
          packages = [
            jdk
            gradle
          ];
        };
        packages = rec {
          Aya = pkgs.callPackage ./nix/package.nix {
            inherit jdk gradle;
            version = versions.project;
            rev = self.rev or "dirty";
          };
          ayaPackages = pkgs.lib.recurseIntoAttrs (
            pkgs.callPackage ./nix/aya-packages.nix {
              inherit Aya;
            }
          );
          inherit (ayaPackages) aya aya-minimal;
        };
      }
    );
}
