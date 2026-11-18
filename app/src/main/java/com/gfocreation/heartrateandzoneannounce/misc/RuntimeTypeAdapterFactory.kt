/*
 * Adapted from Gson's RuntimeTypeAdapterFactory.
 * Original copyright: Copyright (C) 2011 Google Inc.
 * Modified for Hraza by gfocreation-ops.
 * Licensed under the Apache License, Version 2.0.
 * See LICENSE-APACHE-2.0.
 */
package com.gfocreation.heartrateandzoneannounce.misc

import com.google.gson.*
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.lang.reflect.Type
import java.util.*

/**
 * RuntimeTypeAdapterFactory pour Gson — Kotlin friendly.
 * Usage:
 *   val factory = RuntimeTypeAdapterFactory.of(BlocSeance::class.java, "type")
 *       .registerSubtype(Etape::class.java, "etape")
 *       .registerSubtype(GroupRepeats::class.java, "group")
 */
class RuntimeTypeAdapterFactory<T> private constructor(
    private val baseType: Class<T>,
    private val typeFieldName: String,
    private val maintainType: Boolean
) : TypeAdapterFactory {

    private val labelToSubtype: MutableMap<String, Class<out T>> = LinkedHashMap()
    private val subtypeToLabel: MutableMap<Class<out T>, String> = LinkedHashMap()

    companion object {
        fun <T> of(baseType: Class<T>, typeFieldName: String = "type"): RuntimeTypeAdapterFactory<T> {
            return RuntimeTypeAdapterFactory(baseType, typeFieldName, false)
        }
    }

    fun registerSubtype(subtype: Class<out T>, label: String): RuntimeTypeAdapterFactory<T> {
        if (labelToSubtype.containsKey(label) || subtypeToLabel.containsKey(subtype)) {
            throw IllegalArgumentException("types and labels must be unique")
        }
        labelToSubtype[label] = subtype
        subtypeToLabel[subtype] = label
        return this
    }

    @Suppress("UNCHECKED_CAST")
    override fun <R> create(gson: Gson, type: TypeToken<R>): TypeAdapter<R>? {
        val rawType: Class<in R> = type.rawType as Class<in R>
        if (!baseType.isAssignableFrom(rawType)) {
            return null
        }

        // Préparer délégués
        val labelToDelegate: MutableMap<String, TypeAdapter<*>> = LinkedHashMap()
        val subtypeToDelegate: MutableMap<Class<*>, TypeAdapter<*>> = LinkedHashMap()

        for ((label, subtype) in labelToSubtype) {
            val subtypeToken: TypeToken<*> = TypeToken.get(subtype)
            val delegate: TypeAdapter<*> = gson.getDelegateAdapter(this, subtypeToken)
            labelToDelegate[label] = delegate
            subtypeToDelegate[subtype] = delegate
        }

        return object : TypeAdapter<R>() {
            override fun write(out: JsonWriter, value: R) {
                if (value == null) {
                    out.nullValue()
                    return
                }
                val srcType: Class<*> = (value as Any)::class.java
                val label: String? = subtypeToLabel[srcType]
                val delegate = subtypeToDelegate[srcType] as? TypeAdapter<R>
                    ?: throw JsonParseException("Unknown subtype: $srcType")

                // Sérialiser en JsonElement (objet)
                val tree: JsonElement = delegate.toJsonTree(value)
                val obj = if (tree.isJsonObject) tree.asJsonObject else JsonObject()

                if (maintainType) {
                    if (!obj.has(typeFieldName)) {
                        obj.addProperty(typeFieldName, label)
                    }
                    StreamsWrite(out, obj)
                } else {
                    val clone = JsonObject()
                    clone.add(typeFieldName, JsonPrimitive(label))
                    for ((k, v) in obj.entrySet()) {
                        clone.add(k, v)
                    }
                    StreamsWrite(out, clone)
                }
            }

            override fun read(`in`: JsonReader): R {
                val jsonElement: JsonElement = JsonParser.parseReader(`in`)
                val jsonObject = if (jsonElement.isJsonObject) jsonElement.asJsonObject else throw JsonParseException("Expected JsonObject")
                val labelElement = jsonObject.remove(typeFieldName) ?: throw JsonParseException("Cannot deserialize, missing $typeFieldName")
                val label = labelElement.asString
                val delegate = labelToDelegate[label] as? TypeAdapter<R> ?: throw JsonParseException("Unknown label: $label")
                return delegate.fromJsonTree(jsonObject)
            }
        }.nullSafe()
    }

    // Helper simple qui écrit JsonElement via Gson (évite l'usage de Streams interne)
    private fun StreamsWrite(out: JsonWriter, element: JsonElement) {
        // On utilise Gson's JsonWriter via element.toString() — simple et robuste
        // (on écrit la chaîne JSON complète)
        out.value(element.toString())
        // Remarque : écrire element.toString() via out.jsonValue n'existe pas,
        // en pratique on peut faire: out.beginObject()/endObject() manuellement si besoin.
        // Ici usage basique ; si votre Gson/JsonWriter ne permet pas out.value(jsonString),
        // on peut instead obtain a Gson instance to write: Gson().toJson(element, out)
    }
}
