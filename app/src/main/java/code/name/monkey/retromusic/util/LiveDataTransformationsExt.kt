package code.name.monkey.retromusic.util

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import kotlinx.coroutines.*

fun <X, Y>LiveData<X>.mapAsync(coroutineScope: CoroutineScope, workDispatcher: CoroutineDispatcher = Dispatchers.Default, mapper: suspend (X) -> Y): LiveData<Y> {
    val result = MediatorLiveData<Y>()
    result.addSource(this) {
        coroutineScope.launch(workDispatcher) {
            val value = mapper(it)
            withContext(Dispatchers.Main) {
                result.value = value!!
            }
        }
    }
    return result
}

fun <X>LiveData<List<X>>.filter(filter: (X) -> Boolean): LiveData<List<X>> {
    val result = MediatorLiveData<List<X>>()
    result.addSource(this) {
        result.value = it.filter(filter)
    }
    return result
}