package com.spenditos.android.core.network

import okhttp3.OkHttpClient
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class NetworkModuleTest {
    @Test
    fun `auth refresh client has its own dispatcher so it cannot queue behind blocked requests`() {
        val base = OkHttpClient()
        val authed = base.newBuilder().build()
        // The authed client shares the base dispatcher (that is the problem being isolated).
        assertSame(base.dispatcher, authed.dispatcher)
        assertNotSame(base.dispatcher, NetworkModule.publicClient(base).dispatcher)
    }
}
