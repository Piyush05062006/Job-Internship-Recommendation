package com.example.jobintenshipapp

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

data class Opportunity(
    val id: String,
    val title: String,
    val company: String,
    val type: String,
    val skills: List<String>,
    val location: String,
    val description: String,
    val matchPercentage: Int? = null,
    val matchedSkills: List<String> = emptyList(),
    val missingSkills: List<String> = emptyList()
)

class MainActivity : AppCompatActivity() {

    private lateinit var searchInput: EditText
    private lateinit var opportunitiesText: TextView

    private var selectedFilter = "all"
    private var opportunities: List<Opportunity> = emptyList()
    private var showingRecommendations = false

    // Update this IP if your laptop's Wi-Fi IPv4 address changes.
    private val baseUrl = "http://10.176.105.95:8000"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val root = findViewById<View>(R.id.main)

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )
            view.setPadding(
                bars.left, bars.top, bars.right, bars.bottom
            )
            insets
        }

        searchInput = findViewById(R.id.searchInput)
        opportunitiesText = findViewById(R.id.opportunitiesText)

        findViewById<Button>(R.id.jobsButton).setOnClickListener {
            selectedFilter = "job"
            loadJobs()
        }

        findViewById<Button>(R.id.profileButton).setOnClickListener {
            startActivity(
                android.content.Intent(this, ProfileActivity::class.java)
            )
        }

        findViewById<Button>(R.id.internshipsButton).setOnClickListener {
            selectedFilter = "internship"
            loadJobs()
        }

        // Logout is intentionally not on the home screen.
        // It remains inside My Profile.

        findViewById<Button>(R.id.recommendationsButton)
            .setOnClickListener {
                selectedFilter = "recommended"
                loadRecommendations()
            }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?, start: Int, count: Int, after: Int
            ) = Unit

            override fun onTextChanged(
                s: CharSequence?, start: Int, before: Int, count: Int
            ) {
                showOpportunities()
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })

        opportunitiesText.text = "Loading opportunities..."
        loadJobs()
    }

    private fun loadJobs() {
        showingRecommendations = false
        opportunitiesText.text = "Loading opportunities..."

        fetchData(
            endpoint = "/jobs",
            tokenRequired = false
        ) { response ->
            val array = JSONArray(response)
            val fetched = mutableListOf<Opportunity>()

            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                fetched.add(parseOpportunity(item, false))
            }

            opportunities = fetched
            showOpportunities()
        }
    }

    private fun loadRecommendations() {
        showingRecommendations = true
        opportunitiesText.text = "Loading personalized recommendations..."

        val token = getSharedPreferences(
            "CareerConnect", MODE_PRIVATE
        ).getString("access_token", null)

        if (token.isNullOrBlank()) {
            opportunitiesText.text =
                "Please log in to see your recommendations."

            Toast.makeText(
                this,
                "Your login session is missing. Please log in again.",
                Toast.LENGTH_LONG
            ).show()

            startActivity(
                android.content.Intent(this, LoginActivity::class.java)
            )
            finish()
            return
        }

        fetchData(
            endpoint = "/recommendations",
            tokenRequired = true,
            token = token
        ) { response ->
            val array = JSONArray(response)
            val fetched = mutableListOf<Opportunity>()

            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                fetched.add(parseOpportunity(item, true))
            }

            opportunities = fetched
            showOpportunities()
        }
    }

    private fun fetchData(
        endpoint: String,
        tokenRequired: Boolean,
        token: String? = null,
        onSuccess: (String) -> Unit
    ) {
        thread {
            var connection: HttpURLConnection? = null

            try {
                connection = (
                        URL("$baseUrl$endpoint").openConnection()
                                as HttpURLConnection
                        ).apply {
                        requestMethod = "GET"
                        connectTimeout = 10000
                        readTimeout = 15000
                        setRequestProperty("Accept", "application/json")

                        if (tokenRequired && !token.isNullOrBlank()) {
                            setRequestProperty(
                                "Authorization", "Bearer $token"
                            )
                        }
                    }

                val status = connection.responseCode

                val stream = if (status in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

                val response = stream?.bufferedReader()
                    ?.use { it.readText() }.orEmpty()

                if (status !in 200..299) {
                    val message = try {
                        JSONObject(response).optString(
                            "detail", "Server returned HTTP $status"
                        )
                    } catch (_: Exception) {
                        "Server returned HTTP $status"
                    }

                    throw Exception(message)
                }

                runOnUiThread {
                    try {
                        onSuccess(response)
                    } catch (e: Exception) {
                        opportunitiesText.text =
                            "Unable to read server response: ${e.message}"
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    opportunitiesText.text =
                        "Unable to load data.\n\n" +
                                "Check your network and make sure the backend is running.\n\n" +
                                "Error: ${e.message}"

                    Toast.makeText(
                        this,
                        "Could not load data from the server",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun parseOpportunity(
        item: JSONObject,
        isRecommendation: Boolean
    ): Opportunity {
        val skills = readStringArray(
            item.optJSONArray("required_skills")
        )

        return Opportunity(
            id = item.optString(
                if (isRecommendation) "job_id" else "id"
            ),
            title = item.optString("title", "Untitled opportunity"),
            company = item.optString("company", "Unknown company"),
            type = item.optString("job_type", "Job"),
            skills = skills,
            location = item.optString("location", "Not specified"),
            description = item.optString("eligibility", ""),
            matchPercentage = if (isRecommendation) {
                if (
                    item.has("match_percentage") &&
                    !item.isNull("match_percentage")
                ) {
                    item.optInt("match_percentage")
                } else {
                    null
                }
            } else {
                null
            },
            matchedSkills = if (isRecommendation) {
                readStringArray(item.optJSONArray("matched_skills"))
            } else {
                emptyList()
            },
            missingSkills = if (isRecommendation) {
                readStringArray(item.optJSONArray("missing_skills"))
            } else {
                emptyList()
            }
        )
    }

    private fun readStringArray(array: JSONArray?): List<String> {
        if (array == null) return emptyList()

        return (0 until array.length()).mapNotNull { index ->
            val value = array.optString(index, "").trim()
            value.takeIf { it.isNotEmpty() && it != "null" }
        }
    }

    private fun showOpportunities() {
        val query = searchInput.text.toString().trim()

        val results = opportunities.filter { opportunity ->
            val matchesType = when (selectedFilter) {
                "job" -> !opportunity.type.contains(
                    "internship", ignoreCase = true
                )

                "internship" -> opportunity.type.contains(
                    "internship", ignoreCase = true
                )

                else -> true
            }

            val searchableFields = listOf(
                opportunity.title,
                opportunity.company,
                opportunity.type,
                opportunity.location,
                opportunity.skills.joinToString(" "),
                opportunity.description,
                opportunity.matchedSkills.joinToString(" "),
                opportunity.missingSkills.joinToString(" ")
            )

            val matchesSearch = query.isBlank() ||
                    searchableFields.any { field ->
                        field.contains(query, ignoreCase = true)
                    }

            matchesType && matchesSearch
        }

        opportunitiesText.text = buildString {
            appendLine(
                when (selectedFilter) {
                    "job" -> "AVAILABLE JOBS"
                    "internship" -> "AVAILABLE INTERNSHIPS"
                    "recommended" -> "PERSONALIZED RECOMMENDATIONS"
                    else -> "ALL OPPORTUNITIES"
                }
            )
            appendLine()

            if (results.isEmpty()) {
                append(
                    if (
                        selectedFilter == "recommended" &&
                        query.isBlank()
                    ) {
                        "No recommendations found. Check that your candidate profile " +
                                "has been saved and that jobs exist in the database."
                    } else {
                        "No matching opportunities found."
                    }
                )
            } else {
                results.forEachIndexed { index, opportunity ->
                    appendLine("${index + 1}. ${opportunity.title}")
                    appendLine("Company: ${opportunity.company}")
                    appendLine("Type: ${opportunity.type}")
                    appendLine("Location: ${opportunity.location}")
                    appendLine("Skills: ${opportunity.skills.joinToString(", ")}")

                    if (showingRecommendations) {
                        opportunity.matchPercentage?.let {
                            appendLine("Match: $it%")
                        }

                        appendLine(
                            "Matched skills: ${
                                opportunity.matchedSkills
                                    .joinToString(", ")
                                    .ifBlank { "None listed" }
                            }"
                        )

                        appendLine(
                            "Missing skills: ${
                                opportunity.missingSkills
                                    .joinToString(", ")
                                    .ifBlank { "None listed" }
                            }"
                        )
                    }

                    if (opportunity.description.isNotBlank()) {
                        appendLine(
                            "Eligibility: ${opportunity.description}"
                        )
                    }

                    appendLine()
                }
            }
        }
    }
}
