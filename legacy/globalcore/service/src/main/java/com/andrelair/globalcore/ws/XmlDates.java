package com.andrelair.globalcore.ws;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** LocalDate/LocalDateTime ↔ XMLGregorianCalendar (the type the XSD-generated JAXB classes use). */
final class XmlDates {

    private static final DatatypeFactory DF;
    static {
        try {
            DF = DatatypeFactory.newInstance();
        } catch (DatatypeConfigurationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private XmlDates() {}

    static XMLGregorianCalendar date(LocalDate d) {
        if (d == null) return null;
        return DF.newXMLGregorianCalendarDate(d.getYear(), d.getMonthValue(), d.getDayOfMonth(),
                javax.xml.datatype.DatatypeConstants.FIELD_UNDEFINED);
    }

    static XMLGregorianCalendar dateTime(LocalDateTime t) {
        if (t == null) return null;
        return DF.newXMLGregorianCalendar(
                t.getYear(), t.getMonthValue(), t.getDayOfMonth(),
                t.getHour(), t.getMinute(), t.getSecond(), 0,
                javax.xml.datatype.DatatypeConstants.FIELD_UNDEFINED);
    }

    static LocalDate toLocalDate(XMLGregorianCalendar c) {
        return LocalDate.of(c.getYear(), c.getMonth(), c.getDay());
    }
}
