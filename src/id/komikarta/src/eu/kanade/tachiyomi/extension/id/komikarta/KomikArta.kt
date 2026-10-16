package eu.kanade.tachiyomi.extension.id.komikarta

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

class KomikArta : ParsedHttpSource() {

    override val name = "KomikArta"
    override val baseUrl = "https://komikarta.com"
    override val lang = "id"
    override val supportsLatest = true

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val cardSelector = "a.card"

    private fun cardToManga(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))
        title = element.selectFirst(".t")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("data-full")
    }

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)
    override fun popularMangaSelector() = "div.sec:has(h2:containsOwn(Populer)) $cardSelector"
    override fun popularMangaFromElement(element: Element) = cardToManga(element)
    override fun popularMangaNextPageSelector(): String? = null

    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, headers)
    override fun latestUpdatesSelector() = "div.sec:has(h2:containsOwn(Terbaru)) $cardSelector"
    override fun latestUpdatesFromElement(element: Element) = cardToManga(element)
    override fun latestUpdatesNextPageSelector(): String? = null

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("query", query)
            .build()
        return GET(url, headers)
    }
    override fun searchMangaSelector() = cardSelector
    override fun searchMangaFromElement(element: Element) = cardToManga(element)
    override fun searchMangaNextPageSelector(): String? = null

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst(".info h1")!!.text()
        thumbnail_url = document.selectFirst(".cover img")?.absUrl("data-full")
        genre = document.select(".tags-line a.chip").joinToString { it.text() }

        val kv = document.select(".info .kv b").associate { b ->
            val value = (b.nextSibling() as? TextNode)?.text().orEmpty()
                .replace("·", "").trim()
            b.text().trim() to value
        }

        author = kv["Penulis"]?.takeIf { it.isNotBlank() }
        status = when (kv["Status"]?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed", "tamat", "selesai" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }

        val synopsis = document.selectFirst("p.synopsis")?.text()
        val alt = kv["Judul alternatif"]?.takeIf { it.isNotBlank() }
        description = listOfNotNull(synopsis, alt?.let { "Judul alternatif: $it" })
            .joinToString("\n\n")
    }

    override fun chapterListSelector() = ".reader-chapters i"

    override fun chapterFromElement(element: Element): SChapter =
        throw UnsupportedOperationException("Tidak dipakai")

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val mangaPath = response.request.url.encodedPath

        val markers = document.select(chapterListSelector()).map {
            it.attr("data-name") to it.attr("data-start").toInt()
        }
        val lastIndex = document.select(".reader-urls i[data-full]").size - 1

        return markers.mapIndexed { i, (rawName, start) ->
            val end = if (i + 1 < markers.size) markers[i + 1].second - 1 else lastIndex
            SChapter.create().apply {
                name = rawName.replaceFirstChar { it.uppercase() }
                url = "$mangaPath?r=$start-$end"
                chapter_number = Regex("""\d+(\.\d+)?""").find(rawName)?.value?.toFloatOrNull()
                    ?: (i + 1).toFloat()
            }
        }.reversed()
    }

    override fun pageListParse(document: Document): List<Page> {
        val urls = document.select(".reader-urls i[data-full]").map { it.attr("data-full") }
        val range = document.location().substringAfter("?r=", "").split("-")
        val start = range.getOrNull(0)?.toIntOrNull() ?: 1
        val end = (range.getOrNull(1)?.toIntOrNull() ?: urls.lastIndex).coerceAtMost(urls.lastIndex)

        return urls.slice(start..end).mapIndexed { i, imageUrl ->
            Page(i, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(document: Document): String =
        throw UnsupportedOperationException("Tidak dipakai")
}
