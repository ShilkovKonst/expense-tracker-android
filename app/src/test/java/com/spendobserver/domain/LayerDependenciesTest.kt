package com.spendobserver.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Storage stays in the data layer: the domain knows neither Room nor the
 * layers above it, and screens never see tables, only repositories and domain
 * models. The compiler can't enforce any of this within one module.
 */
class LayerDependenciesTest {

    private fun importsIn(layer: String, vararg forbidden: String): List<String> {
        val root = File("src/main/java/com/spendobserver/$layer")
        check(root.isDirectory) { "Run from the app module: ${root.absolutePath}" }
        return root.walkTopDown()
            .filter { it.extension == "kt" }
            .flatMap { file ->
                file.readLines()
                    .filter { line -> forbidden.any { line.startsWith("import $it") } }
                    .map { "${file.name}: $it" }
            }
            .toList()
    }

    @Test
    fun domain_knowsNothingOfStorageOrScreens() {
        assertEquals(
            emptyList<String>(),
            importsIn("domain", "com.spendobserver.data.", "com.spendobserver.ui.", "androidx.room.", "android."),
        )
    }

    @Test
    fun screens_neverSeeTheDatabase() {
        assertEquals(emptyList<String>(), importsIn("ui", "com.spendobserver.data.local.", "androidx.room."))
    }
}
