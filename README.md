# Survey Solutions Analytics

Application Analytics pour visualiser le catalogue des questionnaires, les volumes et statuts d'interviews, les responsables distincts, les superviseurs et l'activité récente, et pour traiter les interviews depuis leur revue. Le backend interroge directement l'API officielle de Survey Solutions HQ; aucun chiffre de démonstration ni microdonnée de réponse n'est stocké.

## Démarrage avec Docker

Le Compose Analytics est indépendant du Compose HQ existant. Il ne recrée ni ne modifie la base de données HQ.

1. Copier `.env.analytics.example` vers `.env.analytics` et renseigner le compte API Survey Solutions dédié.
2. Démarrer l'application :

   ```sh
   docker compose --env-file .env.analytics -f docker-compose.analytics.yml up --build
   ```

3. Ouvrir [http://localhost:8088](http://localhost:8088). Les journaux du backend sont visibles avec :

   ```sh
   docker compose --env-file .env.analytics -f docker-compose.analytics.yml logs -f backend
   ```

Pour arrêter uniquement Analytics, exécuter `docker compose --env-file .env.analytics -f docker-compose.analytics.yml down`. Ne pas utiliser `down -v` sur le projet HQ.

## Configuration et sécurité

- `SURVEY_SOLUTIONS_BASE_URL` : URL accessible **depuis le backend**. La valeur par défaut Docker Desktop vise HQ sur le Mac via `host.docker.internal:8080`.
- `SURVEY_SOLUTIONS_USERNAME` et `SURVEY_SOLUTIONS_PASSWORD` : identifiants Basic Auth d'un compte HQ dédié de rôle `ApiUser`. Ils restent dans le backend et ne sont jamais envoyés au navigateur.
- `ANALYTICS_ALLOWED_ORIGIN` : origine autorisée pour les requêtes du frontend.
- `.env.analytics` est ignoré par Git. Ne pas y mettre de secret dans un fichier versionné.

Le backend lit l'API HQ, crée des jobs d'export (`POST /api/v2/export`) et relaie les actions de revue demandées dans Analytics. Les statistiques utilisent les statuts renvoyés par HQ; l'évolution temporelle compte les interviews selon leur `LastEntryDate` et doit donc être lue comme une activité de mise à jour, pas comme une date de complétion.

Les actions de revue sont disponibles depuis l'onglet Variables, pour les interviews qui ont une réponse à la variable sélectionnée. L'interface permet d'approuver une interview terminée ou approuvée par un superviseur, de la rejeter avec un commentaire facultatif, ou de retirer l'approbation HQ. Le backend contrôle que l'interview appartient à la version synchronisée et que son statut permet l'action, puis appelle `PATCH /api/v1/interviews/{id}/hqapprove`, `PATCH /api/v1/interviews/{id}/hqreject` ou `PATCH /api/v1/interviews/{id}/hqunapprove` dans HQ. Ces opérations exigent que le compte API configuré dispose des permissions HQ correspondantes; les actions réussies sont inscrites dans les journaux du backend.

Analytics n'a pas d'authentification propre. Les actions utilisent le compte API partagé configuré côté backend et ne distinguent pas les utilisateurs Analytics. Garder l'application accessible uniquement sur `127.0.0.1` comme dans le Compose fourni; ne pas exposer l'interface ni le backend sur un réseau avant d'ajouter authentification, autorisation et attribution des actions à un utilisateur.

Les réponses des interviews sont importées dans une base PostgreSQL dédiée (`analytics-db`, non exposée sur le réseau, volume `analytics-db`). Elle contient des microdonnées : appliquer les mêmes règles d'accès, de sauvegarde et de conservation qu'à HQ. L'application n'inclut encore ni authentification propre ni administration. Le port web est limité à `127.0.0.1`; ne pas l'exposer sur un réseau ou en production avant d'ajouter l'authentification et les contrôles d'accès.

L'explorateur applique le [contrat API HQ](https://docs.mysurvey.solutions/headquarters/api/survey-solutions-api/) et utilise le Swagger de l'instance pour les routes et paramètres disponibles. Il liste les questions de `GET /api/v1/statistics/questions`; si HQ renvoie une liste vide, il les découvre dans le document de questionnaire de `GET /api/v1/questionnaires/{id}/{version}/document`. Les types numériques du document sont interprétés selon l'énumération Survey Solutions : `0` choix unique, `3` choix multiples, `4` numérique, `5` date/heure, `6` GPS, `7` texte, `9` liste de texte, `10` code-barres, `11` multimédia, `12` zone et `13` audio.

La documentation du [rapport Survey Statistics](https://docs.mysurvey.solutions/headquarters/reporting/report-survey-statistics/) confirme les statistiques descriptives numériques et tableaux de fréquence catégoriels. Son endpoint `/api/v1/statistics` a aussi été testé sur cette instance, mais il renvoie zéro ligne pour la question catégorielle testée et une erreur serveur pour la question texte; l'Analytics calcule donc les indicateurs disponibles depuis `GET /api/v1/interviews/{id}` au lieu d'afficher ces résultats incomplets comme des zéros. Pour la question sélectionnée, l'interface montre également les valeurs réellement saisies, associées à un numéro d'interview temporaire sans nom ni identifiant HQ. Les variables numériques affichent moyenne, somme, médiane, minimum, maximum, P5, P95 et histogramme; les dates sont regroupées par jour; les modalités catégorielles sont agrégées côté serveur. Si une réponse réelle ne correspond ni au code ni aux libellés des modalités du document, elle reste affichée sous sa valeur brute dans la distribution au lieu d'être regroupée sous une catégorie générique.

Les réponses individuelles peuvent contenir des informations personnelles ou sensibles. Elles sont désormais renvoyées au navigateur pour être affichées : conserver le service sur `127.0.0.1`, ne pas l'exposer sur un réseau ou en production sans authentification et contrôle d'accès, et n'accorder l'accès qu'aux personnes autorisées.

L'explorateur permet de télécharger les réponses du questionnaire sélectionné en CSV ou Excel (`.xlsx`). Chaque ligne représente une réponse renvoyée par HQ; les répétitions (notamment les rosters) restent sur des lignes distinctes. Les valeurs structurées sont présentées sous forme lisible (par exemple `Latitude : 48.8 ; Longitude : 2.3`) plutôt qu'en JSON brut, et les champs additionnels sont rendus en paires nom-valeur. Les réponses multimédia sont exportées comme valeurs/références renvoyées par l'API; les fichiers binaires ne sont pas téléchargés. L'export lit au plus 5 000 interviews et échoue explicitement au-delà. Excel limite une cellule à 32 767 caractères; les valeurs plus longues restent exportables en CSV. Les exports contiennent des réponses potentiellement sensibles : les mêmes précautions d'accès et de stockage que pour les données HQ s'appliquent.

### Synchronisation et calcul des indicateurs

Les indicateurs par variable et la complétude sont calculés sur la base Analytics, alimentée par l'[Export API](https://docs.mysurvey.solutions/headquarters/api/survey-solutions-api/) au format tabulaire :

1. toutes les `ANALYTICS_SYNC_INTERVAL` (30 minutes par défaut), le backend compare pour chaque version le nombre d'interviews et leur dernière modification (une requête GraphQL) avec la dernière synchronisation ; seules les versions modifiées sont réexportées ;
2. ouvrir une version jamais importée lance aussitôt sa synchronisation, et le bouton « Synchroniser maintenant » en force une ;
3. l'archive est lue fichier par fichier (fichier principal et un fichier par roster) puis remplace atomiquement les données de la version.

Conventions de l'export reprises dans les indicateurs : cellule vide = question **non applicable** (désactivée par un filtre), `-999999999` / `##N/A##` = question posée **sans réponse**. Le taux de réponse vaut réponses / (réponses + sans réponse) : les non applicables n'en font pas partie. Pour une variable de roster, l'unité est la ligne (membre, parcelle…) et non l'interview. Les choix multiples sont lus par code (`variable__code`), indépendamment de la traduction. Seuls les statuts d'interview listés dans `ANALYTICS_STATUSES` entrent dans les calculs.

### Suivi terrain

À chaque synchronisation, le backend importe aussi les affectations actives de la version (`/api/v1/assignments`) et la composition des équipes (`/api/v1/supervisors/{id}/interviewers`). L'historique de l'export (`interview__actions.tab`) donne pour chaque interview :
- l'enquêteur, c'est-à-dire celui de la dernière complétion, ou à défaut le dernier enquêteur responsable ;
- la date de création ;
- la date de première complétion.

Définitions :
- **objectif** : somme des quotas des affectations actives ; les affectations sans quota sont comptées à part ;
- **réalisées** : interviews terminées au moins une fois par un enquêteur, quel que soit leur statut actuel ;
- **taux** : réalisées / objectif ;
- **par jour** : réalisées / nombre de jours où l'enquêteur a terminé au moins une interview.

Les objectifs ne dépendent ni de la période ni de la zone filtrées, seulement de l'équipe ou de l'enquêteur.

`SURVEY_SOLUTIONS_WORKSPACE` désigne le workspace HQ interrogé (`primary` par défaut ; vide pour une instance HQ sans workspaces). L'export CSV/Excel de l'explorateur lit encore HQ en direct et reste limité à 5 000 interviews.

## Développement local

Le backend lancé hors Docker attend PostgreSQL sur `localhost:5433` (base, utilisateur `analytics`, mot de passe `ANALYTICS_DB_PASSWORD`) ; `ANALYTICS_DB_URL` permet d'en choisir une autre. Les migrations Flyway créent le schéma au démarrage.

Le backend écoute sur `http://localhost:8081`; le proxy Angular transmet `/api` vers ce port :

```sh
cd backend
SURVEY_SOLUTIONS_BASE_URL=http://localhost:8080 \
SURVEY_SOLUTIONS_USERNAME='votre-compte-api' \
SURVEY_SOLUTIONS_PASSWORD='votre-mot-de-passe' \
docker run --rm -p 8081:8081 \
  -e SURVEY_SOLUTIONS_BASE_URL \
  -e SURVEY_SOLUTIONS_USERNAME \
  -e SURVEY_SOLUTIONS_PASSWORD \
  --add-host=host.docker.internal:host-gateway \
  survey-analytics-backend
```

Pour le développement local, le backend requiert Maven 3.9+ et Java 17; le lancer dans `backend/` avec `mvn spring-boot:run` après avoir défini les trois variables `SURVEY_SOLUTIONS_*` dans l'environnement. Angular requiert Node.js 20.19+; exécuter `npm ci` puis `npm start` dans `frontend/`. Le proxy Angular transmet `/api` vers le backend local.

## Endpoints Analytics

- `GET /api/health` : disponibilité du processus.
- `GET /api/questionnaires` : catalogue HQ paginé.
- `GET /api/dashboard` : agrégats globaux.
- `GET /api/dashboard?questionnaireId={uuid}&version={version}` : agrégats pour une version donnée.
- `GET /api/questionnaires/{uuid}/{version}/question-list` : variables et métadonnées de questions.
- `GET /api/questionnaires/{uuid}/{version}/question-completeness` : taux de réponse agrégé par variable, sans exposer les réponses individuelles.
- `GET /api/questionnaires/{uuid}/{version}/question-analytics?variableName={variable}` : agrégats d'une variable.
- `GET /api/questionnaires/{uuid}/{version}/export?format=csv|xlsx` : export des réponses du questionnaire en CSV ou Excel.
- `GET /api/questionnaires/{uuid}/{version}/questions` : métadonnées des questions HQ.
- `PATCH /api/questionnaires/{uuid}/{version}/interviews/{interviewId}/hqapprove` : approuve une interview.
- `PATCH /api/questionnaires/{uuid}/{version}/interviews/{interviewId}/hqreject` : rejette une interview (`comment` et `responsibleId` facultatifs).
- `PATCH /api/questionnaires/{uuid}/{version}/interviews/{interviewId}/hqunapprove` : retire une approbation HQ.
- `GET /api/questionnaires/{uuid}/{version}/sync` : état de la synchronisation (en cours, date des données, dernière erreur).
- `POST /api/questionnaires/{uuid}/{version}/sync` : lance une synchronisation (réponse 202).
- `GET /api/questionnaires/{uuid}/{version}/fieldwork` : suivi terrain (objectif des affectations, interviews réalisées, approuvées, rejetées, en cours, progression journalière, détail par équipe et par enquêteur).
- `GET /api/questionnaires/{uuid}/{version}/filter-options` : superviseurs, enquêteurs et statuts proposés dans les filtres.

`fieldwork`, `question-completeness` et `question-analytics` acceptent les mêmes filtres : `from` et `to` (date de complétion, AAAA-MM-JJ, UTC), `supervisor`, `interviewer`, `statuses` (liste séparée par des virgules, sinon `ANALYTICS_STATUSES`), et `variable` + `value` (code de modalité, par exemple une région) pour ne garder que les interviews dont une réponse a cette valeur.

Tant qu'une version n'a jamais été importée, `question-completeness` et `question-analytics` renvoient 409 avec `syncPending: true` et déclenchent la synchronisation.

Les appels amont paginés sont parcourus jusqu'au total déclaré par HQ. En cas d'erreur API, d'autorisation ou de pagination incomplète, le backend renvoie une erreur explicite plutôt qu'un total partiel présenté comme complet.

