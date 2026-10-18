package com.androidagent.tool.file

import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Resolves a model-facing path to a concrete [java.nio.file.Path] confined to the
 * canonical APP_SELF root (AG.FILE-0 Preflight §18/§19, FROZEN).
 *
 * The model sees a root-relative path space: `notes.txt` means
 * `<root>/notes.txt`. An absolute path is accepted only if, after canonical
 * resolution, it lands inside the root subtree. Symlinks are resolved via
 * [Path.toRealPath] so a symlink that escapes the root is rejected, not followed.
 *
 * Two different notions are kept apart:
 *  - [Root]           = the canonical root directory (the boundary).
 *  - [ResolvedPath]   = the concrete target inside the root (may not exist yet
 *                       for stat; the boundary check still applies).
 *
 * This is the single tool-local "realm seam" for V0 (single realm = APP_SELF).
 * Future SAF / ADB_SHELL realms would add a different resolver; this function
 * shape is preserved. It is NOT a backend hierarchy — one concrete implementation.
 */
internal class PathResolver(rootFile: File) {

    private val root: Path = Paths.get(rootFile.absolutePath).let {
        if (it.toFile().exists()) it.toRealPath() else it.normalize()
    }

    val rootPath: Path get() = root

    sealed interface Result {
        data class Ok(val path: Path, val relDisplay: String) : Result
        data class OutsideRoot(val reason: String) : Result
    }

    /**
     * Resolve [rawPath] against the root and enforce the boundary.
     *
     * - relative -> root.resolve(rawPath)
     * - absolute -> use as-is, then require it to be inside the root subtree
     * - canonical (toRealPath if the target or its ancestors resolve) + boundary
     * - if the target does not exist, fall back to normalize() of the joined
     *   path so a `../` escape is still caught before any filesystem touch.
     */
    fun resolve(rawPath: String): Result {
        if (rawPath.isBlank()) return Result.OutsideRoot("path is blank")

        val joined: Path = if (isAbsolute(rawPath)) {
            Paths.get(rawPath)
        } else {
            root.resolve(rawPath)
        }

        val canonical: Path = try {
            // toRealPath() resolves symlinks and requires the path to exist.
            joined.toRealPath()
        } catch (e: Exception) {
            // Target (or an ancestor) does not exist: normalize the joined path.
            // This still collapses `..` so an escape is detected without touching
            // the filesystem.
            joined.normalize()
        }

        if (!isWithinRoot(canonical)) {
            return Result.OutsideRoot("resolves outside the file root")
        }

        // Display path is relative to the canonical root so the model sees the
        // same root-relative space it addressed (no `agent/agent/` duplication).
        val rel = if (root.startsWith(canonical) || canonical == root) {
            "."
        } else {
            root.relativize(canonical).toString().replace('\\', '/')
        }
        return Result.Ok(canonical, rel)
    }

    private fun isWithinRoot(candidate: Path): Boolean {
        val c = candidate.toAbsolutePath().normalize()
        return c == root || c.startsWith(root)
    }

    private fun isAbsolute(raw: String): Boolean {
        // Unix-style absolute (the only form meaningful on Android/Linux data dirs).
        return raw.startsWith("/")
    }

    companion object {
        internal fun toPath(file: File): Path = Paths.get(file.absolutePath)
        internal fun toPath(s: String): Path = Paths.get(s)
    }
}
