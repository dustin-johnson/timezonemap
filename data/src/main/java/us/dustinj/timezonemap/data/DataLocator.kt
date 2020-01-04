@file:JvmName("DataLocator")

package us.dustinj.timezonemap.data

import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream
import java.io.IOException
import java.io.InputStream

@Throws(IOException::class)
fun getDataInputStream(): InputStream {
    val inputStream = object {}.javaClass.getResourceAsStream("/$mapArchiveFilename")
    checkLoadedInputStream(inputStream)
    return ZstdCompressorInputStream(inputStream)
}

val mapArchiveFilename get() = BuildInformation.MAP_FILENAME
val mapVersion get() = BuildInformation.MAP_VERSION

fun checkLoadedInputStream(stream: InputStream?) {
    checkNotNull(stream) {
        "Time zone data is not found. Perhaps there is an issue with the class loader or this is being run from the " +
                "IDE without having built with maven first."
    }
}