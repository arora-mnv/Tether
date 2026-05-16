package com.anantva.tether.data.local.dao

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.anantva.tether.data.local.entity.TransactionEntity

class TransactionPagingSource(
    private val dao: TransactionDao
) : PagingSource<Int, TransactionEntity>() {

    override fun getRefreshKey(state: PagingState<Int, TransactionEntity>): Int? =
        state.anchorPosition?.let { position ->
            state.closestPageToPosition(position)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(position)?.nextKey?.minus(1)
        }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, TransactionEntity> {
        return try {
            val all = dao.getAllConfirmedTransactions()
            val pageSize = params.loadSize
            val key = params.key ?: 0
            val startIndex = key * pageSize
            val endIndex = (startIndex + pageSize).coerceAtMost(all.size)
            val page = if (startIndex < all.size) all.subList(startIndex, endIndex) else emptyList()

            LoadResult.Page(
                data = page,
                prevKey = if (key > 0) key - 1 else null,
                nextKey = if (endIndex < all.size) key + 1 else null
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}
