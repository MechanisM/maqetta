package org.davinci.server.util;

import java.math.BigDecimal;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JSONReader {
    private static final String EMPTY_STRING = ""; //$NON-NLS-1$
    private static final String NULL = "null"; //$NON-NLS-1$

    public static Object read(String jsonString) {
        return parse(new StringCharacterIterator(jsonString));
    }

 

    private static RuntimeException error(String message, CharacterIterator it) {
        return new IllegalStateException("[" + it.getIndex() + "] " + message); //$NON-NLS-1$//$NON-NLS-2$
    }

    private static RuntimeException error(String message) {
        return new IllegalStateException(message);
    }

    private static Object parse(CharacterIterator it) {
        parseWhitespace(it);
        Object result = parseValue(it);
        parseWhitespace(it);

        if (it.current() != CharacterIterator.DONE)
            throw error("should be done", it); //$NON-NLS-1$
        return result;
    }

    private static void parseWhitespace(CharacterIterator it) {
        char c = it.current();
        while (Character.isWhitespace(c))
            c = it.next();
    }

    private static Object parseValue(CharacterIterator it) {
        switch (it.current()) {
            case '{' :
                return parseObject(it);
            case '[' :
                return parseArray(it);
            case '"' :
                return parseString(it);
            case '-' :
            case '0' :
            case '1' :
            case '2' :
            case '3' :
            case '4' :
            case '5' :
            case '6' :
            case '7' :
            case '8' :
            case '9' :
                return parseNumber(it);
            case 't' :
                parseText(Boolean.TRUE.toString(), it);
                return Boolean.TRUE;
            case 'f' :
                parseText(Boolean.FALSE.toString(), it);
                return Boolean.FALSE;
            case 'n' :
                parseText(NULL, it);
                return null;
        }
        throw error("Bad JSON starting character '" + it.current() + "'", it); //$NON-NLS-1$ //$NON-NLS-2$;
    }

    private static Map parseObject(CharacterIterator it) {
        it.next();
        parseWhitespace(it);
        if (it.current() == '}') {
            it.next();
            return Collections.EMPTY_MAP;
        }

        Map map = new HashMap();
        while (true) {
            if (it.current() != '"')
                throw error("expected a string start '\"' but was '" + it.current() + "'", it); //$NON-NLS-1$ //$NON-NLS-2$
            String key = parseString(it);
            if (map.containsKey(key))
                throw error("' already defined" + "key '" + key, it); //$NON-NLS-1$ //$NON-NLS-2$
            parseWhitespace(it);
            if (it.current() != ':')
                throw error("expected a pair separator ':' but was '" + it.current() + "'", it); //$NON-NLS-1$ //$NON-NLS-2$
            it.next();
            parseWhitespace(it);
            Object value = parseValue(it);
            map.put(key, value);
            parseWhitespace(it);
            if (it.current() == ',') {
                it.next();
                parseWhitespace(it);
                continue;
            }

            if (it.current() != '}')
                throw error("expected an object close '}' but was '" + it.current() + "'", it); //$NON-NLS-1$ //$NON-NLS-2$
            break;
        }
        it.next();
        return map;
    }

    private static List parseArray(CharacterIterator it) {
        it.next();
        parseWhitespace(it);
        if (it.current() == ']') {
            it.next();
            return Collections.EMPTY_LIST;
        }

        List list = new ArrayList();
        while (true) {
            Object value = parseValue(it);
            list.add(value);
            parseWhitespace(it);
            if (it.current() == ',') {
                it.next();
                parseWhitespace(it);
                continue;
            }

            if (it.current() != ']')
                throw error("expected an array close ']' but was '" + it.current() + "'", it); //$NON-NLS-1$ //$NON-NLS-2$
            break;
        }
        it.next();
        return list;
    }

    private static void parseText(String string, CharacterIterator it) {
        int length = string.length();
        char c = it.current();
        for (int i = 0; i < length; i++) {
            if (c != string.charAt(i))
                throw error("expected to parse '" + string + "' but character " + (i + 1) + " was '" + c + "'", it); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$;
            c = it.next();
        }
    }

    private static Object parseNumber(CharacterIterator it) {
        StringBuffer buffer = new StringBuffer();
        char c = it.current();
        while (Character.isDigit(c) || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') {
            buffer.append(c);
            c = it.next();
        }
        try {
            return new BigDecimal(buffer.toString());
        } catch (NumberFormatException e) {
            throw error("expected a number but was '" + buffer.toString() + "'", it); //$NON-NLS-1$ //$NON-NLS-2$;
        }
    }

    private static String parseString(CharacterIterator it) {
        char c = it.next();
        if (c == '"') {
            it.next();
            return EMPTY_STRING;
        }
        StringBuffer buffer = new StringBuffer();
        while (c != '"') {
            if (Character.isISOControl(c))
                throw error("illegal iso control character: '" + Integer.toHexString(c) + "'", it); //$NON-NLS-1$ //$NON-NLS-2$);

            if (c == '\\') {
                c = it.next();
                switch (c) {
                    case '"' :
                    case '\\' :
                    case '/' :
                        buffer.append(c);
                        break;
                    case 'b' :
                        buffer.append('\b');
                        break;
                    case 'f' :
                        buffer.append('\f');
                        break;
                    case 'n' :
                        buffer.append('\n');
                        break;
                    case 'r' :
                        buffer.append('\r');
                        break;
                    case 't' :
                        buffer.append('\t');
                        break;
                    case 'u' :
                        StringBuffer unicode = new StringBuffer(4);
                        for (int i = 0; i < 4; i++) {
                            unicode.append(it.next());
                        }
                        try {
                            buffer.append((char) Integer.parseInt(unicode.toString(), 16));
                        } catch (NumberFormatException e) {
                            throw error("expected a unicode hex number but was '" + unicode.toString() + "'", it); //$NON-NLS-1$ //$NON-NLS-2$););
                        }
                        break;
                    default :
                        throw error("illegal escape character '" + c + "'", it); //$NON-NLS-1$ //$NON-NLS-2$););
                }
            } else
                buffer.append(c);

            c = it.next();
        }
        c = it.next();
        return buffer.toString();
    }



}
