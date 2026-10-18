package io.github.aedev.flow.data.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountRepositoryTest {
    @Test
    fun accountProfile_serialization_preservesFields() {
        val profile =
            AccountProfile(
                id = "test_123",
                name = "Test User",
                email = "test@example.com",
                channelHandle = "@testuser",
                avatarUrl = "https://example.com/avatar.jpg",
                cookie = "SAPISID=12345",
                dataSyncId = "sync_abc",
                isActive = true,
            )

        assertEquals("test_123", profile.id)
        assertEquals("Test User", profile.name)
        assertEquals("@testuser", profile.channelHandle)
        assertTrue(profile.isActive)
    }

    @Test
    fun accountProfile_copy_updatesActiveState() {
        val profile =
            AccountProfile(
                id = "test_123",
                name = "Test User",
                cookie = "SAPISID=12345",
                isActive = false,
            )

        val updated = profile.copy(isActive = true)
        assertTrue(updated.isActive)
        assertFalse(profile.isActive)
    }
}
