package com.anantva.tether.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.anantva.tether.R

// Define the Figtree Family using your single figtree.ttf file
val Figtree = FontFamily(
    Font(R.font.figtree_regular, FontWeight.Normal),
    Font(R.font.figtree_medium, FontWeight.Medium),
    Font(R.font.figtree_semi_bold, FontWeight.SemiBold),
    Font(R.font.figtree_bold, FontWeight.Bold),
    Font(R.font.figtree_extra_bold, FontWeight.ExtraBold), // Weight 800
    Font(R.font.figtree_black, FontWeight.Black)      // Weight 900
)

// Renamed to TetherTypography to prevent Android Studio crashes
val TetherTypography = Typography(
    // Massive Daily Limit text
    displayLarge = TextStyle(
        fontFamily = Figtree,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        color = TetherWhite // Now this will work once Color.kt is properly set up
    ),
    // Standard Headers
    titleLarge = TextStyle(
        fontFamily = Figtree,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        color = TetherWhite
    ),
    // Standard Body Text
    bodyLarge = TextStyle(
        fontFamily = Figtree,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        color = TetherWhite
    ),
    // Subtle subtext (e.g., dates, merchant info)
    bodyMedium = TextStyle(
        fontFamily = Figtree,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        color = GrimeGrey
    )
)