// SPDX-License-Identifier: MPL-2.0
package org.pocketnutrition.core

import org.pocketnutrition.api.IngredientDetail

/** One option in a `select` input: the code sent to the server and its French label. */
data class FeedbackOption(val code: String, val fr: String)

/** The typed value an error needs. */
sealed interface FeedbackInput {
    data class Number(val suffix: String? = null) : FeedbackInput
    data class NumberUnit(val units: List<String>) : FeedbackInput
    data class Select(val options: List<FeedbackOption>) : FeedbackInput
    data class Text(val placeholder: String? = null) : FeedbackInput
}

/**
 * One error a category can report.
 *
 * [code] MUST match the API allowlist (`FeedbackController ALLOWED_CATEGORIES` / `ALLOWED_CODES`)
 * — see [FEEDBACK_CATEGORIES]. [hint] names the [MealIngredient] value shown beside the row so the
 * reporter can see what they are correcting; resolved by [feedbackCurrentValue].
 */
data class FeedbackErrorDef(
    val code: String,
    val fr: String,
    val input: FeedbackInput,
    val hint: String? = null,
)

/** One category of the guided-correction form, and the errors it groups. */
data class FeedbackCategory(val code: String, val fr: String, val errors: List<FeedbackErrorDef>)

// Closed vocabularies mirrored from contract/data/strings/fr.json's feedback.catalog.optionLists —
// do not extend any of these client-side, the server allowlist will reject an invented code anyway.

private val FOOD_GROUP_OPTIONS = listOf(
    FeedbackOption("meat", "viande"),
    FeedbackOption("fish", "poisson"),
    FeedbackOption("shellfish", "fruits de mer"),
    FeedbackOption("vegetable", "légume"),
    FeedbackOption("cereal", "céréale"),
    FeedbackOption("dairy", "produit laitier"),
    FeedbackOption("beverage", "boisson"),
    FeedbackOption("egg", "œuf"),
    FeedbackOption("fat", "matière grasse"),
    FeedbackOption("fruit", "fruit"),
    FeedbackOption("legume", "légumineuse"),
    FeedbackOption("nut_seed", "fruit à coque / graine"),
    FeedbackOption("spice_herb", "épice / herbe"),
    FeedbackOption("sweetener", "édulcorant"),
    FeedbackOption("baked_good", "produit de boulangerie"),
    FeedbackOption("sauce", "sauce"),
    FeedbackOption("processed_meat", "charcuterie"),
    FeedbackOption("prepared_dish", "plat préparé"),
    FeedbackOption("seaweed", "algue"),
    FeedbackOption("alcohol", "alcool"),
    FeedbackOption("other", "autre"),
)

private val SERVING_UNIT_OPTIONS = listOf(
    FeedbackOption("pinch", "pincée"),
    FeedbackOption("teaspoon", "cuillère à café"),
    FeedbackOption("tablespoon", "cuillère à soupe"),
    FeedbackOption("cup", "tasse"),
    FeedbackOption("handful", "poignée"),
    FeedbackOption("small_handful", "petite poignée"),
    FeedbackOption("glass", "verre"),
)

/**
 * Lowercase forms, used in the feedback form only — distinct from [org.pocketnutrition.ui.Fr.cookingMethod]'s
 * capitalised picker labels.
 */
private val COOKING_METHOD_OPTIONS = listOf(
    FeedbackOption("raw", "cru"),
    FeedbackOption("boiled", "bouilli"),
    FeedbackOption("steamed", "vapeur"),
    FeedbackOption("grilled", "grillé"),
    FeedbackOption("roasted", "rôti"),
    FeedbackOption("fried", "frit"),
    FeedbackOption("cooked", "cuit (générique)"),
)

private val SERVING_MODE_OPTIONS = listOf(
    FeedbackOption("item", "à l'unité"),
    FeedbackOption("weight", "au poids"),
    FeedbackOption("volume", "au volume"),
    FeedbackOption("measure", "à la mesure"),
)

private val DISPLAY_UNIT_OPTIONS = listOf(
    FeedbackOption("g", "grammes (g)"),
    FeedbackOption("ml", "millilitres (ml)"),
)

private val RAW_COOKED_OPTIONS = listOf(
    FeedbackOption("raw", "cru"),
    FeedbackOption("cooked", "cuit"),
    FeedbackOption("both", "les deux"),
)

/**
 * The guided-correction taxonomy shown in the feedback sheet: 5 categories, each with a set of
 * common errors and the input each one needs.
 *
 * Transcribed verbatim from `contract/data/strings/fr.json`'s `feedback.catalog` — CODES ARE NOT
 * COPY: every `code` here must match the server's allowlist, or a submission built from it is
 * rejected with 400. Order is UI order.
 */
val FEEDBACK_CATEGORIES: List<FeedbackCategory> = listOf(
    FeedbackCategory(
        code = "nutrition",
        fr = "Valeurs nutritionnelles",
        errors = listOf(
            FeedbackErrorDef("energy", "Énergie", FeedbackInput.Number("kcal"), hint = "energy"),
            FeedbackErrorDef("protein", "Protéines", FeedbackInput.Number("g"), hint = "protein"),
            FeedbackErrorDef("fat", "Lipides", FeedbackInput.Number("g"), hint = "fat"),
            FeedbackErrorDef("carbs", "Glucides", FeedbackInput.Number("g"), hint = "carbs"),
            FeedbackErrorDef("fiber", "Fibres", FeedbackInput.Number("g"), hint = "fiber"),
            FeedbackErrorDef(
                "other_nutrient",
                "Autre nutriment",
                FeedbackInput.Text("ex : vitamine C ~ 12 mg"),
            ),
        ),
    ),
    FeedbackCategory(
        code = "identity",
        fr = "Identité",
        errors = listOf(
            FeedbackErrorDef(
                "name_fr",
                "Nom (français)",
                FeedbackInput.Text("nom correct en français"),
                hint = "name_fr",
            ),
            FeedbackErrorDef(
                "name_en",
                "Nom (anglais)",
                FeedbackInput.Text("correct English name"),
                hint = "name_en",
            ),
            FeedbackErrorDef(
                "food_group",
                "Groupe alimentaire",
                FeedbackInput.Select(FOOD_GROUP_OPTIONS),
                hint = "food_group",
            ),
            FeedbackErrorDef(
                "aliases",
                "Noms alternatifs / synonymes",
                FeedbackInput.Text("ex : poivre, poivre moulu"),
            ),
            FeedbackErrorDef(
                "wrong_ingredient",
                "Mauvais ingrédient reconnu",
                FeedbackInput.Text("le bon ingrédient serait…"),
            ),
        ),
    ),
    FeedbackCategory(
        code = "measures",
        fr = "Mesures & portions",
        errors = listOf(
            FeedbackErrorDef(
                code = "reference_weight",
                fr = "Poids de référence",
                input = FeedbackInput.NumberUnit(listOf("g", "ml")),
                hint = "reference_weight",
            ),
            FeedbackErrorDef(
                code = "serving_unit",
                fr = "Unité de mesure manquante (ex : pincée)",
                input = FeedbackInput.Select(SERVING_UNIT_OPTIONS),
                hint = "serving_units",
            ),
            FeedbackErrorDef(
                code = "display_unit",
                fr = "Unité d'affichage",
                input = FeedbackInput.Select(DISPLAY_UNIT_OPTIONS),
                hint = "display_unit",
            ),
            FeedbackErrorDef("serving_mode", "Mode de portion", FeedbackInput.Select(SERVING_MODE_OPTIONS)),
            FeedbackErrorDef("density", "Densité", FeedbackInput.Number("g/ml")),
        ),
    ),
    FeedbackCategory(
        code = "cooking",
        fr = "Cuisson",
        errors = listOf(
            FeedbackErrorDef("yield", "Rendement après cuisson", FeedbackInput.Number("%")),
            FeedbackErrorDef(
                code = "cooking_method",
                fr = "Méthode de cuisson manquante",
                input = FeedbackInput.Select(COOKING_METHOD_OPTIONS),
                hint = "cooking_method",
            ),
            FeedbackErrorDef(
                code = "raw_cooked",
                fr = "Se mange cru / cuit",
                input = FeedbackInput.Select(RAW_COOKED_OPTIONS),
                hint = "raw_cooked",
            ),
        ),
    ),
    FeedbackCategory(
        code = "other",
        fr = "Autre / nouvelle information",
        errors = listOf(
            FeedbackErrorDef(
                "free",
                "Autre remarque",
                FeedbackInput.Text("décrivez le problème ou la nouvelle information"),
            ),
        ),
    ),
)

/**
 * The ingredient's current value for an error's [FeedbackErrorDef.hint], shown beside the error row
 * so the reporter can see what they would be correcting. Mirrors the sandbox's `currentValue()`.
 *
 * The first five hints resolve from [item] alone — data the row already holds from
 * `/ingredients/search`, so they render instantly and never depend on [detail]. The nutrition/identity
 * hints below need the fuller `GET /ingredients/{id}` payload the row never fetched on its own, so
 * they resolve from [detail] instead and are simply absent (not an error) until it loads — [detail]
 * defaults to `null` for exactly that "not fetched yet" case. These hint codes are a client-only
 * rendering aid: unlike the first five, they are not declared in the shared `fr.json` catalog's `hint`
 * field, because nothing here is ever sent to the server — only `category`/`code`/`value`/`unit` are.
 */
fun feedbackCurrentValue(hint: String?, item: MealIngredient, detail: IngredientDetail? = null): String? =
    when (hint) {
        "reference_weight" -> "${Math.round(item.basePortionG)} g"
        "display_unit" -> item.displayUnit
        "serving_units" ->
            if (item.servingUnits.isEmpty()) "aucune" else item.servingUnits.joinToString(", ") { it.labelFr }
        "raw_cooked" -> item.canEatRaw?.let { if (it) "cru possible" else "cuisson requise" }
        "cooking_method" -> item.cookingMethod
        "energy" -> detail?.referenceNutrition?.energyKcal?.let { "${Math.round(it)} kcal" }
        "protein" -> detail?.referenceNutrition?.proteinG?.let { "${Math.round(it)} g" }
        "fat" -> detail?.referenceNutrition?.fatG?.let { "${Math.round(it)} g" }
        "carbs" -> detail?.referenceNutrition?.carbohydratesG?.let { "${Math.round(it)} g" }
        "fiber" -> detail?.referenceNutrition?.fiberG?.let { "${Math.round(it)} g" }
        "name_fr" -> detail?.nameFr
        "name_en" -> detail?.nameEn
        "food_group" -> detail?.foodGroup
        else -> null
    }
