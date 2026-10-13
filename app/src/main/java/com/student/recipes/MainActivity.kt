package com.student.recipes

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import org.json.JSONArray

class MainActivity : AppCompatActivity() {

    data class Recipe(
        val name: String,
        val category: String,
        val calories: Int,
        val vegan: Boolean,
        val time: Int,
        val ingredients: List<String>
    )

    private val recipes = mutableListOf<Recipe>()
    private val selected = mutableSetOf<String>()

    private lateinit var chipGroup: ChipGroup
    private lateinit var results: LinearLayout
    private lateinit var countText: TextView
    private lateinit var resultTitle: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        chipGroup = findViewById(R.id.ingredientChips)
        results = findViewById(R.id.results)
        countText = findViewById(R.id.countText)
        resultTitle = findViewById(R.id.resultTitle)

        loadRecipes()
        buildChips()

        findViewById<EditText>(R.id.searchBox).doAfterTextChanged { text ->
            filterChips(text?.toString() ?: "")
        }

        findViewById<Button>(R.id.clearButton).setOnClickListener {
            selected.clear()
            for (i in 0 until chipGroup.childCount) {
                (chipGroup.getChildAt(i) as Chip).isChecked = false
            }
            update()
        }

        update()
    }

    // Reads the 50 recipes from app/src/main/assets/recipes.json
    private fun loadRecipes() {
        val text = assets.open("recipes.json").bufferedReader().use { it.readText() }
        val array = JSONArray(text)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val ingArray = obj.getJSONArray("ingredients")
            val ingredients = List(ingArray.length()) { ingArray.getString(it) }
            recipes.add(
                Recipe(
                    name = obj.getString("name"),
                    category = obj.getString("category"),
                    calories = obj.getInt("calories"),
                    vegan = obj.getBoolean("vegan"),
                    time = obj.getInt("time"),
                    ingredients = ingredients
                )
            )
        }
    }

    // Makes one tappable chip for every ingredient used in the recipes
    private fun buildChips() {
        val leaf = color(R.color.leaf)
        val paper = color(R.color.paper)
        val white = color(R.color.white)
        val ink = color(R.color.ink)
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())

        recipes.flatMap { it.ingredients }.distinct().sorted().forEach { name ->
            val chip = Chip(this)
            chip.text = name
            chip.isCheckable = true
            chip.chipBackgroundColor = ColorStateList(states, intArrayOf(leaf, paper))
            chip.setTextColor(ColorStateList(states, intArrayOf(white, ink)))
            chip.checkedIconTint = ColorStateList.valueOf(white)
            chip.setOnCheckedChangeListener { _, checked ->
                if (checked) selected.add(name) else selected.remove(name)
                update()
            }
            chipGroup.addView(chip)
        }
    }

    private fun filterChips(query: String) {
        val q = query.trim()
        for (i in 0 until chipGroup.childCount) {
            val chip = chipGroup.getChildAt(i) as Chip
            val match = q.isEmpty() || chip.text.toString().contains(q, ignoreCase = true)
            chip.visibility = if (match) View.VISIBLE else View.GONE
        }
    }

    // Scores every recipe against the selected ingredients and shows the best matches
    private fun update() {
        val n = selected.size
        countText.text = when (n) {
            0 -> "No ingredients selected"
            1 -> "1 ingredient selected"
            else -> "$n ingredients selected"
        }
        results.removeAllViews()

        if (n == 0) {
            resultTitle.text = "Recipes"
            results.addView(message(getString(R.string.empty_state)))
            return
        }

        val scored = recipes
            .map { r -> Pair(r, r.ingredients.count { it in selected }) }
            .filter { it.second > 0 }
            .sortedWith(compareBy({ it.first.ingredients.size - it.second }, { -it.second }))

        val ready = scored.count { it.first.ingredients.size == it.second }
        resultTitle.text = when {
            ready == 1 -> "You can cook 1 recipe right now"
            ready > 1 -> "You can cook $ready recipes right now"
            else -> "Closest matches"
        }

        if (scored.isEmpty()) {
            results.addView(message(getString(R.string.no_match)))
            return
        }

        scored.take(20).forEach { (recipe, have) -> results.addView(recipeCard(recipe, have)) }
    }

    private fun recipeCard(r: Recipe, have: Int): View {
        val missing = r.ingredients.size - have
        val accent = when {
            missing == 0 -> color(R.color.leaf)
            missing <= 2 -> color(R.color.mustard)
            else -> color(R.color.leaf_soft)
        }

        val card = MaterialCardView(this)
        card.radius = dp(16).toFloat()
        card.cardElevation = 0f
        card.strokeWidth = dp(2)
        card.strokeColor = accent
        card.setCardBackgroundColor(color(R.color.white))
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.bottomMargin = dp(12)
        card.layoutParams = params

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(16), dp(14), dp(16), dp(14))

        val title = TextView(this)
        title.text = r.name
        title.textSize = 19f
        title.setTypeface(null, Typeface.BOLD)
        title.setTextColor(color(R.color.ink))
        box.addView(title)

        val meta = TextView(this)
        val veganText = if (r.vegan) ", vegan" else ""
        meta.text = "${r.category} cuisine, ${r.calories} kcal, about ${r.time} min$veganText"
        meta.setTextColor(color(R.color.muted))
        box.addView(meta)

        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        bar.max = 100
        bar.progress = have * 100 / r.ingredients.size
        bar.progressTintList = ColorStateList.valueOf(if (missing == 0) color(R.color.leaf) else color(R.color.mustard))
        val barParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(10))
        barParams.topMargin = dp(10)
        bar.layoutParams = barParams
        box.addView(bar)

        val status = TextView(this)
        status.text = if (missing == 0) "You have everything" else "Missing $missing of ${r.ingredients.size}"
        status.setTypeface(null, Typeface.BOLD)
        status.setTextColor(color(R.color.ink))
        status.setPadding(0, dp(6), 0, dp(4))
        box.addView(status)

        // Green = you have it, red = you need it
        val list = SpannableStringBuilder()
        r.ingredients.forEachIndexed { i, ing ->
            if (i > 0) list.append(",  ")
            val start = list.length
            list.append(ing)
            val c = if (ing in selected) color(R.color.leaf) else color(R.color.tomato)
            list.setSpan(ForegroundColorSpan(c), start, list.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val ingText = TextView(this)
        ingText.text = list
        box.addView(ingText)

        card.addView(box)
        return card
    }

    private fun message(text: String): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.setTextColor(color(R.color.muted))
        tv.setPadding(dp(4), dp(8), dp(4), dp(8))
        return tv
    }

    private fun color(id: Int) = ContextCompat.getColor(this, id)

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
