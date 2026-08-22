package com.greedy.festa.importer.parser;

import com.greedy.festa.global.exception.FestaException;
import com.greedy.festa.global.exception.CommonErrorCode;
import com.greedy.festa.importer.exception.ImportErrorCode;
import com.greedy.festa.importer.model.ImportSection;
import com.greedy.festa.importer.model.ParsedCsvRow;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

@SuppressWarnings("NonAsciiCharacters")
class ImportCsvParserTest {

    private final ImportCsvParser parser = new ImportCsvParser();

    @Test
    void UTF8_BOM과_quoted_comma_escaped_quote_multiline을_파싱한다() {
        String csv = "\uFEFF" + String.join(",", ImportSection.ARTISTS.headers()) + "\r\n"
                + "\"밴드, 이름\",\"별칭 \"\"A\"\"|별칭B\",BAND,https://example.com/a.jpg,"
                + "\"true\r\n\"";

        List<ParsedCsvRow> rows = parser.parse(file("artists.csv", csv), ImportSection.ARTISTS);

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().line()).isEqualTo(1);
        assertThat(rows.getFirst().values().get("name")).isEqualTo("밴드, 이름");
        assertThat(rows.getFirst().values().get("other_names")).isEqualTo("별칭 \"A\"|별칭B");
        assertThat(rows.getFirst().values().get("needs_review")).isEqualTo("true\r\n");
    }

    @Test
    void UTF8이_아니면_encoding_오류다() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "artists.csv", "text/csv", new byte[]{(byte) 0xC3, (byte) 0x28});

        FestaException thrown = catchThrowableOfType(
                FestaException.class, () -> parser.parse(file, ImportSection.ARTISTS));

        assertThat(thrown.getErrorCode()).isEqualTo(ImportErrorCode.IMPORT_INVALID_CSV_ENCODING);
    }

    @Test
    void header가_다르면_header_오류다() {
        FestaException thrown = catchThrowableOfType(FestaException.class,
                () -> parser.parse(file("artists.csv", "name,genre\n10CM,BAND\n"),
                        ImportSection.ARTISTS));

        assertThat(thrown.getErrorCode()).isEqualTo(ImportErrorCode.IMPORT_INVALID_CSV_HEADER);
    }

    @Test
    void 빈_파일과_header_only는_empty_오류다() {
        FestaException empty = catchThrowableOfType(FestaException.class,
                () -> parser.parse(new MockMultipartFile("file", new byte[0]),
                        ImportSection.ARTISTS));
        FestaException headerOnly = catchThrowableOfType(FestaException.class,
                () -> parser.parse(file("artists.csv",
                                String.join(",", ImportSection.ARTISTS.headers()) + "\n"),
                        ImportSection.ARTISTS));

        assertThat(empty.getErrorCode()).isEqualTo(ImportErrorCode.IMPORT_EMPTY_CSV);
        assertThat(headerOnly.getErrorCode()).isEqualTo(ImportErrorCode.IMPORT_EMPTY_CSV);
    }

    @Test
    void 파일이_5MB를_초과하면_payload_too_large다() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "artists.csv", "text/csv", new byte[5 * 1024 * 1024 + 1]);

        FestaException thrown = catchThrowableOfType(
                FestaException.class, () -> parser.parse(file, ImportSection.ARTISTS));

        assertThat(thrown.getErrorCode()).isEqualTo(CommonErrorCode.PAYLOAD_TOO_LARGE);
    }

    @Test
    void 닫히지_않은_quote와_열_개수_불일치는_invalid_csv다() {
        String header = String.join(",", ImportSection.ARTISTS.headers());
        FestaException malformed = catchThrowableOfType(FestaException.class,
                () -> parser.parse(file("artists.csv", header + "\n\"닫히지 않음\n"),
                        ImportSection.ARTISTS));
        FestaException inconsistent = catchThrowableOfType(FestaException.class,
                () -> parser.parse(file("artists.csv", header + "\n10CM,BAND\n"),
                        ImportSection.ARTISTS));

        assertThat(malformed.getErrorCode()).isEqualTo(ImportErrorCode.IMPORT_INVALID_CSV);
        assertThat(inconsistent.getErrorCode()).isEqualTo(ImportErrorCode.IMPORT_INVALID_CSV);
    }

    private MockMultipartFile file(String name, String content) {
        return new MockMultipartFile(
                "file", name, "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }
}
