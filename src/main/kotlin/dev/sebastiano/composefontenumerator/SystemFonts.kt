package dev.sebastiano.composefontenumerator

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.FileFont
import androidx.compose.ui.text.platform.Font
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import java.awt.GraphicsEnvironment
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

    val registryMap = Advapi32Util.registryGetValues(WinReg.HKEY_LOCAL_MACHINE, FONTS_KEY_PATH) as TreeMap<String, String>

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
        .toMutableMap()

    return collectIntoSystemFontFamilies(fontFamilyNames, fontFiles)
}

private fun collectIntoSystemFontFamilies(
    fontFamilyNames: Iterable<String>,
    fontFiles: MutableMap<String, File>
): MutableList<SystemFontFamily> {
    val sortedFontFamilyNames = fontFamilyNames.sortedByDescending { it.length }
    val fontFamilies = mutableListOf<SystemFontFamily>()
    for (familyName in sortedFontFamilyNames) {
        println("Processing font family: $familyName")
        val files = fontFiles.filterKeys { name -> name.startsWith(familyName) }

        for ((name, _) in files) {
            fontFiles.remove(name)
        }

        if (files.isEmpty()) {
            System.err.println("Font family '$familyName' has no associated font files")
            continue
        }

        val fonts = files.map { (name, file) ->
            val nameTokens = name.substring(familyName.length)
                .split(' ', '-')
                .map { it.trim().lowercase() }
                .filter { it.isNotBlank() }

            Font(file = file, weight = inferWeightFromName(nameTokens), style = inferStyleFromName(nameTokens)) as FileFont
        }

        fontFamilies += SystemFontFamily(familyName, FontFamily(fonts), fonts)
    }

    return fontFamilies
}

private fun inferWeightFromName(nameTokens: List<String>): FontWeight =
    when {
        nameTokens.any { it == "thin" || it == "100" } -> FontWeight.Thin
        nameTokens.any { it == "extralight" || it == "semilight" || it == "extra light" || it == "semi light" || it == "extra-light" || it == "semi-light" || it == "200" } -> FontWeight.ExtraLight
        nameTokens.any { it == "light" || it == "300" } -> FontWeight.Light
        nameTokens.any { it == "medium" || it == "500" } -> FontWeight.Medium
        nameTokens.any { it == "semibold" || it == "semi bold" || it == "semi-bold" || it == "600" } -> FontWeight.SemiBold
        nameTokens.any { it == "bold" || it == "700" } -> FontWeight.Bold
        nameTokens.any { it == "extrabold" || it == "extra bold" || it == "extra-bold" || it == "800" } -> FontWeight.ExtraBold
        nameTokens.any { it == "black" || it == "900" } -> FontWeight.Black
        else -> FontWeight.Normal
    }

private fun inferStyleFromName(nameTokens: List<String>): FontStyle =
    when {
        nameTokens.contains("italic") -> FontStyle.Italic
        else -> FontStyle.Normal
    }
