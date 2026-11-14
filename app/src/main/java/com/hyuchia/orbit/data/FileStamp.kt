package com.hyuchia.orbit.data

import android.system.ErrnoException
import android.system.Os
import android.system.StructStat
import android.system.StructTimespec
import java.io.File
import java.io.FileDescriptor
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.util.concurrent.TimeUnit

/**
 * A file as it is now, to tell whether it's still the one it was: its path
 * and size, when it was last written, to the nanosecond, and which file it
 * is on its storage. On Linux, as on Android, also when it last changed
 * (its inode's change time), which each write sets anew and no app can set
 * back, as an app can its time: so a file written again in place, at the
 * same size, with its time set back as a sync app sets it, is another, and
 * so is one put in its place under its name, which is another file on its
 * storage. Null when the file can't be looked at, as when it went.
 *
 * What's said of a file open to be read is said by its descriptor
 * ([isOpenAt]), never by its path: the path may lead to another file by
 * then, and the descriptor is the file read. Moving a file, or putting
 * another in its place, sets its change time too, so a file open as one
 * stamp says is the very one the stamp was taken of, unchanged. One thing
 * no stamp tells: a file written in place at the same size within the same
 * tick of the clock the stamp was taken in. Nothing writes a map's files in
 * place: they're written whole beside and moved in ([writeAtomically]).
 */
data class FileStamp(val path: String, val size: Long, val modified: Long, val changed: Long?, val identity: Any?) {
    /** Whether the file open at `descriptor` is this one, as it was: told by the descriptor itself. */
    fun isOpenAt(descriptor: FileDescriptor): Boolean =
        opened(descriptor, path)?.let { it.size == size && it.modified == modified && it.changed == changed && it.identity == identity } == true

    companion object {
        /**
         * The stamp of `file` itself, never of what it links to: a link put
         * in its place is another file, and what's read through it never
         * matches the stamp ([isOpenAt]).
         */
        fun of(file: File): FileStamp? = try {
            if (ON_ANDROID) android(file) else unix(file.toPath(), file.path, LinkOption.NOFOLLOW_LINKS) ?: basic(file)
        } catch (unreadable: IOException) {
            null
        } catch (refused: SecurityException) {
            null
        }

        /** What the file open at `descriptor` is, said for `path`, or null when it can't be told. */
        private fun opened(descriptor: FileDescriptor, path: String): FileStamp? = try {
            if (ON_ANDROID) {
                stamp(Os.fstat(descriptor), path)
            } else {
                // Linux's own link to what a descriptor reads, which goes to that file whatever its path leads to now. A JVM that keeps
                // descriptors' numbers to itself, as one started without the tests' --add-opens (a test's own JVM), has only the path to
                // say what was opened, right after it was: never on Android, which says it of the descriptor itself, above.
                descriptorNumber(descriptor)?.let { unix(Paths.get("/proc/self/fd/$it"), path) } ?: unix(Paths.get(path), path, LinkOption.NOFOLLOW_LINKS)
            }
        } catch (failed: ErrnoException) {
            null
        } catch (unreadable: IOException) {
            null
        } catch (refused: SecurityException) {
            null
        }

        /** The stamp from what Android says of the file. */
        private fun android(file: File): FileStamp? = try {
            stamp(Os.lstat(file.path), file.path)
        } catch (failed: ErrnoException) {
            null
        }

        private fun stamp(stat: StructStat, path: String): FileStamp =
            FileStamp(path, stat.st_size, nanoseconds(stat.st_mtim), nanoseconds(stat.st_ctim), "${stat.st_dev}:${stat.st_ino}")

        private fun nanoseconds(time: StructTimespec): Long = time.tv_sec * 1_000_000_000L + time.tv_nsec

        /** The stamp from what Linux says of the file at `at`, or null where its storage doesn't say. */
        private fun unix(at: Path, path: String, vararg options: LinkOption): FileStamp? {
            val attributes = try {
                Files.readAttributes(at, "unix:size,lastModifiedTime,ctime,ino,dev", *options)
            } catch (unsupported: UnsupportedOperationException) {
                return null
            } catch (unknown: IllegalArgumentException) {
                return null
            }

            val size = attributes["size"] as? Long ?: return null
            val modified = (attributes["lastModifiedTime"] as? FileTime)?.to(TimeUnit.NANOSECONDS) ?: return null
            val changed = (attributes["ctime"] as? FileTime)?.to(TimeUnit.NANOSECONDS) ?: return null
            return FileStamp(path, size, modified, changed, "${attributes["dev"]}:${attributes["ino"]}")
        }

        private fun basic(file: File): FileStamp {
            val attributes = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
            return FileStamp(file.path, attributes.size(), attributes.lastModifiedTime().to(TimeUnit.NANOSECONDS), changed = null, identity = attributes.fileKey())
        }

        /**
         * The number of `descriptor` on a JVM, as the tests run on, which
         * keeps it inside: its own access to it, which Gradle's test JVM
         * opens (`--add-opens`); null where it's closed, as in a JVM a test
         * starts of its own.
         */
        private fun descriptorNumber(descriptor: FileDescriptor): Int? = try {
            val access = Class.forName("jdk.internal.access.SharedSecrets").getMethod("getJavaIOFileDescriptorAccess").invoke(null)
            Class.forName("jdk.internal.access.JavaIOFileDescriptorAccess").getMethod("get", FileDescriptor::class.java).invoke(access, descriptor) as? Int
        } catch (closed: ReflectiveOperationException) {
            null
        } catch (closed: RuntimeException) {
            null
        }

        /** Whether this is Android, whose own calls say what a file and a descriptor are; elsewhere, as in tests, Java's and Linux's. */
        private val ON_ANDROID = System.getProperty("java.vendor").orEmpty().contains("Android")
    }
}
