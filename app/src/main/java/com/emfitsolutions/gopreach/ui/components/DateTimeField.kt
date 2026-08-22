package com.emfitsolutions.gopreach.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * A button that opens the native date picker then time picker and reports the
 * result as epoch millis — used everywhere a [com.emfitsolutions.gopreach.data.model.Schedule]
 * start/end time is entered (Chat Schedule spec §5.1, Calendar spec §6.2), so
 * that widget isn't rebuilt per feature.
 */
@Composable
fun DateTimeField(
    label: String,
    valueMillis: Long?,
    onValueChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val displayFormat = remember { SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()) }

    OutlinedButton(
        onClick = {
            val calendar = Calendar.getInstance().apply { valueMillis?.let { timeInMillis = it } }
            DatePickerDialog(
                context,
                { _, year, month, day ->
                    TimePickerDialog(
                        context,
                        { _, hour, minute ->
                            calendar.set(year, month, day, hour, minute)
                            onValueChange(calendar.timeInMillis)
                        },
                        calendar.get(Calendar.HOUR_OF_DAY),
                        calendar.get(Calendar.MINUTE),
                        false,
                    ).show()
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH),
            ).show()
        },
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(if (valueMillis != null) "$label: ${displayFormat.format(valueMillis)}" else "Set $label")
    }
}
