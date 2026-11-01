package bd.ac.mbstu.calendar.util;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvUtilTest {

    @Test
    void simpleRowsAreSplitIntoCells() {
        List<List<String>> rows = CsvUtil.parse("a,b,c\r\n1,2,3\r\n");
        assertEquals(List.of(List.of("a", "b", "c"), List.of("1", "2", "3")), rows);
    }

    @Test
    void theLastLineNeedsNoLineBreak() {
        assertEquals(List.of(List.of("a", "b"), List.of("1", "2")), CsvUtil.parse("a,b\n1,2"));
    }

    @Test
    void emptyCellsAreKept() {
        assertEquals(List.of(List.of("1", "", "3")), CsvUtil.parse("1,,3"));
        assertEquals(List.of(List.of("1", "2", "")), CsvUtil.parse("1,2,"));
    }

    @Test
    void quotedCellsMayContainCommasQuotesAndLineBreaks() {
        List<List<String>> rows = CsvUtil.parse("\"x, y\",\"say \"\"hi\"\"\",\"line1\nline2\"\n");
        assertEquals(List.of(List.of("x, y", "say \"hi\"", "line1\nline2")), rows);
    }

    @Test
    void anInvisibleExcelMarkAtTheStartIsIgnored() {
        assertEquals(List.of(List.of("title", "type")), CsvUtil.parse("\uFEFFtitle,type\n"));
    }

    @Test
    void blankLinesAreSkipped() {
        assertEquals(List.of(List.of("a"), List.of("b")), CsvUtil.parse("a\n\n   \nb\n"));
    }

    @Test
    void emptyInputGivesNoRows() {
        assertTrue(CsvUtil.parse("").isEmpty());
        assertTrue(CsvUtil.parse(null).isEmpty());
    }

    @Test
    void aQuoteThatIsNeverClosedIsAnError() {
        assertThrows(ValidationException.class, () -> CsvUtil.parse("a,\"oops"));
    }

    @Test
    void lineQuotesOnlyWhenNeeded() {
        assertEquals("a,\"b,c\",\"d\"\"e\",", CsvUtil.line(List.of("a", "b,c", "d\"e", "")));
        assertEquals("a,", CsvUtil.line(Arrays.asList("a", null)));
    }

    @Test
    void parsingWhatWeWroteGivesTheSameCells() {
        List<String> cells = List.of("plain", "with, comma", "with \"quote\"", "two\nlines", "");
        List<List<String>> back = CsvUtil.parse(CsvUtil.line(cells) + "\r\n");
        assertEquals(List.of(cells), back);
    }

    @Test
    void cellsThatLookLikeFormulasAreProtected() {
        assertEquals("'=1+1", CsvUtil.protect("=1+1"));
        assertEquals("'+5", CsvUtil.protect("+5"));
        assertEquals("'-5", CsvUtil.protect("-5"));
        assertEquals("'@cmd", CsvUtil.protect("@cmd"));
        assertEquals("hello", CsvUtil.protect("hello"));
        assertEquals("", CsvUtil.protect(null));
        assertEquals("", CsvUtil.protect(""));
    }

    @Test
    void unprotectIsTheExactReverseAndLeavesNormalTextAlone() {
        for (String original : new String[] {"=1+1", "+5", "-5", "@cmd", "hello"}) {
            assertEquals(original, CsvUtil.unprotect(CsvUtil.protect(original)));
        }
        assertEquals("'hello", CsvUtil.unprotect("'hello"), "an ordinary apostrophe is not removed");
        assertEquals("'", CsvUtil.unprotect("'"));
    }
}
