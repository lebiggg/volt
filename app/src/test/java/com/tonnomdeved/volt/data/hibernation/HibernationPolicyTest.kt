package com.tonnomdeved.volt.data.hibernation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HibernationPolicyTest {

    @Test fun `default values are sane`() {
        val p = HibernationPolicy(
            packageName = "com.example",
            level = HibernationLevel.SOFT
        )
        assertEquals(false, p.userPinned)
        assertEquals(false, p.userForceHibernate)
        assertEquals(0L, p.lastAppliedAt)
        assertEquals(null, p.lastWakeAt)
        assertEquals(0L, p.createdAt)
    }

    @Test fun `userPinned and userForceHibernate are mutually exclusive`() {
        assertThrows(IllegalArgumentException::class.java) {
            HibernationPolicy(
                packageName = "com.example",
                level = HibernationLevel.OFF,
                userPinned = true,
                userForceHibernate = true
            )
        }
    }

    @Test fun `empty package name is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            HibernationPolicy(packageName = "", level = HibernationLevel.SOFT)
        }
        assertThrows(IllegalArgumentException::class.java) {
            HibernationPolicy(packageName = "  ", level = HibernationLevel.SOFT)
        }
    }

    @Test fun `data class equality works across all fields`() {
        val a = HibernationPolicy("com.x", HibernationLevel.HARD, true, false, 1, 2, 3)
        val b = HibernationPolicy("com.x", HibernationLevel.HARD, true, false, 1, 2, 3)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }
}
