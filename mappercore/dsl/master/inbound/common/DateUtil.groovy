package master.inbound.common
//Todo move to common
def class DateUtil {
    static def getDateType(value) {
        def dateType = null
        if (value instanceof DateType || value == null) {
            dateType = value
        } else if (value instanceof Date) {
            def dateTypeValue = new DateType()
            dateTypeValue.value = value as Date
            dateType = getDateType(dateTypeValue)
        } else if (value instanceof StringType) {
            def dateValue = parseToDate(value.toString())
            dateType = getDateType(dateValue)
        } else if (value instanceof String) {
            def dateValue = parseToDate(value)
            dateType = getDateType(dateValue)
        }
        return dateType
    }

    def static parseToDate(String value) {
        def inputDateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
        return inputDateFormat.parse(value)
    }
}
