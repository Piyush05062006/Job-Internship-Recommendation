
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
    val description: String
)

class MainActivity : AppCompatActivity() {

    private lateinit var searchInput: EditText
    private lateinit var opportunitiesText: TextView

    private var selectedFilter = "all"
    private var opportunities: List<Opportunity> = emptyList()

    private val preferredSkills = setOf(
        "kotlin", "android", "java",
        "python", "sql", "machine learning"
    )

    // Replace this IP if your laptop's Wi-Fi IPv4 address changes.
    private val apiUrl = "http://10.176.105.95:8000/jobs"

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
            showOpportunities()
        }

        findViewById<Button>(R.id.internshipsButton).setOnClickListener {
            selectedFilter = "internship"
            showOpportunities()
        }

        findViewById<Button>(R.id.recommendationsButton).setOnClickListener {
            selectedFilter = "recommended"
            showOpportunities()
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) = Unit

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {
                showOpportunities()
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })

        opportunitiesText.text = "Loading opportunities..."
        loadJobs()
    }

    private fun loadJobs() {
        thread {
            var connection: HttpURLConnection? = null

            try {
                connection =
                    (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 10000
                        readTimeout = 10000
                        setRequestProperty("Accept", "application/json")
                    }

                val status = connection.responseCode

                if (status !in 200..299) {
                    throw Exception("Server returned HTTP $status")
                }

                val response = connection.inputStream
                    .bufferedReader()
                    .use { it.readText() }

                val jsonArray = JSONArray(response)
                val fetchedJobs = mutableListOf<Opportunity>()

                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)

                    val skillsJson =
                        item.optJSONArray("required_skills") ?: JSONArray()

                    val skills = (0 until skillsJson.length()).map { index ->
                        skillsJson.optString(index)
                    }

                    fetchedJobs.add(
                        Opportunity(
                            id = item.optString("id"),
                            title = item.optString(
                                "title", "Untitled opportunity"
                            ),
                            company = item.optString(
                                "company", "Unknown company"
                            ),
                            type = item.optString("job_type", "Job"),
                            skills = skills,
                            location = item.optString(
                                "location", "Not specified"
                            ),
                            description = item.optString("eligibility", "")
                        )
                    )
                }

                runOnUiThread {
                    opportunities = fetchedJobs
                    showOpportunities()
                }

            } catch (e: Exception) {
                runOnUiThread {
                    opportunitiesText.text =
                        "Unable to load opportunities.\n\n" +
                                "Check that your laptop and phone are connected " +
                                "and the backend is running.\n\n" +
                                "Error: ${e.message}"

                    Toast.makeText(
                        this,
                        "Could not connect to the jobs server",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun showOpportunities() {
        val query = searchInput.text.toString().trim()

        var results = opportunities.filter { opportunity ->

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
                opportunity.description
            )

            val matchesSearch = query.isBlank() ||
                    searchableFields.any { field ->
                        field.trim().contains(
                            query.trim(),
                            ignoreCase = true
                        )
                    }

            matchesType && matchesSearch
        }

        if (selectedFilter == "recommended") {
            results = results.sortedByDescending { opportunity ->
                opportunity.skills.count { skill ->
                    preferredSkills.any { preferred ->
                        skill.equals(preferred, ignoreCase = true)
                    }
                }
            }
        }

        opportunitiesText.text = buildString {
            appendLine(
                when (selectedFilter) {
                    "job" -> "AVAILABLE JOBS"
                    "internship" -> "AVAILABLE INTERNSHIPS"
                    "recommended" -> "RECOMMENDED FOR YOU"
                    else -> "ALL OPPORTUNITIES"
                }
            )

            appendLine()

            if (results.isEmpty()) {
                append("No matching opportunities found.")
            } else {
                results.forEachIndexed { index, opportunity ->
                    appendLine("${index + 1}. ${opportunity.title}")
                    appendLine("Company: ${opportunity.company}")
                    appendLine("Type: ${opportunity.type}")
                    appendLine("Location: ${opportunity.location}")
                    appendLine(
                        "Skills: ${opportunity.skills.joinToString(", ")}"
                    )

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
