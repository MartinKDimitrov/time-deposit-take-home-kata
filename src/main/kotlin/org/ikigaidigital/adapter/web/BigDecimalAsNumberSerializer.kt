package org.ikigaidigital.adapter.web

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal

/** Writes money as a JSON number with its exact digits (1000.50 stays 1000.50) instead of going through Double. */
@OptIn(ExperimentalSerializationApi::class)
object BigDecimalAsNumberSerializer : KSerializer<BigDecimal> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("java.math.BigDecimal", PrimitiveKind.DOUBLE)

    override fun serialize(encoder: Encoder, value: BigDecimal) =
        (encoder as JsonEncoder).encodeJsonElement(JsonUnquotedLiteral(value.toPlainString()))

    override fun deserialize(decoder: Decoder): BigDecimal =
        BigDecimal((decoder as JsonDecoder).decodeJsonElement().jsonPrimitive.content)
}