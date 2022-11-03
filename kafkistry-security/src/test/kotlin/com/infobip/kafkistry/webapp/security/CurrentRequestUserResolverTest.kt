package com.infobip.kafkistry.webapp.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.util.concurrent.Callable
import java.util.concurrent.Executors

internal class CurrentRequestUserResolverTest {

    private val userResolver = CurrentRequestUserResolver()
    private val tuser = User("tuser", "Unit", "Test", "no@email", UserRole("TEST"))

    @Test
    fun `no user`() {
        val user = userResolver.resolveUser()
        assertThat(user).isNull()
    }

    @Test
    fun `scoped user`() {
        val user = userResolver.withUser(tuser) {
            userResolver.resolveUser()
        }
        assertThat(user).isEqualTo(tuser)
    }

    @Test
    fun `scoped user cleared`() {
        userResolver.withUser(tuser) {}
        val user = userResolver.resolveUser()
        assertThat(user).isNull()
    }

    @Test
    fun `each thread separate user`() {
        val u1 = tuser.copy(username = "u1")
        val u2 = tuser.copy(username = "u2")

        val pool = Executors.newCachedThreadPool()
        try {
            val f1 = pool.submit(Callable {
                userResolver.withUser(u1) { sleepResolve() }
            })
            val f2 = pool.submit(Callable {
                userResolver.withUser(u2) { sleepResolve() }
            })
            val f3 = pool.submit(Callable {
                sleepResolve()
            })
            assertAll(
                { assertThat(userResolver.resolveUser()).`as`("Curr thread before").isNull() },
                { assertThat(f1.get()).`as`("Future 1").isEqualTo(u1) },
                { assertThat(f2.get()).`as`("Future 2").isEqualTo(u2) },
                { assertThat(f3.get()).`as`("Future 3").isNull() },
                { assertThat(userResolver.resolveUser()).`as`("Curr thread after").isNull() },
            )
        } finally {
            pool.shutdown()
        }
    }

    private fun sleepResolve(): User? {
        println("Resolving user [" + Thread.currentThread() + "]")
        Thread.sleep(300)
        return userResolver.resolveUser().also {
            Thread.sleep(300)
            println("Resolved user [" + Thread.currentThread() + "]: $it")
        }
    }

}