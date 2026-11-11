package com.surveysolutions.analytics.questions;

import static com.surveysolutions.analytics.surveysolutions.SurveySolutionsDtos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.surveysolutions.analytics.surveysolutions.SurveySolutionsService;
import com.surveysolutions.analytics.config.HqApiUserPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class DataExportServiceTest {
    private static final UUID QUESTIONNAIRE_ID =
            UUID.fromString("588daf2d-0c72-4b89-bec9-254459a68983");
    private static final UUID INTERVIEW_ID =
            UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

    private final SurveySolutionsService surveySolutions = mock(SurveySolutionsService.class);
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private DataExportService service;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new HqApiUserPrincipal("api-user", "secret"), null, List.of()));
        service = new DataExportService(surveySolutions, executor, objectMapper);
        when(surveySolutions.getQuestions(QUESTIONNAIRE_ID, 1)).thenReturn(List.of(
                new Question("age-id", "age", "Age", "Age of respondent", "Numeric", List.of("Household"), List.of()),
                new Question("gps-id", "location", "Location", "Location", "GPS", List.of("Household"), List.of())));
        when(surveySolutions.getInterviews(QUESTIONNAIRE_ID, 1L)).thenReturn(List.of(
                new Interview(INTERVIEW_ID.toString(), QUESTIONNAIRE_ID.toString(), 1, null,
                        "Completed", "2026-11-03T10:38:49Z")));
        InterviewAnswer numericAnswer = new InterviewAnswer("age", "=1+1");
        InterviewAnswer gpsAnswer = new InterviewAnswer(
                "location", objectMapper.createObjectNode().put("latitude", 48.8).put("longitude", 2.3));
        gpsAnswer.addAdditionalField("RosterVector", objectMapper.valueToTree(List.of(1, 2)));
        when(surveySolutions.getInterviewDetails(INTERVIEW_ID))
                .thenReturn(new InterviewDetails(List.of(numericAnswer, gpsAnswer)));
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
        SecurityContextHolder.clearContext();
    }

    @Test
    void csvKeepsEveryAnswerAndProtectsSpreadsheetFormulaValues() {
        DataExportService.ExportFile file =
                service.export(QUESTIONNAIRE_ID, 1, DataExportService.Format.CSV);
        String csv = new String(file.content(), StandardCharsets.UTF_8);

        assertThat(file.filename()).endsWith(".csv");
        assertThat(csv).startsWith("\uFEFF\"N° interview\"");
        assertThat(csv).contains("\"'=1+1\"");
        assertThat(csv).contains("\"Donnée structurée\"");
        assertThat(csv).contains("Latitude : 48.8 ; Longitude : 2.3");
        assertThat(csv).contains("Roster Vector : 1 ; 2");
        assertThat(csv).doesNotContain("{\"latitude\"");
        assertThat(csv.split("\r\n", -1)).hasSize(4);
    }

    @Test
    void excelKeepsAnswersAsTextAndIncludesTheirAdditionalMetadata() throws Exception {
        DataExportService.ExportFile file =
                service.export(QUESTIONNAIRE_ID, 1, DataExportService.Format.XLSX);

        assertThat(file.filename()).endsWith(".xlsx");
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(file.content()))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(3);
            assertThat(sheet.getRow(1).getCell(7).getStringCellValue()).isEqualTo("=1+1");
            assertThat(sheet.getRow(2).getCell(7).getStringCellValue())
                    .isEqualTo("Latitude : 48.8 ; Longitude : 2.3");
            assertThat(sheet.getRow(2).getCell(9).getStringCellValue())
                    .isEqualTo("Roster Vector : 1 ; 2");
        }
    }

    @Test
    void rejectsUnsupportedFormats() {
        org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> DataExportService.Format.parse("sav"));
    }
}
