package com.example.data.network

import android.content.Context
import android.util.Base64
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

object GmailSmtpClient {

    fun isConfigured(context: Context?): Boolean {
        if (context == null) return false
        val prefs = context.getSharedPreferences("finderkit_smtp_prefs", Context.MODE_PRIVATE)
        val user = prefs.getString("gmail_user", null)?.ifBlank { null }
            ?: BuildConfig.GMAIL_SMTP_USER.ifBlank { null }
        val pass = prefs.getString("gmail_password", null)?.ifBlank { null }
            ?: BuildConfig.GMAIL_SMTP_PASSWORD.ifBlank { null }
        return !user.isNullOrBlank() && !pass.isNullOrBlank()
    }

    fun getSmtpUser(context: Context?): String {
        if (context == null) return BuildConfig.GMAIL_SMTP_USER
        val prefs = context.getSharedPreferences("finderkit_smtp_prefs", Context.MODE_PRIVATE)
        return prefs.getString("gmail_user", null)?.ifBlank { null }
            ?: BuildConfig.GMAIL_SMTP_USER
    }

    fun saveSmtpCredentials(context: Context?, gmailUser: String, appPassword: String) {
        if (context == null) return
        val prefs = context.getSharedPreferences("finderkit_smtp_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("gmail_user", gmailUser.trim())
            .putString("gmail_password", appPassword.trim())
            .apply()
    }

    suspend fun sendVerificationEmail(
        context: Context?,
        recipientEmail: String,
        verificationCode: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (context == null) {
            return@withContext Result.failure(IllegalStateException("Context is null."))
        }
        val prefs = context.getSharedPreferences("finderkit_smtp_prefs", Context.MODE_PRIVATE)
        val smtpUser = prefs.getString("gmail_user", null)?.ifBlank { null }
            ?: BuildConfig.GMAIL_SMTP_USER.ifBlank { null }

        val rawPassword = prefs.getString("gmail_password", null)?.ifBlank { null }
            ?: BuildConfig.GMAIL_SMTP_PASSWORD.ifBlank { null }

        if (smtpUser.isNullOrBlank() || rawPassword.isNullOrBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Gmail SMTP credentials not configured. Please configure your Gmail address & 16-character App Password.")
            )
        }

        val cleanPassword = rawPassword.replace(" ", "")

        try {
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            val socket = factory.createSocket("smtp.gmail.com", 465) as SSLSocket
            socket.soTimeout = 12000

            val reader = BufferedReader(InputStreamReader(socket.inputStream, "UTF-8"))
            val writer = PrintWriter(OutputStreamWriter(socket.outputStream, "UTF-8"), true)

            fun readResponse(): String {
                val line = reader.readLine() ?: throw IllegalStateException("Connection closed by server.")
                return line
            }

            fun sendCommand(cmd: String, expectedCodePrefix: String) {
                writer.print("$cmd\r\n")
                writer.flush()
                val response = readResponse()
                if (!response.startsWith(expectedCodePrefix)) {
                    throw IllegalStateException("SMTP command '$cmd' failed: $response")
                }
            }

            // 1. Read greeting (220)
            val greeting = readResponse()
            if (!greeting.startsWith("220")) {
                throw IllegalStateException("Failed to connect to Gmail SMTP: $greeting")
            }

            // 2. EHLO
            writer.print("EHLO smtp.gmail.com\r\n")
            writer.flush()
            val ehloResp = readResponse()
            if (!ehloResp.startsWith("250")) {
                throw IllegalStateException("EHLO rejected: $ehloResp")
            }
            while (reader.ready()) {
                val line = reader.readLine() ?: break
                if (line.startsWith("250 ")) break
            }

            // 3. AUTH LOGIN
            sendCommand("AUTH LOGIN", "334")

            // 4. Send Base64 Username
            val userB64 = Base64.encodeToString(smtpUser.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            sendCommand(userB64, "334")

            // 5. Send Base64 Password
            val passB64 = Base64.encodeToString(cleanPassword.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            sendCommand(passB64, "235")

            // 6. MAIL FROM
            sendCommand("MAIL FROM:<$smtpUser>", "250")

            // 7. RCPT TO
            sendCommand("RCPT TO:<${recipientEmail.trim()}>", "250")

            // 8. DATA
            sendCommand("DATA", "354")

            // 9. Email Body
            val emailContent = buildString {
                val rfcDateFormat = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", java.util.Locale.US)
                val dateHeader = rfcDateFormat.format(java.util.Date())
                val messageIdHeader = "<${java.util.UUID.randomUUID()}@gmail.com>"

                append("From: Vina Prelist <$smtpUser>\r\n")
                append("To: ${recipientEmail.trim()}\r\n")
                append("Reply-To: Vina Prelist <$smtpUser>\r\n")
                append("Subject: Your Vina Prelist Verification Code: $verificationCode\r\n")
                append("Date: $dateHeader\r\n")
                append("Message-ID: $messageIdHeader\r\n")
                append("Auto-Submitted: auto-generated\r\n")
                append("Content-Type: text/html; charset=UTF-8\r\n")
                append("MIME-Version: 1.0\r\n")
                append("\r\n")
                append("""
                    <!DOCTYPE html>
                    <html>
                    <body style="font-family: Arial, sans-serif; background-color: #f4f6f9; padding: 20px; margin: 0;">
                        <div style="max-width: 500px; margin: auto; background: #ffffff; padding: 30px; border-radius: 12px; border: 1px solid #e2e8f0; box-shadow: 0 4px 12px rgba(0,0,0,0.05);">
                            <div style="text-align: center; margin-bottom: 20px;">
                                <h2 style="color: #1e3a8a; margin: 0; font-size: 22px;">Vina Prelist</h2>
                                <p style="color: #64748b; font-size: 13px; margin-top: 4px;">Factory Sourcing & Curated Pre-Orders</p>
                            </div>
                            <hr style="border: none; border-top: 1px solid #e2e8f0; margin: 20px 0;" />
                            <p style="color: #334155; font-size: 15px; line-height: 1.5;">Hello,</p>
                            <p style="color: #334155; font-size: 15px; line-height: 1.5;">Your requested 6-digit security verification code is:</p>
                            <div style="text-align: center; margin: 28px 0;">
                                <span style="font-size: 34px; font-weight: bold; letter-spacing: 8px; color: #2563eb; background: #eff6ff; padding: 14px 28px; border-radius: 10px; border: 1px solid #bfdbfe; display: inline-block;">$verificationCode</span>
                            </div>
                            <p style="color: #64748b; font-size: 13px; line-height: 1.5;">Enter this code in the app to complete your verification. This code expires in 10 minutes.</p>
                            <p style="color: #94a3b8; font-size: 12px; margin-top: 24px;">If you did not request this verification code, please ignore this email.</p>
                        </div>
                    </body>
                    </html>
                """.trimIndent())
                append("\r\n.\r\n")
            }

            writer.print(emailContent)
            writer.flush()

            val dataResp = readResponse()
            if (!dataResp.startsWith("250")) {
                throw IllegalStateException("Failed to send message content: $dataResp")
            }

            // 10. QUIT
            try {
                writer.print("QUIT\r\n")
                writer.flush()
            } catch (_: Exception) {}

            socket.close()
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
