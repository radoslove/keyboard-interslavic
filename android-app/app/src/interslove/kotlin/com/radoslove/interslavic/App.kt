package com.radoslove.interslavic

/**
 * What makes THIS app this app. Two apps are built from one code
 * (`build.gradle.kts`, product flavors `interslove` and `isc`); per flavor
 * only this file, [Ui] (the settings texts) and `res/` (method.xml, names)
 * differ. Both offer every language; they differ in the language they open
 * with and the one their own collector and ranking serve - so each project
 * gets its own research data.
 */
object App {

    /** The project's own language: its collector, ranking and default subtype. */
    const val MAIN = "isv"

    /** Every language this keyboard offers, in the order of method.xml. */
    val LANGS = listOf("isv", "isc", "pl", "nb", "en")

    /** The main language's name in itself, in exports. */
    const val MAIN_NAME = "Medžuslovjansky"
}
