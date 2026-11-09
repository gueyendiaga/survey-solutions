# Survey Solutions Analytics

MVP en lecture seule pour visualiser le catalogue des questionnaires, les volumes et statuts d'interviews, les responsables distincts, les superviseurs et l'activité récente. Le backend interroge directement l'API officielle de Survey Solutions HQ; aucun chiffre de démonstration ni microdonnée de réponse n'est stocké.

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

Les exports sont générés à la demande et n'exécutent que des requêtes GET. Les statistiques utilisent les statuts renvoyés par HQ; l'évolution temporelle compte les interviews selon leur `LastEntryDate` et doit donc être lue comme une activité de mise à jour, pas comme une date de complétion.

Ce MVP lit les agrégats à la demande : il n'utilise pas PostgreSQL, ne conserve aucune réponse individuelle et n'inclut ni authentification propre à la plateforme ni administration. Le port web est limité à `127.0.0.1`; ne pas l'exposer sur un réseau ou en production avant d'ajouter l'authentification et les contrôles d'accès.

L'explorateur applique le [contrat API HQ](https://docs.mysurvey.solutions/headquarters/api/survey-solutions-api/) et utilise le Swagger de l'instance pour les routes et paramètres disponibles. Il liste les questions de `GET /api/v1/statistics/questions`; si HQ renvoie une liste vide, il les découvre dans le document de questionnaire de `GET /api/v1/questionnaires/{id}/{version}/document`. Les types numériques du document sont interprétés selon l'énumération Survey Solutions : `0` choix unique, `3` choix multiples, `4` numérique, `5` date/heure, `6` GPS, `7` texte, `9` liste de texte, `10` code-barres, `11` multimédia, `12` zone et `13` audio.

La documentation du [rapport Survey Statistics](https://docs.mysurvey.solutions/headquarters/reporting/report-survey-statistics/) confirme les statistiques descriptives numériques et tableaux de fréquence catégoriels. Son endpoint `/api/v1/statistics` a aussi été testé sur cette instance, mais il renvoie zéro ligne pour la question catégorielle testée et une erreur serveur pour la question texte; l'Analytics calcule donc les indicateurs disponibles depuis `GET /api/v1/interviews/{id}` au lieu d'afficher ces résultats incomplets comme des zéros. Pour la question sélectionnée, l'interface montre également les valeurs réellement saisies, associées à un numéro d'interview temporaire sans nom ni identifiant HQ. Les variables numériques affichent moyenne, somme, médiane, minimum, maximum, P5, P95 et histogramme; les dates sont regroupées par jour; les modalités catégorielles sont agrégées côté serveur. Si une réponse réelle ne correspond ni au code ni aux libellés des modalités du document, elle reste affichée sous sa valeur brute dans la distribution au lieu d'être regroupée sous une catégorie générique.

Les réponses individuelles peuvent contenir des informations personnelles ou sensibles. Elles sont désormais renvoyées au navigateur pour être affichées : conserver le service sur `127.0.0.1`, ne pas l'exposer sur un réseau ou en production sans authentification et contrôle d'accès, et n'accorder l'accès qu'aux personnes autorisées.

L'explorateur permet de télécharger les réponses du questionnaire sélectionné en CSV ou Excel (`.xlsx`). Chaque ligne représente une réponse renvoyée par HQ; les répétitions (notamment les rosters) restent sur des lignes distinctes. Les valeurs structurées sont présentées sous forme lisible (par exemple `Latitude : 48.8 ; Longitude : 2.3`) plutôt qu'en JSON brut, et les champs additionnels sont rendus en paires nom-valeur. Les réponses multimédia sont exportées comme valeurs/références renvoyées par l'API; les fichiers binaires ne sont pas téléchargés. L'export lit au plus 5 000 interviews et échoue explicitement au-delà. Excel limite une cellule à 32 767 caractères; les valeurs plus longues restent exportables en CSV. Les exports contiennent des réponses potentiellement sensibles : les mêmes précautions d'accès et de stockage que pour les données HQ s'appliquent.

Chaque analyse lit les détails de toutes les interviews du questionnaire, avec au plus quatre appels concurrents. Une limite de sécurité de 5 000 interviews est appliquée; au-delà, l'API Analytics renvoie une erreur explicite plutôt qu'une analyse partielle. Pour de grands questionnaires, prévoir une stratégie de cache ou de traitement asynchrone.

## Développement local

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

Les appels amont paginés sont parcourus jusqu'au total déclaré par HQ. En cas d'erreur API, d'autorisation ou de pagination incomplète, le backend renvoie une erreur explicite plutôt qu'un total partiel présenté comme complet.


docker compose --env-file .env.analytics -f docker-compose.analytics.yml up --build -d

## URL HEADQUARTERS : http://localhost:8080

## Admin user
Username: gueye25
Password: jks89skfARLSEfb