package com.example.smartfinassistant.rag;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.smartfinassistant.responsecode.ResponseCode;
import com.example.smartfinassistant.responsecode.ResponseCodeRepository;
import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

class ResponseCodeIngestionRunnerTest {

    private ResponseCodeCatalog catalog;
    private ResponseCodeRepository repository;
    private VectorStore vectorStore;
    private JdbcTemplate jdbcTemplate;
    private ResponseCodeIngestionRunner runner;

    @BeforeEach
    void setUp() {
        catalog = new ResponseCodeCatalog();
        repository = mock(ResponseCodeRepository.class);
        vectorStore = mock(VectorStore.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        runner = new ResponseCodeIngestionRunner(catalog, repository, vectorStore, jdbcTemplate);
        when(repository.findAll()).thenReturn(databaseCodes(catalog.chunks()));
    }

    @Test
    void firstRunAddsAllThirteenDocuments() {
        runner.run(mock(ApplicationArguments.class));

        verify(vectorStore).add(org.mockito.ArgumentMatchers.argThat(documents -> documents.size() == 13));
        verify(vectorStore, never()).delete(org.mockito.ArgumentMatchers.<List<String>>any());
    }

    @Test
    void unchangedSecondRunMakesNoVectorWrites() throws Exception {
        List<Document> documents = catalog.documents();
        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(1);
            for (Document document : documents) {
                ResultSet resultSet = mock(ResultSet.class);
                when(resultSet.getString(1)).thenReturn(document.getId());
                when(resultSet.getString(2)).thenReturn(document.getMetadata().get("rc").toString());
                when(resultSet.getString(3)).thenReturn(document.getMetadata().get("content_hash").toString());
                handler.processRow(resultSet);
            }
            return null;
        }).when(jdbcTemplate).query(anyString(), any(RowCallbackHandler.class), any(Object[].class));

        runner.run(mock(ApplicationArguments.class));

        verify(vectorStore, never()).add(org.mockito.ArgumentMatchers.<List<Document>>any());
        verify(vectorStore, never()).delete(org.mockito.ArgumentMatchers.<List<String>>any());
    }

    @Test
    void documentDatabaseMismatchFailsBeforeVectorWrites() {
        List<ResponseCode> rows = databaseCodes(catalog.chunks());
        rows.getFirst().setMeaning("Nội dung sai");
        when(repository.findAll()).thenReturn(rows);

        assertThatThrownBy(() -> runner.run(mock(ApplicationArguments.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("consistency check failed");
        verify(vectorStore, never()).add(org.mockito.ArgumentMatchers.<List<Document>>any());
    }

    private List<ResponseCode> databaseCodes(List<ResponseCodeChunk> chunks) {
        return chunks.stream().map(chunk -> {
            ResponseCode row = new ResponseCode();
            row.setRc(chunk.rc());
            row.setMeaning(chunk.meaning());
            row.setHandling(chunk.handling());
            return row;
        }).toList();
    }
}
