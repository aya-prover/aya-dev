{
  lib,
  stdenv,
  fetchFromGitHub,
  makeWrapper,
  gradle,
  jdk,
  version ? "0-unstable-SNAPSHOT",
  rev,
  mainProgram ? "aya",
}:

# Build base package `Aya`

stdenv.mkDerivation (finalAttrs: {
  pname = "aya-prover";
  inherit version;

  src = lib.fileset.toSource {
    root = ../.;
    fileset = lib.fileset.intersection (lib.fileset.fromSource (lib.sources.cleanSource ../.)) (
      lib.fileset.unions [
        ../base
        ../buildSrc
        ../cli-console
        ../cli-impl
        ../gradle
        ../ide
        ../ide-lsp
        ../jit-compiler
        ../parser
        ../pretty
        ../producer
        ../syntax
        ../tools
        ../tools-kala
        ../tools-md
        ../tools-repl
        ../build.gradle.kts
        ../settings.gradle.kts
      ]
    );
  };

  patches = [
    ./patches/0001-fix-patch-on-gradle-deps-resolution.patch
    ./patches/0002-fix-patch-GenerateVersionTask.patch
  ];

  postPatch = ''
    substituteInPlace buildSrc/src/main/groovy/org/aya/gradle/GenerateVersionTask.groovy \
      --replace-fail "\"__COMMIT_HASH__\"" "\"${rev}\""
  '';

  nativeBuildInputs = [
    gradle
    makeWrapper
  ];

  mitmCache = gradle.fetchDeps {
    pkg = finalAttrs.finalPackage;
    data = ./deps.json;
  };

  # this is required for using mitm-cache on Darwin
  __darwinAllowLocalNetworking = true;

  gradleFlags = [
    "-Dorg.gradle.java.home=${jdk}"
    # "--debug"
    # "--stacktrace"
  ];

  # defaults to "assemble"
  gradleBuildTask = "fatJar";

  # will run the gradleCheckTask (defaults to "test")
  # FIXME: fix gradle check
  doCheck = false;

  installPhase = ''
    runHook preInstall

    mkdir -p $out/{bin,share/{aya,aya-lsp}}
    cp cli-console/build/libs/cli-console-*-fat.jar $out/share/aya/cli-fatjar.jar
    cp ide-lsp/build/libs/ide-lsp-*-fat.jar $out/share/aya-lsp/lsp-fatjar.jar

    makeWrapper ${lib.getExe jdk} $out/bin/aya \
      --add-flags "--enable-preview -jar $out/share/aya/cli-fatjar.jar"
    makeWrapper ${lib.getExe jdk} $out/bin/aya-lsp \
      --add-flags "--enable-preview -jar $out/share/aya-lsp/lsp-fatjar.jar"

    runHook postInstall
  '';

  meta = {
    description = "Proof assistant designed for formalizing math and type-directed programming";
    homepage = "https://www.aya-prover.org";
    licence = lib.licenses.mit;
    inherit mainProgram;
    maintainers = with lib.maintainers; [ definfo ];
    # See `supportedPlatforms` in build.gradle.kts
    platforms = [
      "aarch64-windows"
      "x86_64-windows"
      "aarch64-linux"
      "x86_64-linux"
      "riscv64-linux"
      "aarch64-darwin"
      "x86_64-darwin"
    ];
    sourceProvenance = with lib.sourceTypes; [
      fromSource
      binaryBytecode # mitm cache
    ];
  };
})
