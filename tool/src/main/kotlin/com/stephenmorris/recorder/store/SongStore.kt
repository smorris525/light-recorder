package com.stephenmorris.recorder.store

import com.stephenmorris.recorder.tape.MixLevels
import com.stephenmorris.recorder.tape.Song
import com.stephenmorris.recorder.tape.SongCoding
import java.io.File
import java.util.UUID

/**
 * The songs on disk (spec §3), under [root] (`filesDir/Songs`): every song in `songs.json`; each recording in
 * `Clips/<id>.clip`; each song's last measured mix in `Mix/<song id>.mix`, so its waveform shows the moment it opens.
 * Reads are done in place; writes go one at a time through [background], each replacing its file whole, so a file is
 * never left half written. Nothing here throws: a file that can't be read is missing, and a write that fails leaves the
 * old file as it was.
 */
class SongStore(private val root: File, private val background: (Runnable) -> Unit) {
    private val songsFile = File(root, "songs.json")
    private val clips = File(root, "Clips")
    private val mixes = File(root, "Mix")

    /**
     * Whether the songs last loaded were all of them. After a file that couldn't all be read, [removeUnused] keeps every
     * recording, since some may belong to the songs that were skipped.
     */
    @Volatile
    private var isComplete = true

    /**
     * The saved songs; none when there's no file yet or it can't be read. A file with songs that can't be read is kept
     * aside as `songs.json.<time>.bak` before it's next written over, so they aren't lost for good.
     */
    fun loadSongs(): List<Song> {
        val bytes = read(songsFile) ?: return emptyList()
        val (songs, complete) = SongCoding.decodeReporting(bytes.decodeToString())
        if (!complete) {
            isComplete = false
            File(root, "songs.json.${System.currentTimeMillis()}.bak").let { backup ->
                runCatching { backup.writeBytes(bytes) }
                    .onFailure { System.err.println("SongStore: couldn't keep ${backup.name}: $it") }
            }
        }
        return songs
    }

    fun save(songs: List<Song>) {
        background.invoke(Runnable { write(songsFile, SongCoding.encode(songs).encodeToByteArray()) })
    }

    fun saveClip(id: UUID, samples: FloatArray, sampleRate: Double) {
        // Streamed, so a long recording isn't copied into one more array to write it.
        background.invoke(Runnable { write(clipFile(id)) { ClipCodec.write(it, samples, sampleRate) } })
    }

    /** A recording's samples at [sampleRate], resampled if it was recorded at another rate; `null` when it isn't there. */
    fun loadClip(id: UUID, sampleRate: Double): FloatArray? {
        val (rate, samples) = read(clipFile(id))?.let(ClipCodec::decode) ?: return null
        return Resampler.linear(samples, rate, sampleRate)
    }

    fun saveMix(levels: MixLevels, song: UUID) {
        background.invoke(Runnable { write(mixFile(song), ClipCodec.encode(levels.peaks, levels.sampleRate)) })
    }

    fun loadMix(song: UUID): MixLevels? {
        val (rate, peaks) = read(mixFile(song))?.let(ClipCodec::decode) ?: return null
        return MixLevels(rate, peaks)
    }

    /** Removes recordings no song plays and deleted songs' mixes, after the writes already queued. */
    fun removeUnused(songs: List<Song>) {
        if (!isComplete) {
            return
        }
        val keptClips = songs.flatMap { song -> song.tracks.flatMap { track -> track.spans.mapNotNull { it.clip } } }
            .map { it.toString() }
            .toSet()
        val keptSongs = songs.map { it.id.toString() }.toSet()
        background.invoke(
            Runnable {
                remove(clips) { it !in keptClips }
                remove(mixes) { it !in keptSongs }
            },
        )
    }

    private fun clipFile(id: UUID) = File(clips, "$id.clip")

    private fun mixFile(song: UUID) = File(mixes, "$song.mix")

    private fun read(file: File): ByteArray? = try {
        if (file.isFile) file.readBytes() else null
    } catch (exception: Exception) {
        System.err.println("SongStore: couldn't read ${file.name}: $exception")
        null
    }

    private fun write(file: File, bytes: ByteArray) = write(file) { it.write(bytes) }

    private fun write(file: File, writeTo: (java.io.OutputStream) -> Unit) {
        val temp = File(file.parentFile, "${file.name}.tmp")
        try {
            file.parentFile?.mkdirs()
            temp.outputStream().buffered().use(writeTo)
            if (!temp.renameTo(file)) {
                throw java.io.IOException("rename failed")
            }
        } catch (exception: Exception) {
            System.err.println("SongStore: couldn't write ${file.name}: $exception")
            if (temp.isFile) {
                temp.delete()
            }
        }
    }

    private fun remove(folder: File, isUnused: (String) -> Boolean) {
        folder.listFiles()?.forEach { file ->
            if (isUnused(file.nameWithoutExtension)) {
                file.delete()
            }
        }
    }
}
