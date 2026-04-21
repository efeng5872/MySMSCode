package com.example.mysmscode.data

sealed interface RepositorySaveResult<out T> {
    data class Success<T>(
        val value: T,
    ) : RepositorySaveResult<T>

    data object DuplicateName : RepositorySaveResult<Nothing>

    data object DuplicateSenderNumber : RepositorySaveResult<Nothing>

    data object NotFound : RepositorySaveResult<Nothing>

    data object Failed : RepositorySaveResult<Nothing>
}
