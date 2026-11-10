package ca.safehaven.intake

import androidx.annotation.StringRes
import ca.safehaven.intake.domain.model.VisitReason

@StringRes
fun VisitReason.labelRes(): Int = when (this) {
    VisitReason.ADOPTING -> R.string.reason_adopting
    VisitReason.VOLUNTEERING -> R.string.reason_volunteering
    VisitReason.VISITING -> R.string.reason_visiting
    VisitReason.DROPPING_OFF -> R.string.reason_dropping_off
    VisitReason.OTHER -> R.string.reason_other
}
