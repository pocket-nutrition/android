// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.ui.targets

import org.pocketnutrition.api.LanguageSettings
import org.pocketnutrition.core.ProgressStatus
import org.pocketnutrition.core.UnsupportedReason

// Copy for the daily-targets feature (spec 05). Client-authored in both languages, like the meal-slot copy:
// not yet in the shared deck. The tone is information, not judgement: no "bad day", no streaks, no red.

interface TargetsCopy {
    val featureTitle: String
    val flagLabel: String
    val flagBody: String
    val openTargets: String
    val setMyTargets: String
    val setMyTargetsBody: String
    val logWeight: String
    val needsConnection: String
    fun unsupported(reason: UnsupportedReason): String
    val limitMarker: String
    fun coverage(known: Int, total: Int): String
    fun usual(value: String): String
    fun nutrientName(key: String): String
    val salt: String
    fun status(status: ProgressStatus): String
    val kindGoal: String
    val kindRange: String
    val kindLimit: String
    val kindEnergy: String
    fun aboveUpperLimit(value: String): String
    val noTarget: String
    val close: String
    val back: String

    // Day detail, week, trends
    val dayDetailTitle: String
    val groupEnergyMacros: String
    val groupFats: String
    val groupMinerals: String
    val groupVitamins: String
    val mostFrom: String
    val seeWeek: String
    val seeTrends: String
    val weeklyNutrientNote: String
    val weekTitle: String
    fun weekDays(withData: Int): String
    val trendsTitle: String
    fun lastDays(n: Int): String
    val targetLine: String
    val noDataYet: String

    // Questionnaire
    val questionnaireTitle: String
    val skip: String
    val next: String
    val previous: String
    val save: String
    fun stepOf(step: Int, total: Int): String
    val birthYearQuestion: String
    val birthYearLabel: String
    val bodyQuestion: String
    val heightLabel: String
    val weightLabel: String
    val heightNote: String
    val basisQuestion: String
    val basisExplain: String
    val basisFemale: String
    val basisMale: String
    val basisAverage: String
    val postmenopausalQuestion: String
    val yes: String
    val no: String
    val situationQuestion: String
    val situationNone: String
    val situationPregnant: String
    val situationLactating: String
    fun trimester(n: Int): String
    val lactationFirst: String
    val lactationAfter: String
    val activityQuestion: String
    fun activityName(pal: Double): String
    fun activityExample(pal: Double): String
    val previewTitle: String
    val previewBody: String
    val invalidNumber: String

    // Targets screen
    val targetsTitle: String
    val presetsTitle: String
    fun presetName(id: String): String
    val medicalBanner: String
    val medicalBannerAction: String
    val watchListTitle: String
    val watchListHint: String
    val shown: String
    val hidden: String
    val moveUp: String
    val moveDown: String
    val edit: String
    val resetToEfsa: String
    val resetAll: String
    val minLabel: String
    val maxLabel: String
    val yourValue: String
    val efsaValue: String
    val noEfsaTarget: String
    val editTitle: String
    val cancel: String

    // Explanation
    val explanationTitle: String
    val sourcesLabel: String
    fun inputsUsed(age: Int, population: String, pal: String, weight: String, basis: String, situation: String?): String
    val overridden: String
    val populationAdults: String
    val populationChildren: String
    val ourChoicesTitle: String
    val ourChoices: List<String>
    fun profileUpdated(date: String): String
    val notMedicalAdvice: String
    val noProfile: String

    // Hub and weight
    val answerQuestions: String
    val editAnswers: String
    val weightTitle: String
    val weightEntryLabel: String
    val weightMean: String
    val noWeights: String
    val delete: String
    fun kg(value: String): String
}

object TargetStrings {
    val active: TargetsCopy get() = if (LanguageSettings.effective == "en") TargetsEn else TargetsFr
}

private val EN_NAMES = mapOf(
    "energyKcal" to "Energy", "proteinG" to "Protein", "fatG" to "Fat", "carbohydratesG" to "Carbohydrates",
    "fiberG" to "Fibre", "waterG" to "Water", "sugarsG" to "Sugars", "calciumMg" to "Calcium", "ironMg" to "Iron",
    "magnesiumMg" to "Magnesium", "sodiumMg" to "Sodium", "vitaminCMg" to "Vitamin C", "saturatedFatG" to "Saturated fat",
    "monounsaturatedFatG" to "Monounsaturated fat", "polyunsaturatedFatG" to "Polyunsaturated fat",
    "cholesterolMg" to "Cholesterol", "potassiumMg" to "Potassium", "phosphorusMg" to "Phosphorus", "zincMg" to "Zinc",
    "vitaminAUg" to "Vitamin A", "vitaminDUg" to "Vitamin D", "vitaminB6Mg" to "Vitamin B6", "vitaminB12Ug" to "Vitamin B12",
    "niacinMg" to "Niacin",
)

private val FR_NAMES = mapOf(
    "energyKcal" to "Énergie", "proteinG" to "Protéines", "fatG" to "Lipides", "carbohydratesG" to "Glucides",
    "fiberG" to "Fibres", "waterG" to "Eau", "sugarsG" to "Sucres", "calciumMg" to "Calcium", "ironMg" to "Fer",
    "magnesiumMg" to "Magnésium", "sodiumMg" to "Sodium", "vitaminCMg" to "Vitamine C", "saturatedFatG" to "Acides gras saturés",
    "monounsaturatedFatG" to "Acides gras mono-insaturés", "polyunsaturatedFatG" to "Acides gras polyinsaturés",
    "cholesterolMg" to "Cholestérol", "potassiumMg" to "Potassium", "phosphorusMg" to "Phosphore", "zincMg" to "Zinc",
    "vitaminAUg" to "Vitamine A", "vitaminDUg" to "Vitamine D", "vitaminB6Mg" to "Vitamine B6", "vitaminB12Ug" to "Vitamine B12",
    "niacinMg" to "Niacine",
)

object TargetsEn : TargetsCopy {
    override val featureTitle = "Daily targets"
    override val flagLabel = "Daily targets (preview)"
    override val flagBody = "Rings for the nutrients you watch, from EFSA's reference values. Information, not medical advice."
    override val openTargets = "Daily targets settings"
    override val setMyTargets = "Set my targets"
    override val setMyTargetsBody = "Optional: a few questions, or your own values."
    override val logWeight = "Log weight"
    override val needsConnection = "Targets need a connection the first time."
    override fun unsupported(reason: UnsupportedReason) = when (reason) {
        UnsupportedReason.INFANT -> "Targets for babies under one are not covered here. Please ask a health professional."
        UnsupportedReason.PREGNANCY_UNDER_18 -> "EFSA's pregnancy values are for adults. Please ask a midwife, doctor or dietitian for yours."
        UnsupportedReason.LACTATION_UNDER_18 -> "EFSA's breastfeeding values are for adults. Please ask a midwife, doctor or dietitian for yours."
    }
    override val limitMarker = "limit"
    override fun coverage(known: Int, total: Int) = "based on $known of $total meals"
    override fun usual(value: String) = "usual $value"
    override fun nutrientName(key: String) = EN_NAMES[key] ?: key
    override val salt = "Salt"
    override fun status(status: ProgressStatus) = when (status) {
        ProgressStatus.NO_TARGET -> "no target"
        ProgressStatus.UNDER -> "below target"
        ProgressStatus.OK -> "on target"
        ProgressStatus.OVER -> "above target"
    }
    override val kindGoal = "goal"
    override val kindRange = "range"
    override val kindLimit = "limit"
    override val kindEnergy = "maintenance"
    override fun aboveUpperLimit(value: String) = "above EFSA's safe upper level ($value)"
    override val noTarget = "No target"
    override val close = "Close"
    override val back = "Back"

    override val dayDetailTitle = "Day in detail"
    override val groupEnergyMacros = "Energy & macros"
    override val groupFats = "Fats"
    override val groupMinerals = "Minerals"
    override val groupVitamins = "Vitamins"
    override val mostFrom = "Most from"
    override val seeWeek = "See the week"
    override val seeTrends = "Trends"
    override val weeklyNutrientNote = "Vitamin D and B12 read better over a week."
    override val weekTitle = "Last 7 days"
    override fun weekDays(withData: Int) = if (withData == 1) "1 day with data" else "$withData days with data"
    override val trendsTitle = "Trends"
    override fun lastDays(n: Int) = "$n days"
    override val targetLine = "target"
    override val noDataYet = "Nothing logged yet."

    override val questionnaireTitle = "My targets"
    override val skip = "Skip, I'll enter my own values"
    override val next = "Next"
    override val previous = "Back"
    override val save = "Save"
    override fun stepOf(step: Int, total: Int) = "Step $step of $total"
    override val birthYearQuestion = "What year were you born?"
    override val birthYearLabel = "Year of birth"
    override val bodyQuestion = "Your height and weight"
    override val heightLabel = "Height (cm), optional"
    override val weightLabel = "Weight (kg)"
    override val heightNote = "Height is kept for later: EFSA's values use weight only."
    override val basisQuestion = "Which reference values should we use?"
    override val basisExplain = "EFSA publishes different values for some nutrients. We never ask for a gender: pick the values you want to compute with."
    override val basisFemale = "Female reference values"
    override val basisMale = "Male reference values"
    override val basisAverage = "An average of both"
    override val postmenopausalQuestion = "Postmenopausal?"
    override val yes = "Yes"
    override val no = "No"
    override val situationQuestion = "Anything special right now?"
    override val situationNone = "No"
    override val situationPregnant = "Pregnant"
    override val situationLactating = "Breastfeeding"
    override fun trimester(n: Int) = when (n) { 1 -> "1st trimester"; 2 -> "2nd trimester"; else -> "3rd trimester" }
    override val lactationFirst = "0–6 months"
    override val lactationAfter = "More than 6 months"
    override val activityQuestion = "How active are you?"
    override fun activityName(pal: Double) = when (pal) { 1.4 -> "Sedentary"; 1.6 -> "Moderately active"; 1.8 -> "Active"; else -> "Very active" }
    override fun activityExample(pal: Double) = when (pal) {
        1.4 -> "Desk job, little walking"
        1.6 -> "On your feet part of the day, or 3 sessions a week"
        1.8 -> "Physical job, or daily exercise"
        else -> "Hard physical job or intense daily training"
    }
    override val previewTitle = "Your targets"
    override val previewBody = "From EFSA's reference values. You can change any of them later."
    override val invalidNumber = "Please check this value."

    override val targetsTitle = "Targets"
    override val presetsTitle = "What to watch"
    override fun presetName(id: String) = when (id) {
        "standard" -> "Standard"; "sport" -> "Sport"; "kidney" -> "Kidney"; "hypertension" -> "Blood pressure"
        "diabetes" -> "Diabetes"; "everything" -> "Everything"; else -> "Custom"
    }
    override val medicalBanner = "These are general-population values. Enter the targets your doctor or dietitian gave you."
    override val medicalBannerAction = "Enter my values"
    override val watchListTitle = "Watch list"
    override val watchListHint = "Shown on the journal, in this order."
    override val shown = "Shown"
    override val hidden = "Hidden"
    override val moveUp = "Move up"
    override val moveDown = "Move down"
    override val edit = "Edit"
    override val resetToEfsa = "Reset to EFSA"
    override val resetAll = "Reset all to EFSA"
    override val minLabel = "Minimum"
    override val maxLabel = "Maximum"
    override val yourValue = "your value"
    override val efsaValue = "EFSA"
    override val noEfsaTarget = "no EFSA target"
    override val editTitle = "Daily target"
    override val cancel = "Cancel"

    override val explanationTitle = "How is this calculated"
    override val sourcesLabel = "Sources"
    override fun inputsUsed(age: Int, population: String, pal: String, weight: String, basis: String, situation: String?) =
        "Age $age ($population) · PAL $pal · $weight kg · $basis" + (situation?.let { " · $it" } ?: "")
    override val overridden = "Your value (overrides EFSA)"
    override val populationAdults = "EFSA adults"
    override val populationChildren = "EFSA children"
    override val ourChoicesTitle = "Our choices"
    override val ourChoices = listOf(
        "Activity levels map to EFSA's PAL values: sedentary 1.4, moderately active 1.6, active 1.8, very active 2.0.",
        "Zinc assumes a diet with 600 mg of phytate a day.",
        "The average basis is the mean of EFSA's female and male values.",
        "Weight is the average of your entries over the last 7 days.",
        "Salt is shown from sodium: salt = sodium × 2.5.",
    )
    override fun profileUpdated(date: String) = "Profile last updated $date"
    override val notMedicalAdvice = "Information, not medical advice."
    override val noProfile = "No profile yet: targets are your own values only."

    override val answerQuestions = "Answer the questions"
    override val editAnswers = "Edit my answers"
    override val weightTitle = "Weight"
    override val weightEntryLabel = "Today's weight (kg)"
    override val weightMean = "7-day mean"
    override val noWeights = "No weight logged yet."
    override val delete = "Delete"
    override fun kg(value: String) = "$value kg"
}

object TargetsFr : TargetsCopy {
    override val featureTitle = "Objectifs du jour"
    override val flagLabel = "Objectifs quotidiens (aperçu)"
    override val flagBody = "Des anneaux pour les nutriments que vous suivez, d'après les valeurs de référence de l'EFSA. Une information, pas un avis médical."
    override val openTargets = "Réglages des objectifs"
    override val setMyTargets = "Définir mes objectifs"
    override val setMyTargetsBody = "Facultatif : quelques questions, ou vos propres valeurs."
    override val logWeight = "Noter mon poids"
    override val needsConnection = "Les objectifs ont besoin d'une connexion la première fois."
    override fun unsupported(reason: UnsupportedReason) = when (reason) {
        UnsupportedReason.INFANT -> "Les besoins des bébés de moins d'un an ne sont pas couverts ici. Demandez à un professionnel de santé."
        UnsupportedReason.PREGNANCY_UNDER_18 -> "Les valeurs de grossesse de l'EFSA concernent les adultes. Demandez les vôtres à une sage-femme, un médecin ou un diététicien."
        UnsupportedReason.LACTATION_UNDER_18 -> "Les valeurs d'allaitement de l'EFSA concernent les adultes. Demandez les vôtres à une sage-femme, un médecin ou un diététicien."
    }
    override val limitMarker = "limite"
    override fun coverage(known: Int, total: Int) = "d'après $known repas sur $total"
    override fun usual(value: String) = "habituel $value"
    override fun nutrientName(key: String) = FR_NAMES[key] ?: key
    override val salt = "Sel"
    override fun status(status: ProgressStatus) = when (status) {
        ProgressStatus.NO_TARGET -> "pas d'objectif"
        ProgressStatus.UNDER -> "sous l'objectif"
        ProgressStatus.OK -> "dans l'objectif"
        ProgressStatus.OVER -> "au-dessus de l'objectif"
    }
    override val kindGoal = "objectif"
    override val kindRange = "fourchette"
    override val kindLimit = "limite"
    override val kindEnergy = "maintien"
    override fun aboveUpperLimit(value: String) = "au-dessus de la limite de sécurité de l'EFSA ($value)"
    override val noTarget = "Pas d'objectif"
    override val close = "Fermer"
    override val back = "Retour"

    override val dayDetailTitle = "La journée en détail"
    override val groupEnergyMacros = "Énergie et macronutriments"
    override val groupFats = "Graisses"
    override val groupMinerals = "Minéraux"
    override val groupVitamins = "Vitamines"
    override val mostFrom = "Surtout grâce à"
    override val seeWeek = "Voir la semaine"
    override val seeTrends = "Tendances"
    override val weeklyNutrientNote = "La vitamine D et la B12 se lisent mieux sur une semaine."
    override val weekTitle = "7 derniers jours"
    override fun weekDays(withData: Int) = if (withData <= 1) "$withData jour avec des données" else "$withData jours avec des données"
    override val trendsTitle = "Tendances"
    override fun lastDays(n: Int) = "$n jours"
    override val targetLine = "objectif"
    override val noDataYet = "Rien de noté pour l'instant."

    override val questionnaireTitle = "Mes objectifs"
    override val skip = "Passer, je saisis mes propres valeurs"
    override val next = "Suivant"
    override val previous = "Retour"
    override val save = "Enregistrer"
    override fun stepOf(step: Int, total: Int) = "Étape $step sur $total"
    override val birthYearQuestion = "Quelle est votre année de naissance ?"
    override val birthYearLabel = "Année de naissance"
    override val bodyQuestion = "Votre taille et votre poids"
    override val heightLabel = "Taille (cm), facultatif"
    override val weightLabel = "Poids (kg)"
    override val heightNote = "La taille est gardée pour plus tard : les valeurs de l'EFSA n'utilisent que le poids."
    override val basisQuestion = "Avec quelles valeurs de référence calculer ?"
    override val basisExplain = "L'EFSA publie des valeurs différentes pour certains nutriments. Nous ne demandons jamais le genre : choisissez les valeurs avec lesquelles calculer."
    override val basisFemale = "Valeurs de référence féminines"
    override val basisMale = "Valeurs de référence masculines"
    override val basisAverage = "La moyenne des deux"
    override val postmenopausalQuestion = "Ménopausée ?"
    override val yes = "Oui"
    override val no = "Non"
    override val situationQuestion = "Une situation particulière en ce moment ?"
    override val situationNone = "Non"
    override val situationPregnant = "Enceinte"
    override val situationLactating = "J'allaite"
    override fun trimester(n: Int) = when (n) { 1 -> "1er trimestre"; 2 -> "2e trimestre"; else -> "3e trimestre" }
    override val lactationFirst = "0 à 6 mois"
    override val lactationAfter = "Plus de 6 mois"
    override val activityQuestion = "Quel est votre niveau d'activité ?"
    override fun activityName(pal: Double) = when (pal) { 1.4 -> "Sédentaire"; 1.6 -> "Modérément actif"; 1.8 -> "Actif"; else -> "Très actif" }
    override fun activityExample(pal: Double) = when (pal) {
        1.4 -> "Travail de bureau, peu de marche"
        1.6 -> "Debout une partie de la journée, ou 3 séances par semaine"
        1.8 -> "Métier physique, ou exercice quotidien"
        else -> "Métier très physique ou entraînement intense quotidien"
    }
    override val previewTitle = "Vos objectifs"
    override val previewBody = "D'après les valeurs de référence de l'EFSA. Vous pourrez modifier chacun d'eux ensuite."
    override val invalidNumber = "Vérifiez cette valeur."

    override val targetsTitle = "Objectifs"
    override val presetsTitle = "Que suivre"
    override fun presetName(id: String) = when (id) {
        "standard" -> "Standard"; "sport" -> "Sport"; "kidney" -> "Reins"; "hypertension" -> "Tension"
        "diabetes" -> "Diabète"; "everything" -> "Tout"; else -> "Personnalisé"
    }
    override val medicalBanner = "Ce sont des valeurs pour la population générale. Saisissez les objectifs que votre médecin ou diététicien vous a donnés."
    override val medicalBannerAction = "Saisir mes valeurs"
    override val watchListTitle = "Nutriments suivis"
    override val watchListHint = "Affichés dans le journal, dans cet ordre."
    override val shown = "Affiché"
    override val hidden = "Masqué"
    override val moveUp = "Monter"
    override val moveDown = "Descendre"
    override val edit = "Modifier"
    override val resetToEfsa = "Revenir à l'EFSA"
    override val resetAll = "Tout remettre aux valeurs de l'EFSA"
    override val minLabel = "Minimum"
    override val maxLabel = "Maximum"
    override val yourValue = "votre valeur"
    override val efsaValue = "EFSA"
    override val noEfsaTarget = "pas d'objectif EFSA"
    override val editTitle = "Objectif quotidien"
    override val cancel = "Annuler"

    override val explanationTitle = "Comment c'est calculé"
    override val sourcesLabel = "Sources"
    override fun inputsUsed(age: Int, population: String, pal: String, weight: String, basis: String, situation: String?) =
        "$age ans ($population) · NAP $pal · $weight kg · $basis" + (situation?.let { " · $it" } ?: "")
    override val overridden = "Votre valeur (remplace l'EFSA)"
    override val populationAdults = "adultes EFSA"
    override val populationChildren = "enfants EFSA"
    override val ourChoicesTitle = "Nos choix"
    override val ourChoices = listOf(
        "Les niveaux d'activité correspondent aux NAP de l'EFSA : sédentaire 1,4, modérément actif 1,6, actif 1,8, très actif 2,0.",
        "Le zinc suppose une alimentation à 600 mg de phytates par jour.",
        "La base « moyenne » est la moyenne des valeurs féminines et masculines de l'EFSA.",
        "Le poids est la moyenne de vos saisies des 7 derniers jours.",
        "Le sel est affiché à partir du sodium : sel = sodium × 2,5.",
    )
    override fun profileUpdated(date: String) = "Profil mis à jour le $date"
    override val notMedicalAdvice = "Une information, pas un avis médical."
    override val noProfile = "Pas encore de profil : les objectifs sont uniquement vos propres valeurs."

    override val answerQuestions = "Répondre aux questions"
    override val editAnswers = "Modifier mes réponses"
    override val weightTitle = "Poids"
    override val weightEntryLabel = "Poids du jour (kg)"
    override val weightMean = "Moyenne sur 7 jours"
    override val noWeights = "Aucun poids noté pour l'instant."
    override val delete = "Supprimer"
    override fun kg(value: String) = "$value kg"
}
