# Formules et calcul des indicateurs

Ce document décrit uniquement l’origine et le calcul des chiffres affichés dans l’application. Les effectifs sont des nombres d’éléments; un pourcentage est toujours un rapport entre un numérateur et une base clairement définie.

## Règles de lecture

- **Dénominateur nul** : le pourcentage n’est pas défini. L’application affiche `—` lorsqu’il n’existe aucune base de calcul.
- **Arrondi** : les taux calculés par le backend sont arrondis à deux décimales; l’interface les affiche généralement avec une décimale. La progression d’une interview est arrondie à l’entier.
- **Périmètre** : les filtres et statuts sélectionnés déterminent les interviews prises en compte. Les indicateurs du tableau de bord utilisent les interviews renvoyées par Headquarters pour le questionnaire sélectionné, ou toutes les interviews si aucun questionnaire n’est sélectionné.
- **Unité de réponse** : pour une question classique, l’unité est l’interview; pour une question dans un roster, l’unité est une ligne du roster.

## Tableau de bord

Les quatre cartes affichent des **effectifs**, pas des pourcentages.

| Indicateur | Comment le chiffre est obtenu |
|---|---|
| **Questionnaires** | Nombre d’identifiants de questionnaires distincts présents dans le catalogue HQ. Plusieurs versions du même questionnaire ne comptent qu’une fois dans cette carte. |
| **Interviews** | Nombre d’interviews renvoyées par HQ pour le périmètre choisi, tous statuts confondus. |
| **Enquêteurs actifs** | Nombre d’identifiants de responsables distincts, non nuls, trouvés dans les interviews du périmètre. Ce chiffre correspond aux responsables d’au moins une interview; il ne représente pas nécessairement tous les comptes enquêteurs créés dans HQ. |
| **Superviseurs** | Nombre de comptes superviseur fourni par HQ. Il ne dépend pas du filtre de questionnaire. |

**Interviews par statut** : pour chaque statut `s`,

`effectif(s) = nombre d’interviews du périmètre dont le statut courant est s`

Le graphique présente ces effectifs; il ne calcule pas de part en pourcentage.

**Activité récente** : pour chaque date UTC `d` des 14 derniers jours (aujourd’hui inclus),

`activité(d) = nombre d’interviews dont la date LastEntryDate est d`

Chaque interview est comptée une fois, à sa dernière date de mise à jour connue. Cette courbe représente l’activité de mise à jour, pas le nombre de modifications ni les dates de complétion.

**Catalogue** : le nombre de versions affiché sous la carte et dans la liste est le nombre de versions renvoyées par HQ; il diffère donc du nombre d’identifiants de questionnaires distincts de la carte « Questionnaires ».

## Suivi terrain

**Objectif chiffré** : somme des quotas positifs des affectations actives. Les affectations sans quota positif sont comptées à part et n’entrent pas dans l’objectif.

**Interviews réalisées** : nombre d’interviews ayant une date de première complétion par un enquêteur. Une interview reste réalisée même si son statut courant est ensuite rejeté ou approuvé.

**Taux de réalisation** (général, par équipe ou par enquêteur) :

`réalisées ÷ objectif × 100`

Sans objectif chiffré, le taux est `—`. Le taux peut dépasser 100 % si les réalisations dépassent l’objectif; la barre graphique est alors limitée visuellement à 100 %.

**Part approuvée** :

`interviews actuellement approuvées ÷ interviews réalisées × 100`

Le numérateur comprend les interviews dont le statut courant est « approuvée par le superviseur » ou « approuvée par HQ ». Sans interview réalisée, la part est `—`.

**Progression quotidienne** : les interviews sont regroupées par date UTC de première complétion. Le cumul à la date `d` est la somme des réalisations quotidiennes jusqu’à `d`. Les jours sans complétion valent zéro.

**Rythme par jour actif** :

`interviews réalisées ÷ nombre de jours avec au moins une complétion`

Ce rythme est calculé par enquêteur et affiché avec une décimale. L’estimation des jours restants est :

`arrondi supérieur((objectif − réalisées, minimum 0) ÷ rythme moyen par jour actif)`

Elle n’est pas affichée si le rythme est nul ou s’il ne reste rien à réaliser.

**Durée médiane** : médiane des durées disponibles des interviews terminées, calculée comme le 50e percentile continu en secondes, puis convertie en minutes. Les durées absentes sont ignorées; l’affichage est arrondi à une décimale.

Les filtres de période et de zone filtrent les interviews, mais pas les objectifs. Les filtres d’équipe ou d’enquêteur limitent le calcul aux objectifs correspondants.

## Revue d’une interview

Les nombres de réponses renseignées, de non-réponses, de réponses signalées, de réponses invalides et de commentaires sont fournis par HQ; Analytics ne les recalcule pas.

**Progression des questions renseignées** :

`réponses renseignées ÷ (réponses renseignées + sans réponse) × 100`

Le résultat est arrondi à l’entier. Si le total des questions est nul ou indisponible, la barre et son pourcentage ne sont pas affichés.

## Variables et distributions

Les analyses portent sur les interviews correspondant aux statuts et filtres sélectionnés. Les états de l’export tabulaire sont interprétés ainsi : valeur présente = réponse; `-999999999` ou `##N/A##` = question posée sans réponse; cellule vide = question non applicable. Les non-applicables sont exclus du taux de réponse.

**Taux de réponse d’une variable** :

`réponses ÷ (réponses + sans réponse) × 100`

Pour une variable de roster, les nombres de réponses et de non-réponses comptent les lignes du roster. Si aucune unité n’est applicable, le taux n’a pas de base calculable.

### Pourcentages de distribution

**Choix unique et dates** :

`pourcentage d’une modalité = effectif de la modalité ÷ nombre total de réponses dans la distribution × 100`

Pour une date, une modalité correspond à un jour. Les catégories présentes dans les réponses mais absentes des libellés HQ restent regroupées sous leur valeur brute.

**Choix multiples** : le tableau et les info-bulles indiquent :

`pourcentage d’une modalité = nombre de répondants ayant choisi cette modalité ÷ nombre de réponses non vides × 100`

Un répondant pouvant choisir plusieurs modalités, la somme des pourcentages peut dépasser 100 %. Dans le graphique circulaire uniquement, chaque secteur montre la part de la modalité parmi toutes les sélections; les secteurs totalisent donc 100 % (à l’arrondi près).

**Variables numériques** : l’histogramme contient au plus huit intervalles de même largeur entre la valeur minimale et la valeur maximale :

`pourcentage d’un intervalle = valeurs numériques valides dans l’intervalle ÷ nombre total de valeurs numériques valides × 100`

Si toutes les valeurs sont identiques, un seul intervalle contient 100 % des valeurs.

### Statistiques numériques

Les calculs portent uniquement sur les réponses numériques valides, triées par ordre croissant. Les codes spéciaux définis dans les modalités (par exemple « Ne sait pas » ou « Refus ») et les réponses non numériques sont exclus de ces statistiques.

- **Effectif numérique valide (`n`)** : nombre de valeurs numériques valides.
- **Somme** : `x₁ + x₂ + … + xₙ`.
- **Moyenne** : `(x₁ + x₂ + … + xₙ) ÷ n`, arrondie à deux décimales.
- **Minimum / maximum** : première / dernière valeur de la liste triée.
- **Médiane** : valeur centrale si `n` est impair; moyenne des deux valeurs centrales si `n` est pair.
- **P5 et P95** : rang du percentile `p` = `max(1, plafond(p × n))`; la valeur au rang obtenu dans la liste triée est affichée, avec `p = 0,05` pour P5 et `p = 0,95` pour P95.

Les pourcentages des valeurs spéciales sont calculés sur toutes les réponses non vides de la variable numérique, y compris les valeurs numériques valides :

`pourcentage d’une valeur spéciale = effectif de cette valeur ÷ nombre total de réponses non vides × 100`

Les réponses libres et géographiques ne sont pas agrégées dans une distribution de modalités.

## Qualité des données

Le tableau reprend, variable par variable, les effectifs répondus, sans réponse et non applicables, ainsi que le taux de réponse défini plus haut.

- **Variables posées** : nombre de variables avec au moins une réponse ou une non-réponse.
- **Taux de réponse moyen** : moyenne arithmétique simple des taux des variables posées. Chaque variable a le même poids, quel que soit son nombre de réponses; les variables sans réponse attendue sont exclues.

`taux moyen = somme des taux des variables posées ÷ nombre de variables posées`

- **Variables sous 80 %** : nombre de variables posées dont le taux de réponse est strictement inférieur à 80 %.
- **Non-réponses** : somme des unités sans réponse de toutes les variables; ce n’est pas un nombre d’interviews distinctes.

## Rapport PDF

Le rapport réutilise les mêmes périmètres, effectifs, taux et statistiques que les onglets **Suivi terrain**, **Variables** et **Qualité**. Il n’applique pas de formule différente. Les taux moyens de réponse y sont la moyenne simple des taux des variables posées; les choix multiples gardent leur base « répondants » et peuvent dépasser 100 %.
