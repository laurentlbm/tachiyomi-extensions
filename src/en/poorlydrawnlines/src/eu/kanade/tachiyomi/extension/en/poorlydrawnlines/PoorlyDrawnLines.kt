package eu.kanade.tachiyomi.extension.en.poorlydrawnlines

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList

class PoorlyDrawnLines : HttpSource() {

    override val name = "Poorly Drawn Lines"

    override val baseUrl = "https://poorlydrawnlines.com"

    override val lang = "en"

    override val supportsLatest = false
    override val client: OkHttpClient = network.client.newBuilder()
        .rateLimit(1, 2, TimeUnit.SECONDS)
        .build()

    private val json: Json by injectLazy()

    private val apiHeaders: Headers by lazy { apiHeadersBuilder().build() }

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", USER_AGENT)
        .add("Origin", baseUrl)
        .add("Referer", baseUrl)

    private fun apiHeadersBuilder(): Headers.Builder = headersBuilder()
        .add("Accept", ACCEPT_JSON)

    private fun createManga(): SManga {
        return SManga.create().apply {
            title = "Poorly Drawn Lines"
            url = "/"
            author = "Reza Farazmand"
            artist = author
            description = "Comics by Reza Farazmand"
            thumbnail_url = "https://pbs.twimg.com/profile_images/1455194115767816196/E2x6n-hZ_200x200.jpg" // TODO
        }
    }

    // Popular

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return Observable.just(MangasPage(listOf(createManga()), false))
    }

    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException("Not used")

    override fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException("Not used")

    // Latest

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException("Not used")

    // Search

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = Observable.just(MangasPage(emptyList(), false))

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException("Not used")

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException("Not used")

    // Details

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return Observable.just(createManga().apply { initialized = true })
    }

    override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException("Not used")

    // Chapters

    private fun chapterListApiRequest(page: Int): Request {
        val apiUrl = "$baseUrl/$API_BASE_PATH/posts".toHttpUrl().newBuilder()
            .addQueryParameter("categories", CHAPTER_POST_CATEGORY.toString())
            .addQueryParameter("per_page", CHAPTERS_PER_PAGE.toString())
            .addQueryParameter("page", page.toString())
            .addQueryParameter("_fields", "date_gmt,title,link")
            .toString()

        return GET(apiUrl, apiHeaders)
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        var page = 1
        var hasNextPage = true
        val chapterListResults = ArrayList<SChapter>()

        while (hasNextPage) {
            val request = chapterListApiRequest(page)
            val response = client.newCall(request).execute()
            val chapterListPage = chapterListParse(response)
            chapterListResults.addAll(chapterListPage)

            val lastPage = response.headers["X-Wp-TotalPages"]!!.toInt()
            hasNextPage = page < lastPage
            page += 1
        }

        return Observable.just(chapterListResults)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.parseAs<List<PoorlyDrawnLinesChapterDto>>()
        return result.map { chapterFromObject(it) }
    }

    private fun chapterFromObject(obj: PoorlyDrawnLinesChapterDto): SChapter = SChapter.create().apply {
        name = obj.title.rendered
        date_upload = obj.date.toDate()
        setUrlWithoutDomain(obj.link)
    }

    // Pages

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        return Observable.just(listOf(Page(0, baseUrl + chapter.url)))
    }

    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException("Not used")

    override fun imageUrlParse(response: Response): String {
        return response.asJsoup().select("div.content.comic div.post img").attr("abs:src")
    }

    private inline fun <reified T> Response.parseAs(): T = use {
        json.decodeFromString(it.body?.string().orEmpty())
    }

    private fun String.toDate(): Long {
        return runCatching { DATE_FORMATTER.parse(this)?.time }
            .getOrNull() ?: 0L
    }

    companion object {
        private const val ACCEPT_JSON = "application/json"
        private val USER_AGENT = "Tachiyomi " + System.getProperty("http.agent")

        private const val API_BASE_PATH = "wp-json/wp/v2"
        private const val CHAPTER_POST_CATEGORY = 3
        private const val CHAPTERS_PER_PAGE = 100

        private val DATE_FORMATTER by lazy {
            SimpleDateFormat("yyyy-MM-ddTHH:mm:ss", Locale.ENGLISH)
        }
    }
}
