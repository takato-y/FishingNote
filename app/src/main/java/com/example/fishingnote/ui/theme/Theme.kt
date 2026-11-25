package com.example.fishingnote.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * 配色の決まり：白いカード＋海の青1色を基本にし、色で意味を持たせるのは
 * 「注意（赤）」と「欲しい物（橙）」の2つだけにする。色数を絞って、すっきり見せる。
 * 屋外でも読めるよう、文字と背景の差は WCAG の 4.5:1 以上を保つ。
 */
// 鮮やかな海の青（白い文字とのコントラスト 5.4:1）。グラデーションのカードはダークモードでもこの色を使う
val Ocean = Color(0xFF0B63E5)
/** グラデーションの終わりの色（深い青緑。白い文字とのコントラスト 5.0:1） */
val OceanDeep = Color(0xFF0077A0)

private val Light = lightColorScheme(
    primary = Ocean,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6EFFD),
    onPrimaryContainer = Color(0xFF062B66),
    // 補助の色も青の仲間にそろえる（ボタンの淡い塗り・バッジに使われる）
    secondary = Color(0xFF3D6A8A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6EFFD),
    onSecondaryContainer = Color(0xFF062B66),
    tertiary = Color(0xFF9A5A00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE6CC),
    onTertiaryContainer = Color(0xFF3A2000),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFFCE4E2),
    onErrorContainer = Color(0xFF5C1410),
    // 「元に戻す」などの通知の帯のボタンの色（初期値の紫にしない）
    inversePrimary = Color(0xFF9CCBF2),
    background = Color(0xFFF4F6FB),
    onBackground = Color(0xFF15202B),
    surface = Color(0xFFF4F6FB),
    onSurface = Color(0xFF15202B),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F9FC),
    surfaceContainer = Color.White,          // 下のタブ
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color.White,   // カード
    surfaceVariant = Color(0xFFE6ECF2),
    onSurfaceVariant = Color(0xFF46525E),
    outline = Color(0xFF75808B),
    outlineVariant = Color(0xFFDCE3EA),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF9CCBF2),
    onPrimary = Color(0xFF00324F),
    primaryContainer = Color(0xFF1B3A52),
    onPrimaryContainer = Color(0xFFD7E9F8),
    secondary = Color(0xFFA9C8E0),
    secondaryContainer = Color(0xFF1B3A52),
    onSecondaryContainer = Color(0xFFD7E9F8),
    tertiary = Color(0xFFFFB86E),
    tertiaryContainer = Color(0xFF5A3500),
    onTertiaryContainer = Color(0xFFFFE6CC),
    background = Color(0xFF0F1418),
    surface = Color(0xFF0F1418),
    surfaceContainerLowest = Color(0xFF1A232B),   // カードの地（背景より少し明るくして見分ける）
    surfaceContainerLow = Color(0xFF161E25),
    surfaceContainer = Color(0xFF182027),
    surfaceContainerHigh = Color(0xFF1C252D),
    surfaceContainerHighest = Color(0xFF1C252D),
    outlineVariant = Color(0xFF2E3943),
)

// 端末の言語が日本語以外でも、漢字を日本語の字形で表示する（指定しないと中国語の字形で描かれる）
private val Ja = LocaleList("ja-JP")
private fun TextStyle.ja() = copy(localeList = Ja)

// 屋外で腕を伸ばしたままでも読めるよう、標準より一段大きい文字にする。見出しは太めにして階層を分かりやすくする
private val base = Typography()
private val Sized = Typography(
    headlineSmall = base.headlineSmall.copy(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontSize = 19.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontSize = 18.sp, lineHeight = 26.sp),
    bodyMedium = base.bodyMedium.copy(fontSize = 16.sp, lineHeight = 24.sp),
    bodySmall = base.bodySmall.copy(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = base.labelLarge.copy(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontSize = 15.sp, lineHeight = 20.sp),
    labelSmall = base.labelSmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
)
private val AppTypography = with(Sized) {
    Typography(
        displayLarge = displayLarge.ja(), displayMedium = displayMedium.ja(), displaySmall = displaySmall.ja(),
        headlineLarge = headlineLarge.ja(), headlineMedium = headlineMedium.ja(), headlineSmall = headlineSmall.ja(),
        titleLarge = titleLarge.ja(), titleMedium = titleMedium.ja(), titleSmall = titleSmall.ja(),
        bodyLarge = bodyLarge.ja(), bodyMedium = bodyMedium.ja(), bodySmall = bodySmall.ja(),
        labelLarge = labelLarge.ja(), labelMedium = labelMedium.ja(), labelSmall = labelSmall.ja(),
    )
}

// 角を大きめに丸めて、やわらかい印象にする
private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
)

@Composable
fun FishingNoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
