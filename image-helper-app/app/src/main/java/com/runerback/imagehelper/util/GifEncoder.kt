package com.runerback.imagehelper.util

import android.graphics.Bitmap
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Minimal animated GIF encoder (GIF89a) writing to an [OutputStream].
 * Port of the classic Java AnimatedGifEncoder (Kevin Weiner, FM Software)
 * with NeuQuant color quantization and LZW compression.
 *
 * Usage: start(out) -> setRepeat/setDelay/addFrame... -> finish().
 */
class GifEncoder(private val width: Int, private val height: Int) {

    /** loop count, 0 means forever (default) */
    private var repeat: Int = 0

    /** frame delay in hundredths of a second */
    private var delay: Int = 10

    private var out: OutputStream? = null
    private var started = false
    private var firstFrame = true

    private var indexedPixels = ByteArray(0)
    private var colorTab = ByteArray(0)
    private var colorDepth = 8
    private var palSize = 7

    /** palette index used for transparent pixels; only valid when [hasTransparent] */
    private var transIndex = 0
    private var hasTransparent = false

    fun setRepeat(repeat: Int) {
        if (repeat >= 0) this.repeat = repeat
    }

    fun setDelay(ms: Int) {
        delay = (ms / 10f).roundToInt().coerceAtLeast(1)
    }

    fun start(out: OutputStream): Boolean {
        this.out = out
        return try {
            writeString("GIF89a")
            started = true
            true
        } catch (e: Exception) {
            LogManager.e(TAG, "start failed", e)
            false
        }
    }

    /**
     * Adds a frame. When [transparentMask] is given (width*height entries),
     * pixels marked true are written as transparent, letting the previous
     * frame show through (disposal: do-not-dispose). The palette is then
     * quantized from the changed pixels only.
     */
    fun addFrame(bitmap: Bitmap, transparentMask: BooleanArray? = null): Boolean {
        val stream = out ?: return false
        if (!started || bitmap.width != width || bitmap.height != height) {
            LogManager.w(TAG, "addFrame: bad state or size ${bitmap.width}x${bitmap.height}, expected ${width}x$height")
            return false
        }
        if (transparentMask != null && transparentMask.size != width * height) {
            LogManager.w(TAG, "addFrame: mask size ${transparentMask.size}, expected ${width * height}")
            return false
        }
        return try {
            analyzePixels(bitmap, transparentMask)
            if (firstFrame) {
                writeLSD()
                if (repeat >= 0) writeNetscapeExt()
            }
            writeGraphicCtrlExt()
            writeImageDesc()
            if (!firstFrame) writePalette()
            writePixels()
            firstFrame = false
            stream.flush()
            true
        } catch (e: Exception) {
            LogManager.e(TAG, "addFrame failed", e)
            false
        }
    }

    fun finish(): Boolean {
        val stream = out ?: return false
        if (!started) return false
        return try {
            stream.write(0x3B)
            stream.flush()
            started = false
            true
        } catch (e: Exception) {
            LogManager.e(TAG, "finish failed", e)
            false
        }
    }

    private fun analyzePixels(bitmap: Bitmap, mask: BooleanArray?) {
        val count = width * height
        val argb = IntArray(count)
        bitmap.getPixels(argb, 0, width, 0, 0, width, height)

        // quantize only the changed pixels when a diff mask is provided
        val quantPixels: ByteArray = if (mask == null) {
            ByteArray(count * 3).also { px ->
                for (i in 0 until count) px.fillFrom(argb[i], i)
            }
        } else {
            var changed = 0
            for (i in 0 until count) if (!mask[i]) changed++
            ByteArray(max(changed, 1) * 3).also { px ->
                var p = 0
                for (i in 0 until count) {
                    if (mask[i]) continue
                    px.fillFrom(argb[i], p++)
                }
            }
        }

        val nq = NeuQuant(quantPixels, quantPixels.size, 10)
        colorTab = nq.process()

        val usedEntry = BooleanArray(256)
        indexedPixels = ByteArray(count)
        for (i in 0 until count) {
            if (mask != null && mask[i]) continue // placeholder, resolved below
            val index = nq.map(argb.red(i), argb.green(i), argb.blue(i))
            usedEntry[index] = true
            indexedPixels[i] = index.toByte()
        }

        hasTransparent = false
        if (mask != null) {
            // an unused palette entry becomes the transparent index, so real
            // pixels can never collide with it; fall back to opaque if none
            val free = (0 until 256).firstOrNull { !usedEntry[it] }
            if (free != null) {
                transIndex = free
                hasTransparent = true
                for (i in 0 until count) if (mask[i]) indexedPixels[i] = free.toByte()
            } else {
                LogManager.d(TAG, "palette exhausted, encoding frame without transparency")
                for (i in 0 until count) {
                    if (!mask[i]) continue
                    val index = nq.map(argb.red(i), argb.green(i), argb.blue(i))
                    indexedPixels[i] = index.toByte()
                }
            }
        }

        colorDepth = 8
        palSize = 7
    }

    private fun ByteArray.fillFrom(color: Int, i: Int) {
        this[i * 3] = (color and 0xFF).toByte()
        this[i * 3 + 1] = (color shr 8 and 0xFF).toByte()
        this[i * 3 + 2] = (color shr 16 and 0xFF).toByte()
    }

    private fun IntArray.red(i: Int) = this[i] and 0xFF
    private fun IntArray.green(i: Int) = this[i] shr 8 and 0xFF
    private fun IntArray.blue(i: Int) = this[i] shr 16 and 0xFF

    // logical screen descriptor
    private fun writeLSD() {
        writeShort(width)
        writeShort(height)
        out?.write(0xF0 or palSize)
        out?.write(0)
        out?.write(0)
        writePalette()
    }

    private fun writeNetscapeExt() {
        out?.write(0x21)
        out?.write(0xFF)
        out?.write(11)
        writeString("NETSCAPE2.0")
        out?.write(3)
        out?.write(1)
        writeShort(repeat)
        out?.write(0)
    }

    private fun writeGraphicCtrlExt() {
        out?.write(0x21)
        out?.write(0xF9)
        out?.write(4)
        if (hasTransparent) {
            out?.write(0x05) // disposal 1 (keep previous frame) + transparency flag
            writeShort(delay)
            out?.write(transIndex)
        } else {
            out?.write(0)
            writeShort(delay)
            out?.write(0)
        }
        out?.write(0)
    }

    private fun writeImageDesc() {
        out?.write(0x2C)
        writeShort(0)
        writeShort(0)
        writeShort(width)
        writeShort(height)
        out?.write(if (firstFrame) 0 else 0x80 or palSize)
    }

    private fun writePalette() {
        out?.write(colorTab, 0, colorTab.size)
    }

    private fun writePixels() {
        LZWEncoder(width, height, indexedPixels, colorDepth).encode(out!!)
    }

    private fun writeShort(value: Int) {
        out?.write(value and 0xFF)
        out?.write(value shr 8 and 0xFF)
    }

    private fun writeString(s: String) {
        for (element in s) out?.write(element.code)
    }

    companion object {
        private const val TAG = "GifEncoder"
    }
}

/**
 * NeuQuant neural-net color quantizer (Anthony Dekker).
 * Produces a 256-color palette and nearest-color index lookups.
 */
private class NeuQuant(
    private val thepic: ByteArray,
    private val lengthcount: Int,
    sample: Int
) {
    private var samplefac = sample
    private val network = Array(NETSIZE) { IntArray(4) }
    private val netindex = IntArray(256)
    private val bias = IntArray(NETSIZE)
    private val freq = IntArray(NETSIZE)
    private val radpower = IntArray(INITRAD)

    init {
        for (i in 0 until NETSIZE) {
            network[i][0] = (i shl (NETBIASSHIFT + 8)) / NETSIZE
            network[i][1] = network[i][0]
            network[i][2] = network[i][0]
            freq[i] = INTBIAS / NETSIZE
            bias[i] = 0
        }
    }

    fun process(): ByteArray {
        learn()
        unbiasnet()
        inxbuild()
        return colormap()
    }

    fun map(b: Int, g: Int, r: Int): Int {
        var bestd = 1000
        var best = -1
        var i = netindex[g]
        var j = i - 1
        while (i < NETSIZE || j >= 0) {
            if (i < NETSIZE) {
                val p = network[i]
                var dist = p[1] - g
                if (dist >= bestd) {
                    i = NETSIZE
                } else {
                    i++
                    if (dist < 0) dist = -dist
                    var a = p[0] - b
                    if (a < 0) a = -a
                    dist += a
                    if (dist < bestd) {
                        a = p[2] - r
                        if (a < 0) a = -a
                        dist += a
                        if (dist < bestd) {
                            bestd = dist
                            best = p[3]
                        }
                    }
                }
            }
            if (j >= 0) {
                val p = network[j]
                var dist = g - p[1]
                if (dist >= bestd) {
                    j = -1
                } else {
                    j--
                    if (dist < 0) dist = -dist
                    var a = p[0] - b
                    if (a < 0) a = -a
                    dist += a
                    if (dist < bestd) {
                        a = p[2] - r
                        if (a < 0) a = -a
                        dist += a
                        if (dist < bestd) {
                            bestd = dist
                            best = p[3]
                        }
                    }
                }
            }
        }
        return best
    }

    private fun colormap(): ByteArray {
        val map = ByteArray(3 * NETSIZE)
        val index = IntArray(NETSIZE)
        for (i in 0 until NETSIZE) index[network[i][3]] = i
        var k = 0
        for (l in 0 until NETSIZE) {
            val j = index[l]
            map[k++] = network[j][0].toByte()
            map[k++] = network[j][1].toByte()
            map[k++] = network[j][2].toByte()
        }
        return map
    }

    private fun inxbuild() {
        var previouscol = 0
        var startpos = 0
        for (i in 0 until NETSIZE) {
            val p = network[i]
            var smallpos = i
            var smallval = p[1]
            for (j in i + 1 until NETSIZE) {
                val q = network[j]
                if (q[1] < smallval) {
                    smallpos = j
                    smallval = q[1]
                }
            }
            val q = network[smallpos]
            if (i != smallpos) {
                var t = q[0]; q[0] = p[0]; p[0] = t
                t = q[1]; q[1] = p[1]; p[1] = t
                t = q[2]; q[2] = p[2]; p[2] = t
                t = q[3]; q[3] = p[3]; p[3] = t
            }
            if (smallval != previouscol) {
                netindex[previouscol] = (startpos + i) shr 1
                for (j in previouscol + 1 until smallval) netindex[j] = i
                previouscol = smallval
                startpos = i
            }
        }
        netindex[previouscol] = (startpos + MAXNETPOS) shr 1
        for (j in previouscol + 1 until 256) netindex[j] = MAXNETPOS
    }

    private fun learn() {
        if (lengthcount < MINPICTUREBYTES) samplefac = 1
        val alphadec = 30 + (samplefac - 1) / 3
        val p = thepic
        var pix = 0
        val lim = lengthcount
        val samplepixels = lengthcount / (3 * samplefac)
        var delta = samplepixels / NCYCLES
        var alpha = INITALPHA
        var radius = INITRADIUS

        var rad = radius shr RADIUSBIASSHIFT
        if (rad <= 1) rad = 0
        for (i in 0 until rad) {
            radpower[i] = alpha * ((rad * rad - i * i) * RADBIAS / (rad * rad))
        }

        val step = when {
            lengthcount < MINPICTUREBYTES -> 3
            lengthcount % PRIME1 != 0 -> 3 * PRIME1
            lengthcount % PRIME2 != 0 -> 3 * PRIME2
            lengthcount % PRIME3 != 0 -> 3 * PRIME3
            else -> 3 * PRIME4
        }

        var i = 0
        while (i < samplepixels) {
            val b = (p[pix].toInt() and 0xFF) shl NETBIASSHIFT
            val g = (p[pix + 1].toInt() and 0xFF) shl NETBIASSHIFT
            val r = (p[pix + 2].toInt() and 0xFF) shl NETBIASSHIFT
            val j = contest(b, g, r)

            altersingle(alpha, j, b, g, r)
            if (rad != 0) alterneigh(rad, j, b, g, r)

            pix += step
            if (pix >= lim) pix -= lengthcount

            i++
            if (delta == 0) delta = 1
            if (i % delta == 0) {
                alpha -= alpha / alphadec
                radius -= radius / RADIUSDEC
                rad = radius shr RADIUSBIASSHIFT
                if (rad <= 1) rad = 0
                for (k in 0 until rad) {
                    radpower[k] = alpha * ((rad * rad - k * k) * RADBIAS / (rad * rad))
                }
            }
        }
    }

    private fun unbiasnet() {
        for (i in 0 until NETSIZE) {
            network[i][0] = network[i][0] shr NETBIASSHIFT
            network[i][1] = network[i][1] shr NETBIASSHIFT
            network[i][2] = network[i][2] shr NETBIASSHIFT
            network[i][3] = i
        }
    }

    private fun alterneigh(rad: Int, j: Int, b: Int, g: Int, r: Int) {
        val lo = (j - rad).coerceAtLeast(0)
        val hi = (j + rad).coerceAtMost(NETSIZE - 1)

        var i = j + 1
        var k = j - 1
        var m = 1
        while (i < hi || k > lo) {
            val a = radpower[m++]
            if (i < hi) {
                val p = network[i++]
                p[0] -= a * (p[0] - b) / ALPHARADBIAS
                p[1] -= a * (p[1] - g) / ALPHARADBIAS
                p[2] -= a * (p[2] - r) / ALPHARADBIAS
            }
            if (k > lo) {
                val p = network[k--]
                p[0] -= a * (p[0] - b) / ALPHARADBIAS
                p[1] -= a * (p[1] - g) / ALPHARADBIAS
                p[2] -= a * (p[2] - r) / ALPHARADBIAS
            }
        }
    }

    private fun altersingle(alpha: Int, j: Int, b: Int, g: Int, r: Int) {
        val n = network[j]
        n[0] -= alpha * (n[0] - b) / INITALPHA
        n[1] -= alpha * (n[1] - g) / INITALPHA
        n[2] -= alpha * (n[2] - r) / INITALPHA
    }

    private fun contest(b: Int, g: Int, r: Int): Int {
        var bestd = Int.MAX_VALUE
        var bestbiasd = bestd
        var bestpos = -1
        var bestbiaspos = bestpos
        for (i in 0 until NETSIZE) {
            val n = network[i]
            var dist = n[0] - b
            if (dist < 0) dist = -dist
            var a = n[1] - g
            if (a < 0) a = -a
            dist += a
            a = n[2] - r
            if (a < 0) a = -a
            dist += a
            if (dist < bestd) {
                bestd = dist
                bestpos = i
            }
            val biasdist = dist - (bias[i] shr (INTBIASSHIFT - NETBIASSHIFT))
            if (biasdist < bestbiasd) {
                bestbiasd = biasdist
                bestbiaspos = i
            }
            val betafreq = freq[i] shr BETASHIFT
            freq[i] -= betafreq
            bias[i] += betafreq shl GAMMASHIFT
        }
        freq[bestpos] += BETA
        bias[bestpos] -= BETAGAMMA
        return bestbiaspos
    }

    companion object {
        private const val NETSIZE = 256
        private const val PRIME1 = 499
        private const val PRIME2 = 491
        private const val PRIME3 = 487
        private const val PRIME4 = 503
        private const val MINPICTUREBYTES = 3 * PRIME4
        private const val MAXNETPOS = NETSIZE - 1
        private const val NETBIASSHIFT = 4
        private const val NCYCLES = 100
        private const val INTBIASSHIFT = 16
        private const val INTBIAS = 1 shl INTBIASSHIFT
        private const val GAMMASHIFT = 10
        private const val BETASHIFT = 10
        private const val BETA = INTBIAS shr BETASHIFT
        private const val BETAGAMMA = INTBIAS shl (GAMMASHIFT - BETASHIFT)
        private const val INITRAD = NETSIZE shr 3
        private const val RADIUSBIASSHIFT = 6
        private const val RADIUSBIAS = 1 shl RADIUSBIASSHIFT
        private const val INITRADIUS = INITRAD * RADIUSBIAS
        private const val RADIUSDEC = 30
        private const val ALPHABIASSHIFT = 10
        private const val INITALPHA = 1 shl ALPHABIASSHIFT
        private const val RADBIASSHIFT = 8
        private const val RADBIAS = 1 shl RADBIASSHIFT
        private const val ALPHARADBSHIFT = ALPHABIASSHIFT + RADBIASSHIFT
        private const val ALPHARADBIAS = 1 shl ALPHARADBSHIFT
    }
}

/** LZW GIF compressor (8-bit indexed pixels). */
private class LZWEncoder(
    private val imgW: Int,
    private val imgH: Int,
    private val pixAry: ByteArray,
    private val colorDepth: Int
) {
    private var remaining = imgW * imgH
    private var curPixel = 0
    private val initCodeSize = max(2, colorDepth)

    private var nBits = 0
    private var maxcode = 0
    private var htab = IntArray(HSIZE)
    private var codetab = IntArray(HSIZE)
    private var freeEnt = 0
    private var clearFlg = false
    private var gInitBits = 0
    private var clearCode = 0
    private var eofCode = 0
    private var curAccum = 0
    private var curBits = 0
    private var aCount = 0
    private val accum = ByteArray(256)

    private fun maxCode(n: Int) = (1 shl n) - 1

    private fun nextPixel(): Int {
        if (remaining == 0) return EOF
        --remaining
        return pixAry[curPixel++].toInt() and 0xFF
    }

    private fun charOut(c: Byte, outs: OutputStream) {
        accum[aCount++] = c
        if (aCount >= 254) flushPacket(outs)
    }

    private fun clBlock(outs: OutputStream) {
        clHash(HSIZE)
        freeEnt = clearCode + 2
        clearFlg = true
        output(clearCode, outs)
    }

    private fun clHash(hsize: Int) {
        for (i in 0 until hsize) htab[i] = -1
    }

    private fun compress(initBits: Int, outs: OutputStream) {
        gInitBits = initBits
        clearFlg = false
        nBits = gInitBits
        maxcode = maxCode(nBits)

        // clear/EOF codes derive from the code size, never collide with pixel values
        clearCode = 1 shl (initBits - 1)
        eofCode = clearCode + 1
        freeEnt = clearCode + 2
        aCount = 0

        var ent = nextPixel()

        var hshift = 0
        var f = HSIZE
        while (f < 65536) {
            ++hshift
            f *= 2
        }
        hshift = 8 - hshift

        clHash(HSIZE)

        output(clearCode, outs)

        outer@ while (true) {
            val c = nextPixel()
            if (c == EOF) break
            val fcode = (c shl MAXBITS) + ent
            var i = (c shl hshift) xor ent

            if (htab[i] == fcode) {
                ent = codetab[i]
                continue
            } else if (htab[i] >= 0) {
                var disp = HSIZE - i
                if (i == 0) disp = 1
                do {
                    i -= disp
                    if (i < 0) i += HSIZE
                    if (htab[i] == fcode) {
                        ent = codetab[i]
                        continue@outer
                    }
                } while (htab[i] >= 0)
            }
            output(ent, outs)
            ent = c
            if (freeEnt < MAXMAXCODE) {
                codetab[i] = freeEnt++
                htab[i] = fcode
            } else {
                clBlock(outs)
            }
        }

        output(ent, outs)
        output(eofCode, outs)
    }

    private fun output(code: Int, outs: OutputStream) {
        curAccum = curAccum and MASKS[curBits]
        curAccum = if (curBits > 0) curAccum or (code shl curBits) else code
        curBits += nBits

        while (curBits >= 8) {
            charOut((curAccum and 0xFF).toByte(), outs)
            curAccum = curAccum shr 8
            curBits -= 8
        }

        if (freeEnt > maxcode || clearFlg) {
            if (clearFlg) {
                nBits = gInitBits
                maxcode = maxCode(nBits)
                clearFlg = false
            } else {
                ++nBits
                maxcode = if (nBits == MAXBITS) MAXMAXCODE else maxCode(nBits)
            }
        }

        if (code == eofCode) {
            while (curBits > 0) {
                charOut((curAccum and 0xFF).toByte(), outs)
                curAccum = curAccum shr 8
                curBits -= 8
            }
            flushPacket(outs)
        }
    }

    private fun flushPacket(outs: OutputStream) {
        if (aCount > 0) {
            outs.write(aCount)
            outs.write(accum, 0, aCount)
            aCount = 0
        }
    }

    fun encode(outs: OutputStream) {
        outs.write(initCodeSize)
        remaining = imgW * imgH
        curPixel = 0
        compress(initCodeSize + 1, outs)
        outs.write(0)
    }

    companion object {
        private const val EOF = -1
        private const val MAXBITS = 12
        private const val HSIZE = 5003
        private const val MAXMAXCODE = 1 shl MAXBITS

        private val MASKS = intArrayOf(
            0x0000, 0x0001, 0x0003, 0x0007, 0x000F, 0x001F, 0x003F, 0x007F,
            0x00FF, 0x01FF, 0x03FF, 0x07FF, 0x0FFF, 0x1FFF, 0x3FFF, 0x7FFF, 0xFFFF
        )
    }
}
