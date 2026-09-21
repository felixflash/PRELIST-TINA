package com.example.data.network

import android.content.Context
import android.net.Uri
import com.example.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

object SupabaseStorageClient {
    private val client = OkHttpClient()

    fun uploadImageSync(context: Context?, imageUriString: String): String {
        if (context == null || imageUriString.isBlank()) return imageUriString
        if (imageUriString.startsWith("file://")) {
            return imageUriString // Already a persistent local file
        }
        if (!imageUriString.startsWith("content://")) {
            return imageUriString // Already an HTTP URL or other scheme
        }

        try {
            val uri = Uri.parse(imageUriString)
            val inputStream = context.contentResolver.openInputStream(uri) ?: return imageUriString
            val bytes = inputStream.readBytes()
            inputStream.close()

            val fileName = "${UUID.randomUUID()}.jpg"

            // 1. Save to internal app storage FIRST so content:// permissions don't expire across app restarts
            val storageDir = java.io.File(context.filesDir, "sourcing_photos").apply { if (!exists()) mkdirs() }
            val localFile = java.io.File(storageDir, fileName)
            localFile.writeBytes(bytes)
            val persistentFileUri = Uri.fromFile(localFile).toString()

            // 2. Try Supabase cloud storage upload if configured and not on main thread
            val supabaseUrl = BuildConfig.SUPABASE_URL
            val supabaseKey = BuildConfig.SUPABASE_KEY

            if (supabaseUrl.isNotBlank() && supabaseKey.isNotBlank()) {
                try {
                    val url = "$supabaseUrl/storage/v1/object/sourcing-photos/$fileName"
                    val requestBody = bytes.toRequestBody("image/jpeg".toMediaType())

                    val request = Request.Builder()
                        .url(url)
                        .addHeader("apikey", supabaseKey)
                        .addHeader("Authorization", "Bearer $supabaseKey")
                        .addHeader("Content-Type", "image/jpeg")
                        .post(requestBody)
                        .build()

                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        return "$supabaseUrl/storage/v1/object/public/sourcing-photos/$fileName"
                    }
                } catch (netEx: Throwable) {
                    // Ignored (e.g. NetworkOnMainThreadException or offline). Fall back to persistent local file.
                }
            }

            return persistentFileUri
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return imageUriString
    }
}
