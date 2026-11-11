package com.surveysolutions.analytics.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.surveysolutions.analytics.store.TabularExportParser.AnswerState;
import com.surveysolutions.analytics.store.TabularExportParser.InterviewRecord;
import com.surveysolutions.analytics.store.TabularExportParser.UnitRecord;

class TabularExportParserTest {
    private static final UUID INTERVIEW_ID = UUID.fromString("6513786b-9ed1-4dea-b9cd-f1edd247f6de");
    private static final String RAW_ID = "6513786b9ed14deab9cdf1edd247f6de";

    @TempDir
    Path directory;

    private final List<InterviewRecord> interviews = new ArrayList<>();
    private final List<UnitRecord> units = new ArrayList<>();
    private final TabularExportParser.Sink sink = new TabularExportParser.Sink() {
        @Override
        public void interview(InterviewRecord interview) {
            interviews.add(interview);
        }

        @Override
        public void unit(UnitRecord unit) {
            units.add(unit);
        }
    };

    @Test
    void readsInterviewsAndDistinguishesAnsweredMissingAndDisabled() throws IOException {
        Path zip = zip(Map.of(
                "survey.tab", "﻿interview__key\tinterview__id\tage\tnom\tville\tsexe\tsssys_irnd\thas__errors\tinterview__status\tassignment__id\r\n"
                        + "01-50-90-18\t" + RAW_ID + "\t34\t##N/A##\t\t1\t0.5\t0\t130\t3\r\n",
                "interview__diagnostics.tab", "﻿interview__key\tinterview__id\tinterview__status\tinterviewers\trejections__sup\trejections__hq\tentities__errors\tquestions__comments\tinterview__duration\tn_questions_unanswered\r\n"
                        + "01-50-90-18\t" + RAW_ID + "\t130\t1\t2\t0\t4\t0\t01.00:05:34\t7\r\n",
                "interview__actions.tab", "interview__key\tinterview__id\tdate\r\n01-50-90-18\t" + RAW_ID + "\t2026-11-04\r\n"));

        new TabularExportParser().parse(zip, Map.of(), "survey", sink);

        assertThat(interviews).containsExactly(new InterviewRecord(
                INTERVIEW_ID, "01-50-90-18", "ApprovedByHeadquarters", 3, false, 4, 2, 0, 1,
                86_400L + 334L, 7, null, null, null, null));
        assertThat(units).singleElement().satisfies(unit -> {
            assertThat(unit.level()).isEqualTo("survey");
            assertThat(unit.rowKey()).isEmpty();
            assertThat(unit.answers()).containsExactly(
                    Map.entry("age", new AnswerState("A", "34")),
                    Map.entry("nom", new AnswerState("M", null)),
                    Map.entry("ville", new AnswerState("D", null)),
                    Map.entry("sexe", new AnswerState("A", "1")));
        });
    }

    @Test
    void groupsMultipleChoiceColumnsAndReadsRosterRows() throws IOException {
        Path zip = zip(Map.of(
                "survey.tab", "interview__key\tinterview__id\tbiens__1\tbiens__2\tbiens__3\tautres__1\tautres__2\tinterview__status\r\n"
                        + "01\t" + RAW_ID + "\t1\t0\t1\t\t\t100\r\n",
                "MEMBRES.tab", "interview__key\tinterview__id\tMEMBRES__id\tage\tlangues__0\tlangues__1\tpos__Latitude\tpos__Longitude\r\n"
                        + "01\t" + RAW_ID + "\t1\t30\twolof\t##N/A##\t14.7\t-17.4\r\n"
                        + "01\t" + RAW_ID + "\t2\t-999999999\t\t\t\t\r\n"
                        + "02\t00000000000000000000000000000009\t1\t40\t\t\t\t\r\n"));

        new TabularExportParser().parse(zip, Map.of(
                "biens", "MultyOption", "autres", "MultyOption",
                "langues", "TextList", "pos", "GPS"), "survey", sink);

        assertThat(interviews).singleElement()
                .satisfies(interview -> assertThat(interview.status()).isEqualTo("Completed"));
        assertThat(units).hasSize(3);
        assertThat(units.get(0).answers()).containsEntry("biens", new AnswerState("A", "1, 3"))
                .containsEntry("autres", new AnswerState("D", null));
        assertThat(units.get(1).level()).isEqualTo("MEMBRES");
        assertThat(units.get(1).rowKey()).isEqualTo("1");
        assertThat(units.get(1).answers())
                .containsEntry("age", new AnswerState("A", "30"))
                .containsEntry("langues", new AnswerState("A", "wolof"))
                .containsEntry("pos", new AnswerState("A", "14.7, -17.4"))
                .doesNotContainKey("MEMBRES");
        assertThat(units.get(2).answers())
                .containsEntry("age", new AnswerState("M", null))
                .containsEntry("langues", new AnswerState("D", null));
    }

    @Test
    void derivesInterviewerAndKeyDatesFromTheActionHistory() throws IOException {
        String actions = "\uFEFFinterview__key\tinterview__id\tdate\ttime\taction\toriginator\trole\tresponsible__name\tresponsible__role\r\n"
                + "01\t" + RAW_ID + "\t2026-11-04\t11:58:01\t12\tenq1\t1\tenq1\t1\r\n"
                + "01\t" + RAW_ID + "\t2026-11-05\t08:42:20\t6\tadmin\t4\tenq2\t1\r\n"
                + "01\t" + RAW_ID + "\t2026-11-04\t12:03:36\t3\tenq1\t1\tsup1\t2\r\n"
                + "01\t" + RAW_ID + "\t2026-11-04\t15:00:00\t3\tenq1\t1\tsup1\t2\r\n";
        Path zip = zip(Map.of(
                "survey.tab", "interview__key\tinterview__id\tinterview__status\r\n01\t" + RAW_ID + "\t130\r\n",
                "interview__actions.tab", actions));

        new TabularExportParser().parse(zip, Map.of(), "survey", sink);

        assertThat(interviews).singleElement().satisfies(interview -> {
            assertThat(interview.interviewerName()).isEqualTo("enq1");
            assertThat(interview.createdAt()).isEqualTo(java.time.Instant.parse("2026-11-04T11:58:01Z"));
            assertThat(interview.completedAt()).isEqualTo(java.time.Instant.parse("2026-11-04T12:03:36Z"));
            assertThat(interview.lastActionAt()).isEqualTo(java.time.Instant.parse("2026-11-05T08:42:20Z"));
        });
    }

    @Test
    void fallsBackToTheLastInterviewerResponsibleWhenNeverCompleted() throws IOException {
        String actions = "interview__key\tinterview__id\tdate\ttime\taction\toriginator\trole\tresponsible__name\tresponsible__role\r\n"
                + "01\t" + RAW_ID + "\t2026-11-04\t10:00:00\t1\tsup1\t2\tenq1\t1\r\n"
                + "01\t" + RAW_ID + "\t2026-11-06\t10:00:00\t1\tsup1\t2\tenq3\t1\r\n";
        Path zip = zip(Map.of(
                "survey.tab", "interview__key\tinterview__id\tinterview__status\r\n01\t" + RAW_ID + "\t60\r\n",
                "interview__actions.tab", actions));

        new TabularExportParser().parse(zip, Map.of(), "survey", sink);

        assertThat(interviews).singleElement().satisfies(interview -> {
            assertThat(interview.interviewerName()).isEqualTo("enq3");
            assertThat(interview.completedAt()).isNull();
            assertThat(interview.status()).isEqualTo("InterviewerAssigned");
        });
    }

    @Test
    void ordersRankedMultipleChoiceByRank() {
        AnswerState state = TabularExportParser.normalize(
                List.of(new String[] {"1", "2"}, new String[] {"2", "0"}, new String[] {"3", "1"}),
                "MultyOption");

        assertThat(state).isEqualTo(new AnswerState("A", "3, 1"));
    }

    @Test
    void parsesDurationsWithAndWithoutDays() {
        assertThat(TabularExportParser.duration("00:05:34")).isEqualTo(334L);
        assertThat(TabularExportParser.duration("2.01:00:00")).isEqualTo(2 * 86_400L + 3_600L);
        assertThat(TabularExportParser.duration("")).isNull();
    }

    private Path zip(Map<String, String> files) throws IOException {
        Path zip = directory.resolve("export.zip");
        try (OutputStream output = Files.newOutputStream(zip);
                ZipOutputStream archive = new ZipOutputStream(output)) {
            for (Map.Entry<String, String> file : files.entrySet()) {
                archive.putNextEntry(new ZipEntry(file.getKey()));
                archive.write(file.getValue().getBytes(StandardCharsets.UTF_8));
                archive.closeEntry();
            }
            archive.putNextEntry(new ZipEntry("Questionnaire/content.zip"));
            archive.closeEntry();
        }
        return zip;
    }
}
