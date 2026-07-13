package com.example.smartfinassistant.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

class ResponseCodeDocumentParserTest {

    private final ResponseCodeDocumentParser parser = new ResponseCodeDocumentParser();

    @Test
    void parsesAllVerbatimResponseCodes() {
        List<ResponseCodeChunk> chunks = parser.parse(new ClassPathResource("rag/response-codes.md"));

        assertThat(chunks).hasSize(13);
        assertThat(chunks).extracting(ResponseCodeChunk::rc)
                .containsExactly("00", "01", "05", "12", "14", "30", "51", "54", "61", "68", "75", "96", "99");
        assertThat(chunks).filteredOn(chunk -> chunk.rc().equals("51")).singleElement()
                .satisfies(chunk -> {
                    assertThat(chunk.meaning()).isEqualTo("Số dư không đủ (Insufficient Funds)");
                    assertThat(chunk.handling()).isEqualTo(
                            "Tài khoản của khách hàng không đủ tiền để thực hiện giao dịch (hoặc số tiền giao dịch vượt quá số dư khả dụng). Khách hàng cần nạp thêm tiền hoặc kiểm tra lại số dư.");
                });
    }

    @Test
    void rejectsDuplicateCodes() {
        String duplicate = """
                | 51 | Số dư không đủ | Nạp thêm tiền. |
                | 51 | Trùng mã | Không hợp lệ. |
                """;

        assertThatThrownBy(() -> parser.parse(resource(duplicate)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate response code");
    }

    @Test
    void rejectsIncompleteDocument() {
        assertThatThrownBy(() -> parser.parse(resource("| 51 | Số dư không đủ | Nạp thêm tiền. |")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exactly the 13 appendix codes");
    }

    private ByteArrayResource resource(String content) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8));
    }
}
