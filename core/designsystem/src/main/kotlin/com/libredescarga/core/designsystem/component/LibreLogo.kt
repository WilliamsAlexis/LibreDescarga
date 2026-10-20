package com.libredescarga.core.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.libredescarga.core.designsystem.R

/**
 * Logo de LibreDescarga: una flecha de descarga que cae en una forma
 * abierta con punta de ala, que transmite libertad.
 */
@Composable
fun LibreLogo(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    contentDescription: String? = null,
) {
    Image(
        painter = painterResource(R.drawable.ic_libre_logo),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
    )
}

/**
 * Nombre de la app leído de su etiqueta (definida solo en app/src/main/res/values/strings.xml),
 * para que los módulos no repitan el nombre en sus propios textos.
 */
@Composable
fun rememberAppName(): String {
    val context = LocalContext.current
    return remember(context) {
        context.applicationInfo.loadLabel(context.packageManager).toString()
    }
}
