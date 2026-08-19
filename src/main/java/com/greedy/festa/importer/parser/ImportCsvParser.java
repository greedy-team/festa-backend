package com.greedy.festa.importer.parser;

import com.greedy.festa.global.exception.FestaException;
import com.greedy.festa.importer.exception.ImportErrorCode;
import com.greedy.festa.importer.model.ImportSection;
import com.greedy.festa.importer.model.ParsedCsvRow;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ImportCsvParser {

    static final long MAX_FILE_SIZE = 5L * 1024 * 1024;

    public List<ParsedCsvRow> parse(MultipartFile file, ImportSection section) {
        byte[] bytes = read(file);
        if (bytes.length == 0) {
            throw new FestaException(ImportErrorCode.IMPORT_EMPTY_CSV);
        }

        String csv = decodeUtf8(bytes);
        if (!csv.isEmpty() && csv.charAt(0) == '\uFEFF') {
            csv = csv.substring(1);
        }

        try (CSVParser parser = CSVParser.parse(csv, CSVFormat.RFC4180.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .get())) {
            validateHeader(parser.getHeaderNames(), section);
            List<ParsedCsvRow> rows = new ArrayList<>();
            int line = 1;
            for (CSVRecord record : parser) {
                if (!record.isConsistent()) {
                    throw new FestaException(ImportErrorCode.IMPORT_INVALID_CSV);
                }
                Map<String, String> values = new LinkedHashMap<>();
                for (String header : section.headers()) {
                    values.put(header, record.get(header));
                }
                rows.add(new ParsedCsvRow(line++, Map.copyOf(values)));
            }
            if (rows.isEmpty()) {
                throw new FestaException(ImportErrorCode.IMPORT_EMPTY_CSV);
            }
            return List.copyOf(rows);
        } catch (FestaException e) {
            throw e;
        } catch (IOException | UncheckedIOException | IllegalArgumentException e) {
            throw new FestaException(ImportErrorCode.IMPORT_INVALID_CSV);
        }
    }

    private byte[] read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FestaException(ImportErrorCode.IMPORT_EMPTY_CSV);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new FestaException(ImportErrorCode.PAYLOAD_TOO_LARGE);
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new FestaException(ImportErrorCode.IMPORT_INVALID_CSV);
        }
    }

    private String decodeUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new FestaException(ImportErrorCode.IMPORT_INVALID_CSV_ENCODING);
        }
    }

    private void validateHeader(List<String> actual, ImportSection section) {
        if (!actual.equals(section.headers())) {
            throw new FestaException(ImportErrorCode.IMPORT_INVALID_CSV_HEADER);
        }
    }
}
