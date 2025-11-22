package dev.namn.steady_fetch.io

import dev.namn.steady_fetch.datamodels.DownloadMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import okhttp3.OkHttpClient

class Networking(private val okHttpClient: OkHttpClient) {
    val ioScope = CoroutineScope(Dispatchers.IO)

    fun getExpectedFileSize(url: String, headers: Map<String, String>): Long? {}
    fun doesServerSupportChunking(): Boolean {}
    fun download(downloadMetadata: DownloadMetadata){
       val semaphore = Semaphore(downloadMetadata.request.maxParallelDownloads)

        if(semaphore.tryAcquire()){
            ioScope.launch {

            }
        }
    }
}
