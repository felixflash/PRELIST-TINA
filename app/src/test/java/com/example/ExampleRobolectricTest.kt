package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.SecurityUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `verify app name resource matches Vina Prelist`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Vina Prelist", appName)
    }

    @Test
    fun `verify password rules integrity`() {
        // Must fail: < 8 characters
        assertNotNull(SecurityUtils.validatePassword("Ab1!"))

        // Must fail: no digit
        assertNotNull(SecurityUtils.validatePassword("Password!"))

        // Must fail: no special character
        assertNotNull(SecurityUtils.validatePassword("Password123"))

        // Must pass: >= 8 chars with letter, digit, and special char
        assertNull(SecurityUtils.validatePassword("Admin123!"))
        assertNull(SecurityUtils.validatePassword("Customer123!"))
    }

    @Test
    fun `verify cryptographic checksum generation`() {
        val checksum1 = SecurityUtils.computeChecksum("req-101:batch-A:350.0")
        val checksum2 = SecurityUtils.computeChecksum("req-101:batch-A:350.0")
        val checksum3 = SecurityUtils.computeChecksum("req-101:batch-B:350.0")

        assertEquals(checksum1, checksum2)
        assert(checksum1 != checksum3)
        assertEquals(8, checksum1.length)
    }
}
