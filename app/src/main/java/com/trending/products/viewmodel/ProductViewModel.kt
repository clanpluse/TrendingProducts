package com.trending.products.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trending.products.data.model.Product
import com.trending.products.data.repository.ProductRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}

class ProductViewModel : ViewModel() {

    private val repository = ProductRepository()

    private val _topSelling = MutableLiveData<UiState<List<Product>>>()
    val topSelling: LiveData<UiState<List<Product>>> = _topSelling

    private val _chineseFactory = MutableLiveData<UiState<List<Product>>>()
    val chineseFactory: LiveData<UiState<List<Product>>> = _chineseFactory

    private val _hotNew = MutableLiveData<UiState<List<Product>>>()
    val hotNew: LiveData<UiState<List<Product>>> = _hotNew

    private val _lastUpdated = MutableLiveData<String>()
    val lastUpdated: LiveData<String> = _lastUpdated

    init { loadAll() }

    fun loadAll() {
        _lastUpdated.value = SimpleDateFormat("dd/MM/yyyy - HH:mm", Locale.getDefault()).format(Date())
        loadTopSelling()
        loadChineseFactory()
        loadHotNew()
    }

    private fun loadTopSelling() {
        _topSelling.value = UiState.Loading
        viewModelScope.launch {
            repository.getTopSellingProducts()
                .onSuccess { _topSelling.value = UiState.Success(it) }
                .onFailure { _topSelling.value = UiState.Error(it.message ?: "خطأ") }
        }
    }

    private fun loadChineseFactory() {
        _chineseFactory.value = UiState.Loading
        viewModelScope.launch {
            repository.getChineseFactoryProducts()
                .onSuccess { _chineseFactory.value = UiState.Success(it) }
                .onFailure { _chineseFactory.value = UiState.Error(it.message ?: "خطأ") }
        }
    }

    private fun loadHotNew() {
        _hotNew.value = UiState.Loading
        viewModelScope.launch {
            repository.getHotNewProducts()
                .onSuccess { _hotNew.value = UiState.Success(it) }
                .onFailure { _hotNew.value = UiState.Error(it.message ?: "خطأ") }
        }
    }
}
