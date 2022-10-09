package com.github.premnirmal.ticker.network

import com.github.premnirmal.ticker.model.FetchException
import com.github.premnirmal.ticker.model.FetchResult
import com.github.premnirmal.ticker.network.data.NewsArticle
import com.github.premnirmal.ticker.network.data.Quote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NewsProvider @Inject constructor(
  private val coroutineScope: CoroutineScope,
  private val googleNewsApi: GoogleNewsApi,
  private val yahooNewsApi: YahooFinanceNewsApi,
  private val apeWisdom: ApeWisdom,
  private val stocksApi: StocksApi
) {

  private var cachedBusinessArticles: List<NewsArticle> = emptyList()
  private var cachedTrendingStocks: List<Quote> = emptyList()

  fun initCache() {
    coroutineScope.launch {
      fetchMarketNews()
      fetchTrendingStocks()
    }
  }

  suspend fun fetchNewsForQuery(query: String): FetchResult<List<NewsArticle>> =
    withContext(Dispatchers.IO) {
      try {
        val newsFeed = googleNewsApi.getNewsFeed(query = query)
        val articles = newsFeed.articleList?.sorted() ?: emptyList()
        return@withContext FetchResult.success(articles)
      } catch (ex: Exception) {
        Timber.w(ex)
        return@withContext FetchResult.failure<List<NewsArticle>>(
            FetchException("Error fetching news", ex))
      }
    }

  suspend fun fetchMarketNews(useCache: Boolean = false): FetchResult<List<NewsArticle>> =
    withContext(Dispatchers.IO) {
      try {
        if (useCache && cachedBusinessArticles.isNotEmpty()) {
          return@withContext FetchResult.success(cachedBusinessArticles)
        }
        val marketNewsArticles = yahooNewsApi.getNewsFeed().articleList.orEmpty()
        val businessNewsArticles = googleNewsApi.getBusinessNews().articleList.orEmpty()
        val articles: Set<NewsArticle> = HashSet<NewsArticle>().apply {
          addAll(marketNewsArticles)
          addAll(businessNewsArticles)
        }
        val newsArticleList = articles.toList().sorted()
        cachedBusinessArticles = newsArticleList
        return@withContext FetchResult.success(newsArticleList)
      } catch (ex: Exception) {
        Timber.w(ex)
        return@withContext FetchResult.failure<List<NewsArticle>>(
            FetchException("Error fetching news", ex))
      }
    }

  suspend fun fetchTrendingStocks(useCache: Boolean = false): FetchResult<List<Quote>> =
    withContext(Dispatchers.IO) {
      try {
        if (useCache && cachedTrendingStocks.isNotEmpty()) {
          return@withContext FetchResult.success(cachedTrendingStocks)
        }
        val result = apeWisdom.getTrendingStocks().results
        val data = result.map { it.ticker }
        val trendingResult = stocksApi.getStocks(data)
        if (trendingResult.wasSuccessful) {
          cachedTrendingStocks = trendingResult.data
        }
        return@withContext trendingResult
      } catch (ex: Exception) {
        Timber.w(ex)
        return@withContext FetchResult.failure<List<Quote>>(
            FetchException("Error fetching trending", ex))
      }
  }
}