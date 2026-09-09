package com.evergarden.evergardenbackend.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ErrorCode}가 {@code docs/error-codes.md}와 어긋나지 않는지 확인한다.
 *
 * <p>오류 코드 표는 API 명세를 쓸 때의 근거 문서다. 코드만 추가하고 문서를 두면
 * 명세에 없는 코드가 응답으로 나가고, 문서만 고치면 구현이 못 따라간다.
 * 둘 중 하나만 바꾸면 이 테스트가 깨진다.
 */
class ErrorCodeDocumentSyncTest {

    private static final Path DOC = Path.of("docs/error-codes.md");

    /** 표의 절 번호 → HTTP 상태. 8절은 행마다 상태가 달라 따로 읽는다. */
    private static final Map<String, Integer> SECTION_STATUS = Map.of(
            "3", 400, "4", 401, "5", 403, "6", 404, "7", 409);

    @Test
    @DisplayName("문서의 오류 코드와 enum이 정확히 일치한다")
    void 문서와_enum이_일치한다() throws IOException {
        Map<String, Integer> documented = readDocument();

        Map<String, Integer> declared = new LinkedHashMap<>();
        for (ErrorCode code : ErrorCode.values()) {
            declared.put(code.name(), code.getStatus().value());
        }

        assertThat(declared.keySet())
                .as("enum에만 있거나 문서에만 있는 코드가 없어야 한다")
                .containsExactlyInAnyOrderElementsOf(documented.keySet());

        assertThat(declared)
                .as("코드마다 HTTP 상태가 문서와 같아야 한다")
                .containsAllEntriesOf(documented);
    }

    @Test
    @DisplayName("모든 코드에 사용자에게 보여줄 메시지가 있다")
    void 모든_코드에_메시지가_있다() {
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(code.getMessage())
                    .as("%s에 메시지가 없다", code)
                    .isNotBlank();
        }
    }

    private Map<String, Integer> readDocument() throws IOException {
        Pattern section = Pattern.compile("^## (\\d+)\\. ");
        Pattern row = Pattern.compile("^\\| `([A-Z][A-Z_]+)` \\| (.*)");
        Map<String, Integer> found = new LinkedHashMap<>();
        String current = null;

        for (String line : Files.readAllLines(DOC)) {
            Matcher s = section.matcher(line);
            if (s.find()) {
                current = s.group(1);
                continue;
            }
            Matcher r = row.matcher(line);
            if (!r.find() || current == null) {
                continue;
            }
            if (SECTION_STATUS.containsKey(current)) {
                found.put(r.group(1), SECTION_STATUS.get(current));
            } else if (current.equals("8")) {
                // 8절은 첫 칸에 상태가 적혀 있다 — 예: `500`
                String cell = r.group(2).split("\\|")[0].replaceAll("[^0-9]", "");
                found.put(r.group(1), Integer.parseInt(cell));
            }
        }
        return found;
    }
}
