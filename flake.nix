{
  description = "Aya proof assistant";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";
    # Provide jdk22
    nixpkgs-24_05.url = "github:NixOS/nixpkgs/release-24.05";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs =
    {
      self,
      nixpkgs,
      nixpkgs-24_05,
      flake-utils,
    }:
    flake-utils.lib.eachDefaultSystem (
      system:
      let
        pkgs = import nixpkgs {
          inherit system;
          overlays = [
            # workaround for JDK 22 deprecation in latest nixpkgs
            (self: super: {
              jdk22 = nixpkgs-24_05.legacyPackages.${system}.jdk22;
            })
          ];
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
