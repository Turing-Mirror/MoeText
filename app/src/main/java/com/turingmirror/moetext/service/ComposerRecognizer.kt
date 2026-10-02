package com.turingmirror.moetext.service

/**
 * Recognition state for one service session. Once an app's composer id is seen, that build
 * keeps its id, so the layout fallback turns off for the app and cannot pick other input boxes.
 */
internal class ComposerRecognizer {
    private val confirmed = HashSet<String>()
    private val learned = HashMap<String, MutableSet<String>>()

    fun match(packageName: String?, resourceId: String?, label: String?): ComposerMatch? {
        val match = ChatTargets.match(packageName, resourceId, label)
        if (match == ComposerMatch.ID && packageName != null && confirmed.add(packageName)) learned.remove(packageName)
        return match
    }

    fun layoutAllowed(packageName: String?): Boolean =
        ChatTargets.usesLayoutFallback(packageName) && packageName !in confirmed

    /** Records an id found through the layout fallback; true when it is new. */
    fun learn(packageName: String, resourceId: String): Boolean =
        layoutAllowed(packageName) && learned.getOrPut(packageName) { HashSet() }.add(resourceId)

    fun lookupIds(packageName: String): List<String> =
        ChatTargets.viewIdsFor(packageName) + learned[packageName].orEmpty()
}
