package com.androidagent.tool.file

/**
 * Frozen minimal error code set for the file tool (AG.FILE-0, task §13).
 *
 * Distinctions that must hold:
 *  - A clean argument decision (invalid / unknown action / missing path) is a
 *    mechanism failure (success = false) with one of these codes.
 *  - A path that resolves OUTSIDE the canonical root is PATH_OUTSIDE_ROOT
 *    (traversal / absolute escape / symlink escape / canonical escape).
 *  - NOT_FOUND / NOT_A_FILE / NOT_A_DIRECTORY are domain outcomes reported as
 *    success=false with a structured error (the model can adapt).
 *  - UNSUPPORTED_CONTENT covers binary / non-decodable content that is refused
 *    rather than force-decoded into mojibake.
 *  - CANCELLED is never a code here: a cancelled read/search propagates
 *    CancellationException (frozen runtime semantics) and never becomes a result.
 */
enum class FileError {
    INVALID_ARGUMENT,
    UNKNOWN_ACTION,
    PATH_OUTSIDE_ROOT,
    NOT_FOUND,
    NOT_A_FILE,
    NOT_A_DIRECTORY,
    UNSUPPORTED_CONTENT,
    LIMIT_EXCEEDED,
    IO_ERROR,
}
