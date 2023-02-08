package com.varian.mappercore.client

import com.quovadx.cloverleaf.upoc.PropertyTree

class HL7EncodeCharacterManager (
    private val fieldSeparator: String,
    private val escapeSequence: String,
    private val repetitionSeparator: String,
    private val componentSeparator: String,
    private val subcomponentSeparator: String,
    var hl7EncodedCharMap: HashMap<String, String>
): IHL7EncodeCharacterManager {
    override fun getEncodeCharacters(userdata: PropertyTree): HashMap<String, String> {
        hl7EncodedCharMap[this.fieldSeparator] =
            if (userdata.get("FIELD_SEPARATOR").toString()=="\\") "\\\\" else userdata.get("FIELD_SEPARATOR").toString()
        hl7EncodedCharMap[this.componentSeparator]=
            if (userdata.get("COMPONENT_SEPARATOR").toString()=="\\") "\\\\" else userdata.get("COMPONENT_SEPARATOR").toString()
        hl7EncodedCharMap[this.repetitionSeparator] =
            if (userdata.get("REPETITION_SEPARATOR").toString()=="\\") "\\\\" else userdata.get("REPETITION_SEPARATOR").toString()
        hl7EncodedCharMap[this.escapeSequence] =
            if (userdata.get("ESCAPE_SEQUENCE").toString()=="\\") "\\\\" else userdata.get("ESCAPE_SEQUENCE").toString()
        hl7EncodedCharMap[this.subcomponentSeparator] =
            if (userdata.get("SUBCOMPONENT_SEPARATOR").toString()=="\\") "\\\\" else userdata.get("SUBCOMPONENT_SEPARATOR").toString()

        return hl7EncodedCharMap
    }

    override fun decodeHl7Message(message: String, userdata: PropertyTree): String {
        var replacedString = message
        hl7EncodedCharMap = this.getEncodeCharacters(userdata)
        for (entry in hl7EncodedCharMap.entries) {
            replacedString = replacedString.replace(entry.key, entry.value)
        }
        return replacedString
    }
}