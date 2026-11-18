package com.stephenmorris.recorder.tape

/** [base], or if it's taken, `base 2`, `base 3` and so on: the first that isn't in [names]. */
fun uniqueName(base: String, names: List<String>): String {
    if (base !in names) {
        return base
    }
    var number = 2
    while ("$base $number" in names) {
        number += 1
    }
    return "$base $number"
}
