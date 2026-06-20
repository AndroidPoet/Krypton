package io.krypton.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.KonanTarget
import java.io.File

/**
 * `kryptonLibsignal { ... }` — configure how libsignal_ffi is obtained.
 *
 * The libsignal `version` MUST match the version Krypton is pinned to.
 */
abstract class KryptonLibsignalExtension {
    /** libsignal version, e.g. "0.86.5". Must equal Krypton's pinned version. */
    abstract val version: Property<String>

    /** "build" (git clone signalapp/libsignal + cargo) or "download" (official prebuilt). */
    abstract val mode: Property<String>

    /** Root cache dir; the .a lands in <cacheDir>/<version>/<arch>/libsignal_ffi.a */
    abstract val cacheDir: Property<String>
}

/**
 * Provides `fetchLibsignal` and auto-wires the fetched libsignal_ffi.a onto every
 * Apple native target, so a consumer just does:
 *
 *   plugins { id("io.krypton.libsignal") version "0.1.0" }
 *   kryptonLibsignal { version.set("0.86.5") }
 *
 * Krypton ships no Signal binary; this generates it from Signal's official source
 * on the consumer's own machine.
 */
class LibsignalPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val ext = project.extensions.create("kryptonLibsignal", KryptonLibsignalExtension::class.java)
        ext.version.convention("0.86.5")
        ext.mode.convention("build")
        ext.cacheDir.convention("${System.getProperty("user.home")}/.krypton/libsignal")

        val fetch = project.tasks.register("fetchLibsignal") { task ->
            task.group = "krypton"
            task.description = "Fetch libsignal_ffi from Signal's official source into the cache."
            task.doLast {
                val out = File(ext.cacheDir.get(), ext.version.get())
                fetchLibsignal(project, ext.version.get(), ext.mode.get(), out)
            }
        }

        // Auto-wire onto every Apple native target once Kotlin Multiplatform is applied.
        project.plugins.withId("org.jetbrains.kotlin.multiplatform") {
            val kmp = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
            val base = File(ext.cacheDir.get(), ext.version.get())
            kmp.targets.withType(KotlinNativeTarget::class.java).configureEach { target ->
                val arch = appleArchDir(target.konanTarget) ?: return@configureEach
                target.binaries.configureEach { binary ->
                    binary.linkerOpts("-L${File(base, arch).absolutePath}")
                    binary.linkTaskProvider.configure { it.dependsOn(fetch) }
                }
            }
        }
    }

    /** KonanTarget -> the arch subdir Krypton's cinterop expects. null = not Apple. */
    private fun appleArchDir(t: KonanTarget): String? = when (t) {
        KonanTarget.MACOS_ARM64 -> "macos-arm64"
        KonanTarget.MACOS_X64 -> "macos-x64"
        KonanTarget.IOS_ARM64 -> "ios-arm64"
        KonanTarget.IOS_SIMULATOR_ARM64 -> "ios-sim-arm64"
        KonanTarget.IOS_X64 -> "ios-sim-x64"
        else -> null
    }

    /** triple -> arch subdir, for cargo build mode. */
    private val triples = listOf(
        "aarch64-apple-darwin" to "macos-arm64",
        "x86_64-apple-darwin" to "macos-x64",
        "aarch64-apple-ios" to "ios-arm64",
        "aarch64-apple-ios-sim" to "ios-sim-arm64",
        "x86_64-apple-ios" to "ios-sim-x64",
    )

    private fun fetchLibsignal(project: Project, version: String, mode: String, out: File) {
        out.mkdirs()
        project.logger.lifecycle("Krypton: fetching libsignal $version ($mode) -> $out")
        when (mode) {
            "download" -> downloadOfficial(project, version, out)
            else -> buildFromSource(project, version, out)
        }
    }

    private fun buildFromSource(project: Project, version: String, out: File) {
        val work = File(System.getProperty("java.io.tmpdir"), "libsignal-$version")
        if (!File(work, ".git").exists()) {
            project.exec {
                it.commandLine(
                    "git", "clone", "--depth", "1", "--branch", "v$version",
                    "https://github.com/signalapp/libsignal", work.absolutePath,
                )
            }
        }
        for ((triple, arch) in triples) {
            project.exec { it.workingDir(work); it.commandLine("rustup", "target", "add", triple); it.isIgnoreExitValue = true }
            project.exec { it.workingDir(work); it.commandLine("cargo", "build", "-p", "libsignal-ffi", "--release", "--target", triple) }
            val a = File(work, "target/$triple/release/libsignal_ffi.a")
            val dst = File(out, arch).apply { mkdirs() }
            a.copyTo(File(dst, "libsignal_ffi.a"), overwrite = true)
        }
    }

    private fun downloadOfficial(project: Project, version: String, out: File) {
        val archive = "libsignal-client-ios-build-v$version.tar.gz"
        val url = "https://build-artifacts.signal.org/libraries/$archive"
        val dest = File(out, archive)
        project.logger.lifecycle("Krypton: downloading Signal's official prebuilt: $url")
        java.net.URL(url).openStream().use { input -> dest.outputStream().use { input.copyTo(it) } }
        val expected = System.getenv("LIBSIGNAL_FFI_PREBUILD_CHECKSUM")
        if (!expected.isNullOrBlank()) {
            val actual = java.security.MessageDigest.getInstance("SHA-256")
                .digest(dest.readBytes()).joinToString("") { "%02x".format(it) }
            check(actual == expected) { "libsignal checksum mismatch: expected $expected, got $actual" }
            project.logger.lifecycle("Krypton: checksum verified against Signal's published SHA-256.")
        } else {
            project.logger.warn("Krypton: no LIBSIGNAL_FFI_PREBUILD_CHECKSUM set — download is UNVERIFIED.")
        }
        project.exec { it.commandLine("tar", "-xzf", dest.absolutePath, "-C", out.absolutePath) }
        // Signal's archive lays the .a out as target/<triple>/release/libsignal_ffi.a; relocate each
        // into the <arch>/ layout the linker auto-wiring expects. The prebuilt is iOS-only.
        for ((triple, arch) in triples) {
            val extracted = File(out, "target/$triple/release/libsignal_ffi.a")
            if (extracted.exists()) {
                val dst = File(out, arch).apply { mkdirs() }
                extracted.copyTo(File(dst, "libsignal_ffi.a"), overwrite = true)
            }
        }
        File(out, "target").deleteRecursively()
        project.logger.lifecycle(
            "Krypton: prebuilt is iOS-only (no macOS). For macOS targets use mode \"build\".",
        )
    }
}
