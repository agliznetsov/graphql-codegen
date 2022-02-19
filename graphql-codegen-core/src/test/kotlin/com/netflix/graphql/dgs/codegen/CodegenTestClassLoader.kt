/*
 *
 *  Copyright 2020 Netflix, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.netflix.graphql.dgs.codegen

import com.google.testing.compile.Compilation
import java.io.ByteArrayOutputStream
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import javax.tools.JavaFileObject

internal class CodegenTestClassLoader(private val compilation: Compilation, parent: ClassLoader?) : ClassLoader(parent) {

    private val seenClasses = ConcurrentHashMap<String, Class<*>>()

    @Throws(ClassNotFoundException::class)
    override fun loadClass(name: String): Class<*> {
        val packageNameAsUnixPath = name.replace(".", "/")
        val normalizedName = "/CLASS_OUTPUT/$packageNameAsUnixPath.class"

        return seenClasses.computeIfAbsent(normalizedName) { _ ->
            Optional.ofNullable(
                compilation
                    .generatedFiles()
                    .find { it.kind == JavaFileObject.Kind.CLASS && it.name == normalizedName }
            ).map { fileObject ->
                val classData = fileObject.openInputStream().use { inputStream ->
                    val buffer = ByteArrayOutputStream()
                    inputStream.copyTo(buffer)
                    buffer.toByteArray()
                }
                defineClass(name, classData, 0, classData.size)
            }.orElse(super.loadClass(name))
        }
    }
}
