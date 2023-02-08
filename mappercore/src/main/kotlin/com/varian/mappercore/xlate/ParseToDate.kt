package com.varian.mappercore.xlate

import com.quovadx.cloverleaf.upoc.CloverEnv
import com.quovadx.cloverleaf.upoc.CloverleafException
import com.quovadx.cloverleaf.upoc.XLTString
import com.quovadx.cloverleaf.upoc.Xpm
import com.varian.mappercore.constant.XlateConstant
import com.varian.mappercore.constant.XlateConstant.ACTIVE_NULL_LITERAL
import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat

class ParseToDate : XLTString() {
    @Throws(CloverleafException::class)
    override fun xlateString(cloverEnv: CloverEnv, xpm: Xpm, inputValue: String?): Any {
        var inputDateFormat: SimpleDateFormat? = null
        var targetDateFormat: DateFormat? = null
        var fhirDate: String? = null
        if (inputValue != null && inputValue.isNotEmpty()) {
            if (inputValue == ACTIVE_NULL_LITERAL) {
                fhirDate = ACTIVE_NULL_LITERAL
            } else if (inputValue.length == 8) {
                inputDateFormat = SimpleDateFormat(XlateConstant.HL7_DATE_FORMAT)
                targetDateFormat = SimpleDateFormat(XlateConstant.FHIR_DATE_FORMAT)
            } else if (inputValue.length == 14) {
                inputDateFormat = SimpleDateFormat(XlateConstant.HL7_DATETIME_FORMAT)
                targetDateFormat = SimpleDateFormat(XlateConstant.FHIR_DATETIME_FORMAT)
            } else if (inputValue.length == 24) {
                inputDateFormat = SimpleDateFormat(XlateConstant.HL7_DATETIME_LOCAL_FORMAT)
                targetDateFormat = SimpleDateFormat(XlateConstant.FHIR_DATETIME_FORMAT)
            } else {
                cloverEnv.log(
                    1, """Invalid Date format for inputValue: '$inputValue'. 
                          Expected Date Formats: ${XlateConstant.HL7_DATE_FORMAT}, ${XlateConstant.HL7_DATETIME_FORMAT}
                        """.trimIndent()
                )
            }
        }
        if (inputDateFormat != null) {
            try {
                fhirDate = targetDateFormat!!.format(inputDateFormat.parse(inputValue))
            } catch (e: ParseException) {

            }
        }
        return fhirDate ?: ""
    }
}
