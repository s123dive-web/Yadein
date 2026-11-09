# Benchmark build only: keep R8's shrinking and optimisation (so startup numbers match the beta APK) but not its
# renaming, so the baseline profile recorded on this build uses real class names that apply to the beta and release builds.
-dontobfuscate
