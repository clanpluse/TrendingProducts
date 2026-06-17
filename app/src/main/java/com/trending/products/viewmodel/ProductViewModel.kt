package com.trending.products.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trending.products.data.model.Product
import com.trending.products.data.network.TrendingJsonData
import com.trending.products.data.repository.ProductRepository
import kotlinx.coroutines.launch

sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}

class ProductViewModel : ViewModel() {

    private val repository = ProductRepository()
    private var cached: TrendingJsonData? = null

    /** الفترة الزمنية المختارة: day / week / month */
    var timeframe: String = "day"
        private set

    private val _topSelling = MutableLiveData<UiState<List<Product>>>()
    val topSelling: LiveData<UiState<List<Product>>> = _topSelling

    private val _alibaba = MutableLiveData<UiState<List<Product>>>()
    val alibaba: LiveData<UiState<List<Product>>> = _alibaba

    private val _trending = MutableLiveData<UiState<List<Product>>>()
    val trending: LiveData<UiState<List<Product>>> = _trending

    private val _newBestSellers = MutableLiveData<UiState<List<Product>>>()
    val newBestSellers: LiveData<UiState<List<Product>>> = _newBestSellers

    private val _exclusive = MutableLiveData<UiState<List<Product>>>()
    val exclusive: LiveData<UiState<List<Product>>> = _exclusive

    private val _lastUpdated = MutableLiveData<String>()
    val lastUpdated: LiveData<String> = _lastUpdated

    init { loadAll() }

    /** يجلب البيانات من الشبكة ثم يعرض الفترة المختارة. */
    fun loadAll() {
        setLoading()
        viewModelScope.launch {
            repository.getTrendingData()
                .onSuccess {
                    cached = it
                    _lastUpdated.value = it.updatedAt ?: "—"
                    emitFromCache()
                }
                .onFailure { setError(it.message ?: "تعذّر جلب البيانات.") }
        }
    }

    /** يبدّل الفترة الزمنية ويعرض من البيانات المخزّنة دون إعادة تحميل. */
    fun setTimeframe(tf: String) {
        if (tf == timeframe) return
        timeframe = tf
        if (cached != null) emitFromCache() else loadAll()
    }

    private fun emitFromCache() {
        val slice = repository.sliceTimeframe(cached, timeframe)
        _topSelling.value     = toState(slice.topSelling, "لا توجد منتجات في هذه الفترة.")
        _alibaba.value        = toState(slice.alibaba, "لا توجد بيانات علي بابا في هذه الفترة.")
        _trending.value       = toState(slice.trending, "لا توجد منتجات رائجة في هذه الفترة.")
        _newBestSellers.value = toState(slice.newBestSellers, "لا توجد منتجات جديدة في هذه الفترة.")
        _exclusive.value      = toState(slice.exclusive, "لا توجد منتجات حصرية في هذه الفترة.")
    }

    private fun toState(list: List<Product>, emptyMsg: String): UiState<List<Product>> =
        if (list.isEmpty()) UiState.Error(emptyMsg) else UiState.Success(list)

    private fun setLoading() {
        _topSelling.value = UiState.Loading
        _alibaba.value = UiState.Loading
        _trending.value = UiState.Loading
        _newBestSellers.value = UiState.Loading
        _exclusive.value = UiState.Loading
    }

    private fun setError(msg: String) {
        _topSelling.value = UiState.Error(msg)
        _alibaba.value = UiState.Error(msg)
        _trending.value = UiState.Error(msg)
        _newBestSellers.value = UiState.Error(msg)
        _exclusive.value = UiState.Error(msg)
    }
}
