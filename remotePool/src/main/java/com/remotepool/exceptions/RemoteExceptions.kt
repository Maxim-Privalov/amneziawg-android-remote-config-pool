package com.remotepool.exceptions

import java.io.IOException

/** The remote source rejected the credentials or the token (HTTP 401/403). */
class RemoteAuthException(val code: Int) : IOException("Remote authentication failed: HTTP $code")

/** Any other non-2xx answer of the remote source. */
class RemoteHttpException(val code: Int) : IOException("Remote request failed: HTTP $code")
