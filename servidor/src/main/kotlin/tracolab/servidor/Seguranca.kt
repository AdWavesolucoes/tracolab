package tracolab.servidor

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private val aleatorio = SecureRandom()

fun bytesAleatorios(n: Int): ByteArray = ByteArray(n).also { aleatorio.nextBytes(it) }

fun sha256Hex(texto: String): String = MessageDigest.getInstance("SHA-256").digest(texto.toByteArray()).joinToString("") { "%02x".format(it) }

/** Token opaco para cookie de sessão e convites: 32 bytes em base64url. */
fun novoToken(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytesAleatorios(32))

private const val ALFABETO32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

/** Base32 (RFC 4648) sem preenchimento: é o que os apps autenticadores leem. */
fun base32(bytes: ByteArray): String {
    val sb = StringBuilder()
    var buffer = 0; var bits = 0
    for (b in bytes) {
        buffer = (buffer shl 8) or (b.toInt() and 0xff); bits += 8
        while (bits >= 5) { sb.append(ALFABETO32[(buffer shr (bits - 5)) and 31]); bits -= 5 }
    }
    if (bits > 0) sb.append(ALFABETO32[(buffer shl (5 - bits)) and 31])
    return sb.toString()
}

fun deBase32(texto: String): ByteArray {
    val limpo = texto.trim().uppercase().replace("=", "").replace(" ", "")
    val saida = java.io.ByteArrayOutputStream()
    var buffer = 0; var bits = 0
    for (c in limpo) {
        val v = ALFABETO32.indexOf(c); if (v < 0) throw IllegalArgumentException("base32 inválido")
        buffer = (buffer shl 5) or v; bits += 5
        if (bits >= 8) { saida.write((buffer shr (bits - 8)) and 0xff); bits -= 8 }
    }
    return saida.toByteArray()
}

/** Código curto legível (convites, códigos de recuperação): minúsculas de base32 sem letras ambíguas. */
fun codigoLegivel(tamanho: Int): String {
    val alfabeto = "abcdefghjkmnpqrstuvwxyz23456789"   // sem i, l, o, 0, 1
    return String(CharArray(tamanho) { alfabeto[aleatorio.nextInt(alfabeto.length)] })
}

// ------------------------------------------------------------------ senhas (Argon2id)

/**
 * Argon2id com os parâmetros da OWASP para máquinas pequenas (19 MiB, 2 passagens): a instância
 * pode rodar numa placa ARM com pouca memória. Os parâmetros ficam gravados junto do hash, para
 * poderem subir no futuro sem invalidar as senhas antigas.
 *
 * Formato: `argon2id$19$m=19456,t=2,p=1$<sal base64>$<hash base64>`
 */
object Senhas {
    private const val MEMORIA_KB = 19456
    private const val PASSAGENS = 2
    private const val PARALELISMO = 1
    private const val TAMANHO = 32
    private val b64 = Base64.getEncoder().withoutPadding()
    private val deB64 = Base64.getDecoder()

    /** Hash de uma senha que não existe, para o tempo de resposta a um usuário inexistente ser o mesmo. */
    val FANTASMA: String by lazy { gerar("senha-fantasma-" + novoToken()) }

    fun gerar(senha: String): String {
        val sal = bytesAleatorios(16)
        val hash = derivar(senha, sal, MEMORIA_KB, PASSAGENS, PARALELISMO)
        return "argon2id\$19\$m=$MEMORIA_KB,t=$PASSAGENS,p=$PARALELISMO\$${b64.encodeToString(sal)}\$${b64.encodeToString(hash)}"
    }

    fun confere(senha: String, guardado: String): Boolean {
        val partes = guardado.split('$')
        if (partes.size != 5 || partes[0] != "argon2id") return false
        val p = partes[2].split(',').associate { it.substringBefore('=') to it.substringAfter('=').toInt() }
        val sal = deB64.decode(partes[3]); val esperado = deB64.decode(partes[4])
        val hash = derivar(senha, sal, p.getValue("m"), p.getValue("t"), p.getValue("p"), esperado.size)
        return MessageDigest.isEqual(hash, esperado)
    }

    private fun derivar(senha: String, sal: ByteArray, memoriaKb: Int, passagens: Int, paralelismo: Int, tamanho: Int = TAMANHO): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withMemoryAsKB(memoriaKb).withIterations(passagens).withParallelism(paralelismo).withSalt(sal).build()
        val gen = Argon2BytesGenerator().apply { init(params) }
        return ByteArray(tamanho).also { gen.generateBytes(senha.toByteArray(Charsets.UTF_8), it) }
    }
}

// ------------------------------------------------------------------ TOTP (RFC 6238)

/** Senha de uso único por tempo: SHA-1, 30 s, 6 dígitos — o que todo app autenticador aceita. */
object Totp {
    private const val PASSO_S = 30L
    private const val DIGITOS = 6

    fun novoSegredo(): String = base32(bytesAleatorios(20))

    fun codigo(segredo: String, passo: Long): String {
        val mac = Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec(deBase32(segredo), "HmacSHA1")) }
        val h = mac.doFinal(java.nio.ByteBuffer.allocate(8).putLong(passo).array())
        val o = h[h.size - 1].toInt() and 0x0f
        val bin = ((h[o].toInt() and 0x7f) shl 24) or ((h[o + 1].toInt() and 0xff) shl 16) or ((h[o + 2].toInt() and 0xff) shl 8) or (h[o + 3].toInt() and 0xff)
        return "%0${DIGITOS}d".format(bin % 1_000_000)
    }

    /** Aceita o passo atual e os vizinhos (relógio do celular um pouco fora). */
    fun confere(segredo: String, codigo: String, agoraMs: Long = agora()): Boolean {
        val digitado = codigo.filter { it.isDigit() }
        if (digitado.length != DIGITOS) return false
        val passo = agoraMs / 1000 / PASSO_S
        return (-1L..1L).any { MessageDigest.isEqual(codigo(segredo, passo + it).toByteArray(), digitado.toByteArray()) }
    }

    fun otpauth(usuario: String, segredo: String, emissor: String = "TraçoLab"): String {
        val e = URLEncoder.encode(emissor, "UTF-8").replace("+", "%20")
        val u = URLEncoder.encode(usuario, "UTF-8").replace("+", "%20")
        return "otpauth://totp/$e:$u?secret=$segredo&issuer=$e&algorithm=SHA1&digits=$DIGITOS&period=$PASSO_S"
    }

    /** QR em SVG (um retângulo por módulo), para a tela sem imagem externa nem script inline. */
    fun qrSvg(texto: String): String {
        val matriz = QRCodeWriter().encode(texto, BarcodeFormat.QR_CODE, 0, 0,
            mapOf(EncodeHintType.MARGIN to 2, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.CHARACTER_SET to "UTF-8"))
        val n = matriz.width
        val caminho = StringBuilder()
        for (y in 0 until matriz.height) for (x in 0 until n) if (matriz.get(x, y)) caminho.append("M").append(x).append(' ').append(y).append("h1v1h-1z")
        return """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $n ${matriz.height}" shape-rendering="crispEdges" role="img" aria-label="Código QR">""" +
            """<rect width="$n" height="${matriz.height}" fill="#fff"/><path d="$caminho" fill="#0E1726"/></svg>"""
    }
}
