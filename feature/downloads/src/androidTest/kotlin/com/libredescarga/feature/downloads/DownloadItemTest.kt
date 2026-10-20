package com.libredescarga.feature.downloads

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.libredescarga.core.designsystem.theme.LibreTheme
import com.libredescarga.core.domain.usecase.DownloadRequestFactory
import com.libredescarga.core.model.DownloadStatus
import com.libredescarga.core.model.DownloadTask
import com.libredescarga.core.model.VideoQuality
import com.libredescarga.core.testing.TestMedia
import com.libredescarga.feature.downloads.component.DownloadItem
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadItemTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun task(status: DownloadStatus) = DownloadTask(
        id = "1",
        request = DownloadRequestFactory.video(TestMedia.entry, VideoQuality.P720),
        status = status,
        progressPercent = 45f,
        speedBytesPerSecond = 2_000_000,
        etaSeconds = 20,
        totalBytes = 50_000_000,
        downloadedBytes = 22_500_000,
        outputUri = null,
        errorMessage = null,
        priority = 0,
        createdAt = 0,
        completedAt = null,
    )

    private fun show(task: DownloadTask, onAction: (String) -> Unit) {
        composeRule.setContent {
            LibreTheme {
                DownloadItem(
                    task = task,
                    wifiOnly = false,
                    isFirstInQueue = false,
                    onPause = { onAction("pause") },
                    onResume = { onAction("resume") },
                    onCancel = { onAction("cancel") },
                    onRetry = { onAction("retry") },
                    onPrioritize = { onAction("prioritize") },
                    onOpen = { onAction("open") },
                    onShare = { onAction("share") },
                    onRemove = { onAction("remove") },
                )
            }
        }
    }

    @Test
    fun enPausaMuestraReanudar() {
        var action = ""
        show(task(DownloadStatus.PAUSED)) { action = it }

        composeRule.onNodeWithText(context.getString(R.string.downloads_paused, 45)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.downloads_action_resume)).performClick()
        assertEquals("resume", action)
    }

    @Test
    fun fallidaMuestraElMensajeYReintentar() {
        var action = ""
        show(task(DownloadStatus.FAILED).copy(errorMessage = "drm")) { action = it }

        composeRule.onNodeWithText(context.getString(com.libredescarga.core.ui.R.string.core_ui_error_drm))
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.downloads_action_retry)).performClick()
        assertEquals("retry", action)
    }
}
