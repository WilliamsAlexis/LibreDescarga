package com.libredescarga.core.domain.usecase

import com.libredescarga.core.model.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageDownloadsTest {

    @Test
    fun `reconoce enlaces directos a imagenes por su extension`() {
        assertTrue(ImageDownloads.isImageUrl("https://cdn.example.com/a/foto.JPG"))
        assertTrue(ImageDownloads.isImageUrl("https://cdn.example.com/foto.webp?w=800#x"))
        assertTrue(ImageDownloads.isImageUrl("http://cdn.example.com/foto.png"))
    }

    @Test
    fun `una pagina o un video no se toman por imagen`() {
        assertFalse(ImageDownloads.isImageUrl("https://example.com/watch?v=1"))
        assertFalse(ImageDownloads.isImageUrl("https://example.com/video.mp4"))
        assertFalse(ImageDownloads.isImageUrl("https://example.com/foto.jpg/pagina"))
        assertFalse(ImageDownloads.isImageUrl("javascript:alert(1).png"))
        assertFalse(ImageDownloads.isImageUrl("file:///sdcard/foto.png"))
    }

    @Test
    fun `la solicitud usa el nombre del archivo y la propia imagen como miniatura`() {
        val request = ImageDownloads.request("http://cdn.example.com/fotos/gato%20negro.png?x=1")!!
        assertEquals(MediaKind.IMAGE, request.kind)
        assertEquals("https://cdn.example.com/fotos/gato%20negro.png?x=1", request.url)
        assertEquals(request.url, request.thumbnailUrl)
        assertEquals("cdn.example.com", request.uploader)
        assertEquals("gato 20negro", request.title)
        assertNull(request.video)
        assertNull(request.audioFormat)
    }

    @Test
    fun `sin nombre util usa el titulo de la pagina o imagen`() {
        val sinTitulo = ImageDownloads.request("https://cdn.example.com/")!!
        assertEquals("imagen", sinTitulo.fileName)
        val conTitulo = ImageDownloads.request("https://cdn.example.com/", "Mi página")!!
        assertEquals("Mi página", conTitulo.title)
    }

    @Test
    fun `en Pinterest primero se prueba la version original y luego la recibida`() {
        assertEquals(
            listOf("https://i.pinimg.com/originals/ab/cd/foto.jpg", "https://i.pinimg.com/736x/ab/cd/foto.jpg"),
            ImageDownloads.candidates("https://i.pinimg.com/736x/ab/cd/foto.jpg"),
        )
        assertEquals(
            listOf("https://i.pinimg.com/originals/ab/foto.png", "https://i.pinimg.com/236x/ab/foto.png"),
            ImageDownloads.candidates("https://i.pinimg.com/236x/ab/foto.png"),
        )
        val yaOriginal = "https://i.pinimg.com/originals/ab/foto.png"
        assertEquals(listOf(yaOriginal), ImageDownloads.candidates(yaOriginal))
        val otraWeb = "https://cdn.example.com/736x/foto.jpg"
        assertEquals(listOf(otraWeb), ImageDownloads.candidates(otraWeb))
    }

    @Test
    fun `encuentra la imagen principal que declara una pagina`() {
        val html = """
            <html><head>
            <meta charset="utf-8">
            <meta property="og:title" content="Un pin">
            <meta property="og:image" content="https://i.pinimg.com/736x/ab/cd/foto.jpg?a=1&amp;b=2" />
            <meta name="twitter:image" content="https://otro.example.com/x.jpg">
            </head></html>
        """.trimIndent()
        assertEquals(
            "https://i.pinimg.com/736x/ab/cd/foto.jpg?a=1&b=2",
            ImageDownloads.mainImageFromHtml(html, "https://www.pinterest.com/pin/1/"),
        )
    }

    @Test
    fun `acepta el orden inverso de atributos, rutas relativas y twitter como respaldo`() {
        val inverso = """<meta content="https://cdn.example.com/a.jpg" property="og:image">"""
        assertEquals("https://cdn.example.com/a.jpg", ImageDownloads.mainImageFromHtml(inverso, "https://example.com/p"))

        val relativa = """<META NAME="twitter:image" CONTENT="/img/b.png">"""
        assertEquals("https://example.com/img/b.png", ImageDownloads.mainImageFromHtml(relativa, "https://example.com/p"))

        val sinProtocolo = """<meta property="og:image" content="//cdn.example.com/c.png">"""
        assertEquals("https://cdn.example.com/c.png", ImageDownloads.mainImageFromHtml(sinProtocolo, "https://example.com/p"))
    }

    @Test
    fun `una pagina sin imagen declarada o con una direccion peligrosa devuelve null`() {
        assertNull(ImageDownloads.mainImageFromHtml("<html><head><title>x</title></head></html>", "https://example.com/p"))
        val peligrosa = """<meta property="og:image" content="javascript:alert(1)">"""
        assertNull(ImageDownloads.mainImageFromHtml(peligrosa, "https://example.com/p"))
        val data = """<meta property="og:image" content="data:image/png;base64,AAAA">"""
        assertNull(ImageDownloads.mainImageFromHtml(data, "https://example.com/p"))
    }

    @Test
    fun `una direccion no valida no genera solicitud`() {
        assertNull(ImageDownloads.request("data:image/png;base64,AAAA"))
        assertNull(ImageDownloads.request("file:///sdcard/a.jpg"))
        assertNull(ImageDownloads.request("no es un enlace"))
    }
}
