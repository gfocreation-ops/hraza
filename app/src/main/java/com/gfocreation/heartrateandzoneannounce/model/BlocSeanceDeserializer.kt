package com.gfocreation.heartrateandzoneannounce.model

import com.google.gson.*

class BlocSeanceDeserializer : JsonDeserializer<BlocSeance> {

    override fun deserialize(
        json: JsonElement,
        typeOfT: java.lang.reflect.Type,
        context: JsonDeserializationContext
    ): BlocSeance {

        val obj = json.asJsonObject

        val typeStr = obj.get("blocType")?.asString
            ?: throw JsonParseException("Missing blocType")

        return when (BlocType.valueOf(typeStr)) {
            BlocType.ETAPE ->
                context.deserialize(obj, Etape::class.java)

            BlocType.GROUP ->
                context.deserialize(obj, GroupRepeats::class.java)
        }
    }
}