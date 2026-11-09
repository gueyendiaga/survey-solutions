package com.surveysolutions.analytics.report;

import java.time.Instant;
import java.util.List;
import com.surveysolutions.analytics.fieldwork.FieldworkDtos.FieldworkOverview;
import com.surveysolutions.analytics.questions.QuestionAnalyticsDtos.CompletenessOverview;
import com.surveysolutions.analytics.questions.QuestionAnalyticsDtos.QuestionAnalytics;

/**
 * Données agrégées d'un rapport. Aucune réponse individuelle n'y figure : seuls les comptes,
 * pourcentages et statistiques descriptives sont rendus.
 *
 * @param organization  organisme affiché en en-tête (configurable)
 * @param scope         description lisible des filtres appliqués
 * @param variables     analyses des variables retenues (choix et numériques)
 * @param omittedVariables variables analysables non détaillées faute de place
 * @param excludedVariables variables non agrégeables (texte libre, GPS, multimédia), citées pour mémoire
 */
public record ReportData(
        String organization,
        String questionnaireTitle,
        long version,
        Instant generatedAt,
        String scope,
        FieldworkOverview fieldwork,
        CompletenessOverview completeness,
        List<QuestionAnalytics> variables,
        int omittedVariables,
        List<String> excludedVariables) {}
