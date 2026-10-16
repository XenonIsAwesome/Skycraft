set(VCPKG_TARGET_ARCHITECTURE x64)
set(VCPKG_CRT_LINKAGE dynamic)
set(VCPKG_LIBRARY_LINKAGE static)

# Wine/Proton's built-in msvcp140 predates the MSVC 14.40 constexpr std::mutex and crashes on the
# first lock; keep the old, runtime-initialised mutex so the plugin loads there too.
set(VCPKG_CXX_FLAGS "/D_DISABLE_CONSTEXPR_MUTEX_CONSTRUCTOR")
set(VCPKG_C_FLAGS "")
