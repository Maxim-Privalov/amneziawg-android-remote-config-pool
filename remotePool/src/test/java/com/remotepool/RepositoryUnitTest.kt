package com.experum.remotepool

import com.remotepool.RemotePoolRepository
import com.remotepool.models.AuthUser
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class RepositoryUnitTest {
    @Test
    fun getTokenFromCredentials() = runTest {
        val user = AuthUser("", "", "")
        val repo = RemotePoolRepository()
        launch {
            val token = repo.getToken(user, "")
            println(token)
        }

    }
}