package com.surveysolutions.analytics.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class InterviewFilterTest {
    @Test
    void excludesDeletedInterviewsWhenNoStatusIsRequested() {
        InterviewFilter.Sql sql = new InterviewFilter(null, null, null, null, null, null, null).toSql();

        assertThat(sql.clause()).isEqualTo(" AND i.status <> 'Deleted'");
        assertThat(sql.args()).isEmpty();
    }

    @Test
    void buildsParameterizedClausesInDeclarationOrder() {
        InterviewFilter.Sql sql = new InterviewFilter(
                List.of("Completed"), LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-03"),
                "sup1", "enq1", "region", "2").toSql();

        assertThat(sql.clause())
                .contains("i.status = ANY (?)", "i.completed_at >= ?", "i.completed_at < ?",
                        "i.interviewer_name = ?", "tf.supervisor_name = ?", "fu.answers -> ? ->> 'v' = ?")
                .doesNotContain("sup1", "region");
        assertThat(sql.args()).hasSize(7);
        assertThat(sql.args().get(1)).isEqualTo(OffsetDateTime.parse("2026-11-01T00:00Z"));
        assertThat(sql.args().get(2)).isEqualTo(OffsetDateTime.parse("2026-11-04T00:00Z"));
        assertThat(sql.args().subList(3, 7)).containsExactly("enq1", "sup1", "region", "2");
    }

    @Test
    void ignoresBlankValuesAndKeepsExplicitStatuses() {
        InterviewFilter filter = new InterviewFilter(List.of(" ", ""), null, null, " ", "", null, null);

        assertThat(filter.statuses()).isNull();
        assertThat(filter.narrowsPopulation()).isFalse();
        assertThat(filter.withDefaultStatuses(List.of("Completed")).statuses()).containsExactly("Completed");
        assertThat(InterviewFilter.withStatuses(List.of("Restarted")).withDefaultStatuses(List.of("Completed"))
                .statuses()).containsExactly("Restarted");
    }

    @Test
    void rejectsInconsistentOrUnsafeFilters() {
        assertThatThrownBy(() -> new InterviewFilter(null, LocalDate.parse("2026-11-05"),
                LocalDate.parse("2026-11-01"), null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InterviewFilter(null, null, null, null, null, "region", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InterviewFilter(null, null, null, null, null, "x'); DROP", "1"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
