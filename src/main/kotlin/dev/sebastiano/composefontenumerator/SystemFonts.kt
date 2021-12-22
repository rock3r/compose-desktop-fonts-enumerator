package dev.sebastiano.composefontenumerator

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.FileFont
import androidx.compose.ui.text.platform.Font
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import java.awt.GraphicsEnvironment
import java.awt.font.TextAttribute
import java.io.File
import java.util.Locale
import java.util.TreeMap

fun listFontFamilies(): List<SystemFontFamily> {
    val fontFamilyNames = GraphicsEnvironment.getLocalGraphicsEnvironment()
        .getAvailableFontFamilyNames(Locale.ROOT)
        .sorted()

    return when {
        isMacOs() -> listFontFamiliesOnMacOs()
        isWindows() -> listFontFamiliesOnWindows(fontFamilyNames)
        isLinux() -> listFontFamiliesOnLinux()
        else -> error("Unsupported OS")
    }
}

private fun listFontFamiliesOnMacOs(): List<SystemFontFamily> {
    TODO("Not yet implemented")
}

private fun listFontFamiliesOnLinux(): List<SystemFontFamily> {
    TODO("Not yet implemented")
}

private const val FONTS_KEY_PATH = "SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\Fonts"

// Current limitations:
//  * If a font has a different "real" family name (as reported by AWT) from the name it appears with
//    in the registry, that font will not be matched, and thus won't be listed
//  * Font substitutions and "system" fonts (like Monospaced, SansSerif, etc) aren't listed — but the
//    former are available as FontFamily.Monospaced, FontFamily.SansSerif, etc at least
private fun listFontFamiliesOnWindows(fontFamilyNames: Iterable<String>): List<SystemFontFamily> {
    val fontsDir = File("${System.getenv("WINDIR")}\\Fonts")

    @Suppress("UNCHECKED_CAST")
    val registryMap = (Advapi32Util.registryGetValues(WinReg.HKEY_LOCAL_MACHINE, FONTS_KEY_PATH) as TreeMap<String, String>)
        .filterValues { it.endsWith(".ttf", ignoreCase = true) || it.endsWith(".otf", ignoreCase = true) }

    val fontPathByName = mutableMapOf<String, String>()
    for ((name, path) in registryMap.entries) {
        if (!name.contains(" & ")) {
            fontPathByName[name.substringBeforeLast(" (")] = path
        } else {
            // This case handles entries where multiple fonts are provided by a single file.
            // E.g.: Microsoft JhengHei Light & Microsoft JhengHei UI Light -> msjhl.ttc
            val names = name.substringBeforeLast(" (").split(" & ")
            for (subName in names) {
                fontPathByName[subName] = path
            }
        }
    }

    val fontFiles = fontPathByName.mapKeys { (key, _) -> key.substringBeforeLast(" (") }
        .mapValues { (_, path) -> if (path.contains('\\')) File(path) else File(fontsDir, path) }
        .mapKeys { (_, value) -> java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, value) }

    return collectIntoSystemFontFamilies(fontFamilyNames, fontFiles)
}

private fun collectIntoSystemFontFamilies(
    fontFamilyNames: Iterable<String>,
    fontFiles: Map<java.awt.Font, File>
): MutableList<SystemFontFamily> {
    val sortedFontFamilyNames = fontFamilyNames.sortedByDescending { it.length }
    val fontFamilies = mutableListOf<SystemFontFamily>()
    val filesByFont = fontFiles.toMutableMap()

    for (familyName in sortedFontFamilyNames) {
        println("Processing font family: $familyName")
        val files = filesByFont.filterKeys { font -> familyName.equals(font.family, ignoreCase = true) }

        for ((name, _) in files) {
            filesByFont.remove(name)
        }

        if (files.isEmpty()) {
            System.err.println("Font family '$familyName' has no associated font files")
            continue
        }

        val fileFonts = files.map { (font, file) ->
            val fontStyle = if (font.isItalic || looksItalic(font.name)) FontStyle.Italic else FontStyle.Normal
            val rawWeight = fontWeightFromTextAttributeValue(font.attributes[TextAttribute.WEIGHT] as Float?)
            val fontWeight = rawWeight ?: inferWeightFromName(
                font.name.substringAfter(font.family).split(' ', '-')
                    .map { it.trim().lowercase() }
                    .filter { it.isNotBlank() }
            )

            Font(file = file, weight = fontWeight, style = fontStyle) as FileFont
        }

        println("Font files:\n$fileFonts")

        fontFamilies += SystemFontFamily(familyName, FontFamily(fileFonts), fileFonts)
    }

    return fontFamilies
}

private fun looksItalic(name: String): Boolean = name.trimEnd().endsWith("italic", ignoreCase = true)

// The mappings are somewhat arbitrary, and may look wrong, but this just going in order on both sides
fun fontWeightFromTextAttributeValue(weightValue: Float?): FontWeight? =
    when (weightValue) {
        TextAttribute.WEIGHT_EXTRA_LIGHT -> FontWeight.Thin
        TextAttribute.WEIGHT_LIGHT -> FontWeight.ExtraLight
        TextAttribute.WEIGHT_DEMILIGHT -> FontWeight.Light
        TextAttribute.WEIGHT_REGULAR -> FontWeight.Normal
        TextAttribute.WEIGHT_SEMIBOLD -> FontWeight.Medium
        TextAttribute.WEIGHT_MEDIUM -> FontWeight.SemiBold
        TextAttribute.WEIGHT_BOLD -> FontWeight.Bold
        TextAttribute.WEIGHT_HEAVY, TextAttribute.WEIGHT_EXTRABOLD -> FontWeight.ExtraBold
        TextAttribute.WEIGHT_ULTRABOLD -> FontWeight.Black
        else -> null
    }

private fun inferWeightFromName(nameTokens: List<String>): FontWeight =
    when {
        nameTokens.any { it.startsWith("thin") || it == "100" } -> FontWeight.Thin
        nameTokens.any {
            it.startsWith("extralight") || it.startsWith("semilight") || it.startsWith("extra light")
                || it.startsWith("semi light") || it.startsWith("extra-light") || it.startsWith("semi-light") || it == "200"
        } -> FontWeight.ExtraLight
        nameTokens.any { it.startsWith("light") || it == "300" } -> FontWeight.Light
        nameTokens.any { it.startsWith("medium") || it == "500" } -> FontWeight.Medium
        nameTokens.any { it.startsWith("semibold") || it.startsWith("semi bold") || it.startsWith("semi-bold") || it == "600" } -> FontWeight.SemiBold
        nameTokens.any { it.startsWith("bold") || it == "700" } -> FontWeight.Bold
        nameTokens.any {
            it.startsWith("extrabold") || it.startsWith("extra bold") || it.startsWith("extra-bold")
                || it.startsWith("heavy") || it == "800"
        } -> FontWeight.ExtraBold
        nameTokens.any { it.startsWith("black") || it == "900" } -> FontWeight.Black
        else -> FontWeight.Normal
    }
