package com.example.ui.registration

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Muestra qué posiciones de la placa tuvieron poco consenso entre las
 * hipótesis del reconocedor. No bloquea el guardado: orienta la revisión.
 */
@Composable
fun PlateConfidenceHint(
    plate: String,
    confidence: List<Float>,
    modifier: Modifier = Modifier
) {
    if (
        plate.isBlank() ||
        confidence.isEmpty()
    ) {
        return
    }

    val normalized =
        plate.uppercase()
            .replace("-", "")
            .replace(" ", "")
            .take(6)

    if (
        normalized.isBlank()
    ) {
        return
    }

    val uncertainPositions =
        confidence
            .take(6)
            .mapIndexedNotNull {
                    index,
                    value ->
                if (
                    value > 0f &&
                    value < LOW_CONFIDENCE_THRESHOLD &&
                    index < normalized.length
                ) {
                    index
                } else {
                    null
                }
            }

    Column(
        modifier = modifier
    ) {
        Row(
            horizontalArrangement =
                Arrangement.spacedBy(6.dp),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            normalized.forEachIndexed {
                    index,
                    char ->
                val uncertain =
                    index in uncertainPositions

                Surface(
                    shape =
                        RoundedCornerShape(8.dp),
                    color =
                        if (uncertain) {
                            Color(0xFFFFF7E6)
                        } else {
                            MaterialTheme
                                .colorScheme
                                .surfaceVariant
                        },
                    modifier =
                        Modifier
                            .size(36.dp)
                            .then(
                                if (uncertain) {
                                    Modifier.border(
                                        width = 1.dp,
                                        color = Color(
                                            0xFFD97706
                                        ),
                                        shape =
                                            RoundedCornerShape(
                                                8.dp
                                            )
                                    )
                                } else {
                                    Modifier
                                }
                            )
                ) {
                    Row(
                        verticalAlignment =
                            Alignment.CenterVertically,
                        horizontalArrangement =
                            Arrangement.Center
                    ) {
                        Text(
                            text = char.toString(),
                            fontSize = 16.sp,
                            fontWeight =
                                FontWeight.Bold,
                            color =
                                if (uncertain) {
                                    Color(0xFFB45309)
                                } else {
                                    MaterialTheme
                                        .colorScheme
                                        .onSurface
                                }
                        )
                    }
                }
            }
        }

        if (
            uncertainPositions.isNotEmpty()
        ) {
            Text(
                text =
                    "Revisa las posiciones ${uncertainPositions.joinToString(", ") { (it + 1).toString() }}: " +
                        "hay alternativas distintas. Puedes corregir solo un carácter por voz, " +
                        "por ejemplo «corrige último dígito negativo», o editar la placa.",
                fontSize = 11.sp,
                color = Color(0xFFB45309),
                modifier =
                    Modifier.padding(top = 6.dp)
            )
        }
    }
}

private const val LOW_CONFIDENCE_THRESHOLD =
    0.60f
