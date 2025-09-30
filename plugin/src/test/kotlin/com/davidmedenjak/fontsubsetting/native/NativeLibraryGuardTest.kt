package com.davidmedenjak.fontsubsetting.native

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.File

/**
 * Guard test that FAILS (not skips) when the native library is missing or broken.
 *
 * All other test classes use assumeTrue() which silently skips tests when the
 * native library is unavailable. This means CI would report "PASSED (0 failures)"
 * with zero actual test coverage. This class ensures that doesn't happen.
 */
class NativeLibraryGuardTest {

    @Test
    fun `native library loads successfully`() {
        assertThat(HarfBuzzSubsetter.isNativeLibraryAvailable())
            .withFailMessage(
                "Native library not available. All other native tests will be silently skipped. " +
                "Rebuild with: cd plugin && ./build-in-docker.sh"
            )
            .isTrue()
    }

    @Test
    fun `native library is functional`() {
        val fontPath = System.getProperty("test.font.path")
            ?: error("System property 'test.font.path' not set")
        val fontFile = File(fontPath)

        assertThat(fontFile).exists()

        val subsetter = HarfBuzzSubsetter()
        assertThat(subsetter.validateFont(fontFile.absolutePath))
            .withFailMessage("validateFont() returned false for known-good font")
            .isTrue()
        assertThat(subsetter.getFontInfo(fontFile.absolutePath))
            .withFailMessage("getFontInfo() returned null for known-good font")
            .isNotNull()
    }
}
