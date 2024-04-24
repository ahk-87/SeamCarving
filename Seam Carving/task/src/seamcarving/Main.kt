package seamcarving

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.sqrt

fun applyNegative(source: BufferedImage) {
    for (x in 0 until source.width) {
        for (y in 0 until source.height) {
            val c = Color(source.getRGB(x, y))
            val negativeColor = Color(255 - c.red, 255 - c.green, 255 - c.blue)
            source.setRGB(x, y, negativeColor.rgb)
        }
    }
}

fun getEnergyTable(source: BufferedImage, width: Int = source.width, height: Int = source.height): Array<DoubleArray> {
    val energies = Array(height) { DoubleArray(width) }
    for (x in 0 until width) {
        for (y in 0 until height) {
            val xx = x.coerceIn(1, width - 2)
            val yy = y.coerceIn(1, height - 2)

            val xColor1 = Color(source.getRGB(xx - 1, y))
            val xColor2 = Color(source.getRGB(xx + 1, y))
            val yColor1 = Color(source.getRGB(x, yy - 1))
            val yColor2 = Color(source.getRGB(x, yy + 1))

            val xGradient = gradient(xColor1, xColor2)
            val yGradient = gradient(yColor1, yColor2)

            energies[y][x] = sqrt(xGradient + yGradient)
        }
    }
    return energies
}

fun gradient(c1: Color, c2: Color): Double {
    val r = c2.red - c1.red
    val g = c2.green - c1.green
    val b = c2.blue - c1.blue
    return (r * r + g * g + b * b).toDouble()
}

fun applyIntensity(source: BufferedImage, energyTable: Array<DoubleArray>, max: Double) {
    for (x in 0 until source.width) {
        for (y in 0 until source.height) {
            val intensity = (255 * energyTable[y][x] / max).toInt()
            val color = Color(intensity, intensity, intensity)
            source.setRGB(x, y, color.rgb)
        }
    }
}

fun findMaxEnergy(energyTable: Array<DoubleArray>): Double {
    return energyTable.maxOf { it.maxOrNull()!! }
}

fun findSeam(energyTable: Array<DoubleArray>): List<Int> {
    val height = energyTable.size
    val width = energyTable[0].size

    // Create a 2D array to store the cumulative energy values and the previous index
    // of the energy that lead to the cumulative calculation path
    val cumulativeEnergy = Array(height) { Array(width) { 0.0 to 0 } }
    cumulativeEnergy[0] = energyTable[0].map { it to 0 }.toTypedArray()

    // Calculate cumulative energy for the remaining rows
    for (j in 1 until height) {
        for (i in 0 until width) {
            // Energy of the current pixel
            val currentEnergy = energyTable[j][i]

            // Find the minimum cumulative energy and its index
            val iLeft = i.coerceAtLeast(1) - 1
            val iRight = i.coerceAtMost(width - 2) + 1

            val left = cumulativeEnergy[j - 1][iLeft].first
            val middle = cumulativeEnergy[j - 1][i].first
            val right = cumulativeEnergy[j - 1][iRight].first

            val minPrevCumulativeEnergy = minOf(left, middle, right)

            val prevIndex = if (left == minPrevCumulativeEnergy) iLeft else
                if (middle == minPrevCumulativeEnergy) i else iRight

            // Update cumulative energy and the previous index for the current pixel
            cumulativeEnergy[j][i] = Pair(minPrevCumulativeEnergy + currentEnergy, prevIndex)
        }
    }

    // Find the seam with the minimum cumulative energy in the last row
    var minEnergyPair = cumulativeEnergy[height - 1].minByOrNull { it.first }!!
    val minEnergyIndex = cumulativeEnergy[height - 1].indexOf(minEnergyPair)
    val seam = mutableListOf(minEnergyIndex)
    for (j in height - 2 downTo 0) {
        seam.add(0, minEnergyPair.second)
        minEnergyPair = cumulativeEnergy[j][minEnergyPair.second]
    }
    return seam
}

fun resize(image: BufferedImage, w: Int, h: Int): BufferedImage {
    var width = image.width
    var height = image.height
    val resizedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    resizedImage.data = image.data

    repeat(w) {
        val energyTable = getEnergyTable(resizedImage, width--, height)
        val seam = findSeam(energyTable)
        for (y in 0 until height) {
            val x = seam[y]
            val copyWidth = width - x
            val arr = resizedImage.getRGB(x + 1, y, copyWidth, 1, null, 0, copyWidth)
            resizedImage.setRGB(x, y, copyWidth, 1, arr, 0, copyWidth)
        }
    }

    repeat(h) {
        val energyTable = getEnergyTable(resizedImage, width, height--)
        val transpose = Array(energyTable[0].size) { y -> DoubleArray(energyTable.size) { x -> energyTable[x][y] } }
        val seam = findSeam(transpose)
        for (x in 0 until width) {
            for (y in seam[x] until height) {
                resizedImage.setRGB(x, y, resizedImage.getRGB(x, y + 1))
            }
        }
    }

    return resizedImage.getSubimage(0, 0, width, height)
}

fun saveImage(image: BufferedImage, fileName: String) {
    val file = File(fileName)
    ImageIO.write(image, "png", file)
}

fun getImage(fileName: String): BufferedImage {
    val file = File(fileName)
    return ImageIO.read(file)
}

fun main(args: Array<String>) {
    val map = args.toList().chunked(2).associate { it[0] to it[1] }
    val image = getImage(map["-in"]!!)
    val seamWidth = map["-width"]!!.toInt()
    val seamHeight = map["-height"]!!.toInt()
    val resized = resize(image, seamWidth, seamHeight)
    saveImage(resized, map["-out"]!!)
}