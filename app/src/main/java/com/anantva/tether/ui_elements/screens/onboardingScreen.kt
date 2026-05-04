package com.anantva.tether.ui_elements.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anantva.tether.ui.theme.Figtree

private val TetherRed = Color(0xFFE53935)
private val DarkBg = Color(0xFF0F0F0F)
private val MutedText = Color(0xFFA0A0A0)
private val TrackGrey = Color(0xFF343434)

data class OnboardingPage(
    val title: String,
    val description: String
)

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit
) {
    val pages = remember {
        listOf(
            OnboardingPage(
                "Welcome to Tether",
                "Your personal finance companion that helps you save money and reach your goals."
            ),
            OnboardingPage(
                "Track Your Spending",
                "Keep an eye on every transaction and understand where your money goes."
            ),
            OnboardingPage(
                "Set Your Goals",
                "Define your savings goals and watch your progress grow day by day."
            ),
            OnboardingPage(
                "Stay on Track",
                "Get reminders and insights to help you stay on top of your finances."
            )
        )
    }

    var currentPage by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(44.dp))

            OnboardingProgress(
                currentPage = currentPage,
                pageCount = pages.size
            )

            Spacer(modifier = Modifier.height(36.dp))

            val infiniteTransition = rememberInfiniteTransition(label = "onboardingFloat")
            val floatOffset by infiniteTransition.animateFloat(
                initialValue = -8f,
                targetValue = 8f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "ballFloat"
            )

            AnimatedContent(
                targetState = currentPage,
                transitionSpec = {
                    val direction = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(
                        animationSpec = tween(520, easing = FastOutSlowInEasing)
                    ) { fullWidth -> fullWidth * direction } + fadeIn(
                        animationSpec = tween(360, delayMillis = 80)
                    )) togetherWith (slideOutHorizontally(
                        animationSpec = tween(420, easing = FastOutSlowInEasing)
                    ) { fullWidth -> -fullWidth * direction } + fadeOut(
                        animationSpec = tween(220)
                    ))
                },
                label = "onboardingGraphic"
            ) { pageIndex ->
                OnboardingGraphic(
                    pageIndex = pageIndex,
                    modifier = Modifier
                        .offset(y = floatOffset.dp)
                        .size(150.dp)
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            AnimatedContent(
                targetState = currentPage,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    val direction = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(
                        animationSpec = tween(520, easing = FastOutSlowInEasing)
                    ) { fullWidth -> fullWidth * direction } + fadeIn(
                        animationSpec = tween(360, delayMillis = 80)
                    )) togetherWith (slideOutHorizontally(
                        animationSpec = tween(420, easing = FastOutSlowInEasing)
                    ) { fullWidth -> -fullWidth * direction } + fadeOut(
                        animationSpec = tween(220)
                    ))
                },
                label = "onboardingCopy"
            ) { pageIndex ->
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val titleAlpha = delayedFadeProgress(pageIndex, delayMillis = 80)
                    val subtitleAlpha = delayedFadeProgress(pageIndex, delayMillis = 180)

                    Text(
                        text = pages[pageIndex].title,
                        modifier = Modifier
                            .alpha(titleAlpha)
                            .offset(y = ((1f - titleAlpha) * 10f).dp),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = Figtree,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = pages[pageIndex].description,
                        modifier = Modifier
                            .alpha(subtitleAlpha)
                            .offset(y = ((1f - subtitleAlpha) * 10f).dp),
                        fontSize = 16.sp,
                        color = MutedText,
                        textAlign = TextAlign.Center
                    )
                }
            }

            val buttonAlpha = delayedFadeProgress(currentPage, delayMillis = 300)

            Button(
                onClick = {
                    if (currentPage < pages.size - 1) {
                        currentPage++
                    } else {
                        onComplete()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .alpha(buttonAlpha)
                    .offset(y = ((1f - buttonAlpha) * 8f).dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TetherRed
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                AnimatedContent(
                    targetState = currentPage < pages.size - 1,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(180, delayMillis = 60)) togetherWith
                            fadeOut(animationSpec = tween(120))
                    },
                    label = "onboardingButtonText"
                ) { hasNextPage ->
                    Text(
                        text = if (hasNextPage) "Next" else "Get Started",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = Figtree,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Skip button
            TextButton(
                onClick = onComplete
            ) {
                Text(
                    text = "Skip",
                    fontSize = 14.sp,
                    fontFamily = Figtree,
                    color = MutedText
                )
            }
        }
    }
}

@Composable
private fun OnboardingProgress(
    currentPage: Int,
    pageCount: Int
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val isSelected = currentPage == index
            val width by animateDpAsState(
                targetValue = if (isSelected) 28.dp else 8.dp,
                animationSpec = tween(260, easing = FastOutSlowInEasing),
                label = "progressWidth"
            )
            val color by animateColorAsState(
                targetValue = if (index <= currentPage) TetherRed else TrackGrey,
                animationSpec = tween(260),
                label = "progressColor"
            )

            Box(
                modifier = Modifier
                    .width(width)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
        }
    }
}

@Composable
private fun OnboardingGraphic(
    pageIndex: Int,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val red = TetherRed
        val softRed = Color(0xFFFF6B68)
        val deepRed = Color(0xFF8E1515)
        val darkCard = Color(0xFF191919)

        when (pageIndex) {
            0 -> {
                drawCircle(red.copy(alpha = 0.16f), radius = size.minDimension * 0.48f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(softRed, red, deepRed),
                        center = center,
                        radius = size.minDimension * 0.36f
                    ),
                    radius = size.minDimension * 0.34f,
                    center = center
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.16f),
                    radius = size.minDimension * 0.09f,
                    center = Offset(size.width * 0.42f, size.height * 0.38f)
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.18f),
                    radius = size.minDimension * 0.44f,
                    center = center,
                    style = Stroke(width = 3.dp.toPx())
                )
            }

            1 -> {
                drawRoundRect(
                    color = darkCard,
                    topLeft = Offset(size.width * 0.18f, size.height * 0.18f),
                    size = Size(size.width * 0.64f, size.height * 0.64f),
                    cornerRadius = CornerRadius(24.dp.toPx())
                )
                repeat(3) { index ->
                    val left = size.width * (0.3f + index * 0.15f)
                    val top = size.height * (0.62f - index * 0.11f)
                    drawRoundRect(
                        color = if (index == 2) red else TrackGrey,
                        topLeft = Offset(left, top),
                        size = Size(size.width * 0.08f, size.height * (0.18f + index * 0.09f)),
                        cornerRadius = CornerRadius(8.dp.toPx())
                    )
                }
                drawLine(
                    color = softRed,
                    start = Offset(size.width * 0.28f, size.height * 0.42f),
                    end = Offset(size.width * 0.72f, size.height * 0.34f),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            2 -> {
                repeat(3) { index ->
                    drawCircle(
                        color = if (index % 2 == 0) red.copy(alpha = 0.18f) else TrackGrey,
                        radius = size.minDimension * (0.42f - index * 0.1f),
                        center = center,
                        style = Stroke(width = 7.dp.toPx())
                    )
                }
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(softRed, red, deepRed),
                        center = center,
                        radius = size.minDimension * 0.2f
                    ),
                    radius = size.minDimension * 0.16f,
                    center = center
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.28f),
                    start = Offset(size.width * 0.63f, size.height * 0.37f),
                    end = Offset(size.width * 0.8f, size.height * 0.2f),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            else -> {
                drawCircle(red.copy(alpha = 0.14f), radius = size.minDimension * 0.46f)
                drawRoundRect(
                    color = darkCard,
                    topLeft = Offset(size.width * 0.26f, size.height * 0.2f),
                    size = Size(size.width * 0.48f, size.height * 0.58f),
                    cornerRadius = CornerRadius(28.dp.toPx())
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(softRed, red, deepRed),
                        center = center,
                        radius = size.minDimension * 0.25f
                    ),
                    radius = size.minDimension * 0.22f,
                    center = center
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.82f),
                    start = Offset(size.width * 0.41f, size.height * 0.5f),
                    end = Offset(size.width * 0.48f, size.height * 0.58f),
                    strokeWidth = 5.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.82f),
                    start = Offset(size.width * 0.48f, size.height * 0.58f),
                    end = Offset(size.width * 0.61f, size.height * 0.43f),
                    strokeWidth = 5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
private fun delayedFadeProgress(
    key: Int,
    delayMillis: Int
): Float {
    var visible by remember(key) { mutableStateOf(false) }

    LaunchedEffect(key) {
        visible = true
    }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(
            durationMillis = 360,
            delayMillis = delayMillis,
            easing = FastOutSlowInEasing
        ),
        label = "delayedFade"
    )

    return alpha
}
