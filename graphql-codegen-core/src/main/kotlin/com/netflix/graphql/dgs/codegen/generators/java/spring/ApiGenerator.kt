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

package com.netflix.graphql.dgs.codegen.generators.java.spring

import com.netflix.graphql.dgs.codegen.*
import com.netflix.graphql.dgs.codegen.generators.java.TypeUtils
import com.netflix.graphql.dgs.codegen.generators.java.sanitizeJavaDoc
import com.squareup.javapoet.*
import graphql.language.*
import javax.lang.model.element.Modifier

class ApiGenerator(private val config: CodeGenConfig, private val document: Document) {

    val SpringAnnotations = "org.springframework.graphql.data.method.annotation"
    val QueryMapping = "QueryMapping"
    val MutationMapping = "MutationMapping"
    val BatchMapping = "BatchMapping"
    val SchemaMapping = "SchemaMapping"
    val Argument = "Argument"

//    val Mono = ClassName.get("reactor.core.publisher", "Mono")

    private val typeUtils = TypeUtils(config.packageNameTypes, config, document)
    private val useInterfaceType = config.generateInterfaces

    fun generate(definition: ObjectTypeDefinition): CodeGenResult {

        if (definition.shouldSkip(config)) {
            return CodeGenResult()
        }

        val javaType = TypeSpec.interfaceBuilder(definition.name + "Api")
            .addModifiers(Modifier.PUBLIC)

        if (definition.description != null) {
            javaType.addJavadoc(definition.description.sanitizeJavaDoc())
        }

        val fields = if (definition.name == "Query" || definition.name == "Mutation")
            definition.fieldDefinitions else definition.fieldDefinitions.filterFetchFields()
        fields.forEach {
            addInterfaceMethod(definition, it, javaType)
        }

        if (javaType.methodSpecs.isEmpty()) {
            return CodeGenResult()
        } else {
           val javaFile = JavaFile.builder(config.packageNameApis, javaType.build()).build()
            return CodeGenResult(javaApis = listOf(javaFile))
        }
    }

    private fun addInterfaceMethod(definition: ObjectTypeDefinition, fieldDefinition: FieldDefinition, javaType: TypeSpec.Builder) {
        val returnType = typeUtils.findReturnType(fieldDefinition.type, useInterfaceType)

        val fieldName = fieldDefinition.name
        val batch = isBatch(fieldDefinition)
        val rootQuery = definition.name == "Query" || definition.name == "Mutation"
        val parentType = ClassName.get(config.packageNameTypes, definition.name)

        val methodBuilder = MethodSpec.methodBuilder(fieldName)
            .addModifiers(Modifier.ABSTRACT, Modifier.PUBLIC)
            .addAnnotation(ClassName.get(SpringAnnotations, getMethodAnnotaton(definition, batch)))
        if (fieldDefinition.description != null) {
            methodBuilder.addJavadoc(fieldDefinition.description.sanitizeJavaDoc())
        }

        if (batch) {
            val paramType = ParameterizedTypeName.get(ClassName.get(List::class.java), parentType)
            val paramBuilder = ParameterSpec.builder(paramType, "parents")
            methodBuilder.addParameter(paramBuilder.build())

            methodBuilder.returns(
//                    ParameterizedTypeName.get(Mono,
                    ParameterizedTypeName.get(ClassName.get(Map::class.java), parentType, returnType)
//            )
            )
        } else {
            methodBuilder.returns(returnType)

            if (!rootQuery) {
                methodBuilder.addParameter(ParameterSpec.builder(parentType, "parent").build())
            }

            fieldDefinition.inputValueDefinitions.forEach {
                val paramType = typeUtils.findReturnType(it.type, useInterfaceType)
                val paramBuilder = ParameterSpec.builder(paramType, it.name)
                paramBuilder.addAnnotation(ClassName.get(SpringAnnotations, Argument))
                methodBuilder.addParameter(paramBuilder.build())
            }
        }

        javaType.addMethod(methodBuilder.build())
    }

    private fun getMethodAnnotaton(definition: ObjectTypeDefinition, batch: Boolean): String {
        return if (definition.name == "Query")
            QueryMapping
        else if (definition.name == "Mutation")
            MutationMapping
        else {
            if (batch) BatchMapping else SchemaMapping
        }
    }

    private fun isBatch(fieldDefinition: FieldDefinition): Boolean {
        val fetch = fieldDefinition.directivesByName.get("fetch")?.firstOrNull()
        if (fetch != null) {
            val batch = fetch.argumentsByName.get("batch")
            if (batch != null) {
                val value: BooleanValue = batch.value as BooleanValue
                return value.isValue
            }
        }
        return false
    }
}
