package com.libredescarga.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.libredescarga.core.designsystem.icon.LibreIcons
import com.libredescarga.core.designsystem.theme.LibreTheme
import com.libredescarga.feature.onboarding.component.PermissionsPage
import com.libredescarga.feature.onboarding.component.TermsPage
import com.libredescarga.feature.onboarding.component.WelcomePage
import kotlinx.coroutines.launch

internal const val ONBOARDING_PAGE_COUNT = 3

@Composable
internal fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnFinished by rememberUpdatedState(onFinished)

    LaunchedEffect(uiState.finished) {
        if (uiState.finished) currentOnFinished()
    }

    OnboardingContent(
        uiState = uiState,
        onTermsAcceptedChange = viewModel::onTermsAcceptedChange,
        onFinish = viewModel::onFinish,
    )
}

/** Bienvenida en 3 pasos: principios, permisos y términos de uso. */
@Composable
internal fun OnboardingContent(
    uiState: OnboardingUiState,
    onTermsAcceptedChange: (Boolean) -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { ONBOARDING_PAGE_COUNT })
    val scope = rememberCoroutineScope()
    val currentPage = pagerState.currentPage

    fun goTo(page: Int) {
        scope.launch { pagerState.animateScrollToPage(page) }
    }

    BackHandler(enabled = currentPage > 0) { goTo(currentPage - 1) }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            OnboardingBottomBar(
                currentPage = currentPage,
                canFinish = uiState.canFinish,
                onBack = { goTo(currentPage - 1) },
                onNext = { goTo(currentPage + 1) },
                onFinish = onFinish,
            )
        },
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalAlignment = Alignment.Top,
        ) { page ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                ) {
                    when (page) {
                        0 -> WelcomePage()
                        1 -> PermissionsPage()
                        else -> TermsPage(
                            accepted = uiState.termsAccepted,
                            onAcceptedChange = onTermsAcceptedChange,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingBottomBar(
    currentPage: Int,
    canFinish: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit,
) {
    val isLastPage = currentPage == ONBOARDING_PAGE_COUNT - 1
    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (currentPage > 0) {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.onboarding_back))
                    }
                }
            }
            PageIndicator(pageCount = ONBOARDING_PAGE_COUNT, currentPage = currentPage)
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                if (isLastPage) {
                    Button(onClick = onFinish, enabled = canFinish) {
                        Text(stringResource(R.string.onboarding_start))
                    }
                } else {
                    Button(onClick = onNext) {
                        Text(stringResource(R.string.onboarding_next))
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = LibreIcons.Forward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PageIndicator(pageCount: Int, currentPage: Int, modifier: Modifier = Modifier) {
    val description = stringResource(
        R.string.onboarding_step_content_description,
        currentPage + 1,
        pageCount,
    )
    Row(
        modifier = modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val selected = index == currentPage
            val width by animateDpAsState(
                targetValue = if (selected) 24.dp else 8.dp,
                label = "indicatorWidth",
            )
            val color by animateColorAsState(
                targetValue = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                label = "indicatorColor",
            )
            Box(
                modifier = Modifier
                    .size(width = width, height = 8.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 760)
@Composable
private fun OnboardingContentPreview() {
    LibreTheme {
        OnboardingContent(
            uiState = OnboardingUiState(),
            onTermsAcceptedChange = {},
            onFinish = {},
        )
    }
}
