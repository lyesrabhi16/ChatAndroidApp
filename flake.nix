{
  description = "Chat app dev environment: Node server + Android build";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs = { self, nixpkgs }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs {
        inherit system;
        config = {
          allowUnfree = true;                  # the Android SDK is not free software
          android_sdk.accept_license = true;
        };
      };
      android = pkgs.androidenv.composeAndroidPackages {
        platformVersions = [ "33" ];           # compileSdk / targetSdk of the app
        buildToolsVersions = [ "30.0.3" "33.0.2" ];  # 30.0.3 is the default of Android Gradle Plugin 7.4
        includeEmulator = false;
        includeSystemImages = false;
      };
      sdkRoot = "${android.androidsdk}/libexec/android-sdk";
    in {
      devShells.${system} = {
        # nix develop           -> run and test the server (Node + Docker Compose)
        default = pkgs.mkShell {
          packages = with pkgs; [ nodejs_22 docker-compose ];
        };

        # nix develop .#android -> build the app:  ./gradlew assembleDebug
        android = pkgs.mkShell {
          packages = with pkgs; [ jdk17 nodejs_22 docker-compose ];
          JAVA_HOME = "${pkgs.jdk17}";
          ANDROID_HOME = sdkRoot;
          ANDROID_SDK_ROOT = sdkRoot;
          # the aapt2 that Gradle downloads is a generic Linux binary that cannot run on NixOS; use the SDK's own
          GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${sdkRoot}/build-tools/33.0.2/aapt2";
        };
      };
    };
}
