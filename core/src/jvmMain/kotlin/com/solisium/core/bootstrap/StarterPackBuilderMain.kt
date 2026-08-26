package com.solisium.core.bootstrap

import java.nio.file.Path

fun main(args: Array<String>) {
    val output = Path.of(args.firstOrNull() ?: error("output directory required"))
    StarterPackBuilder.build(output)
    println("starter pack written to $output")
}
