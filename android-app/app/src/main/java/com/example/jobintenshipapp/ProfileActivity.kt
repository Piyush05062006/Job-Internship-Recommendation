
package com.example.jobintenshipapp

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class ProfileActivity : AppCompatActivity() {

    private lateinit var educationInput: EditText
    private lateinit var skillsInput: EditText
    private lateinit var locationsInput: EditText
    private lateinit var jobTypeInput: EditText
    private lateinit var profileStatus: TextView

    private val baseUrl = "http://10.176.105.95:8000"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        educationInput = findViewById(R.id.educationInput)
        skillsInput = findViewById(R.id.skillsInput)
        locationsInput = findViewById(R.id.locationsInput)
        jobTypeInput = findViewById(R.id.jobTypeInput)
        profileStatus = findViewById(R.id.profileStatus)

        setEditing(false)

        findViewById<Button>(R.id.editProfileButton)
            .setOnClickListener {
                setEditing(true)
                profileStatus.text =
                    "Edit your details and tap Save Profile."
            }

        findViewById<Button>(R.id.saveProfileButton)
            .setOnClickListener {
                saveProfile()
            }

        findViewById<Button>(R.id.backButton)
            .setOnClickListener {
                finish()
            }

        findViewById<Button>(R.id.logoutButton).setOnClickListener {
            getSharedPreferences("CareerConnect", MODE_PRIVATE)
                .edit()
                .remove("access_token")
                .apply()

            val intent = android.content.Intent(
                this,
                LoginActivity::class.java
            ).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                        android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

            startActivity(intent)
            finish()
        }


        loadProfile()
    }

    private fun setEditing(enabled: Boolean) {
        educationInput.isEnabled = enabled
        skillsInput.isEnabled = enabled
        locationsInput.isEnabled = enabled
        jobTypeInput.isEnabled = enabled

        findViewById<Button>(R.id.saveProfileButton)
            .isEnabled = enabled

        findViewById<Button>(R.id.editProfileButton)
            .isEnabled = !enabled
    }

    private fun getToken(): String? {
        return getSharedPreferences(
            "CareerConnect",
            MODE_PRIVATE
        ).getString("access_token", null)
    }

    private fun loadProfile() {
        profileStatus.text = "Loading profile..."

        request("GET", null) { response ->
            val profile = JSONObject(response)

            educationInput.setText(
                profile.optString("education", "")
            )

            skillsInput.setText(
                readArray(profile.optJSONArray("skills"))
            )

            locationsInput.setText(
                readArray(
                    profile.optJSONArray("preferred_locations")
                )
            )

            jobTypeInput.setText(
                profile.optString(
                    "preferred_job_type",
                    "Internship"
                )
            )

            setEditing(false)
            profileStatus.text = "Profile loaded."
        }
    }

    private fun saveProfile() {
        val education = educationInput.text.toString().trim()
        val skills = splitInput(skillsInput.text.toString())
        val locations = splitInput(locationsInput.text.toString())
        val jobType = jobTypeInput.text.toString().trim()

        if (
            education.isBlank() ||
            skills.isEmpty() ||
            locations.isEmpty() ||
            jobType.isBlank()
        ) {
            Toast.makeText(
                this,
                "Please fill in all fields.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val body = JSONObject().apply {
            put("education", education)
            put("skills", JSONArray(skills))
            put("preferred_locations", JSONArray(locations))
            put("preferred_job_type", jobType)
        }

        profileStatus.text = "Saving profile..."

        request("PUT", body.toString()) {
            setEditing(false)
            profileStatus.text = "Profile saved successfully!"

            Toast.makeText(
                this@ProfileActivity,
                "Profile updated",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun splitInput(value: String): List<String> {
        return value.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
    }

    private fun readArray(array: JSONArray?): String {
        if (array == null) return ""

        return (0 until array.length())
            .map { array.optString(it) }
            .joinToString(", ")
    }

    private fun request(
        method: String,
        body: String?,
        onSuccess: (String) -> Unit
    ) {
        val token = getToken()

        if (token.isNullOrBlank()) {
            profileStatus.text =
                "Login session missing. Please log in again."
            return
        }

        thread {
            var connection: HttpURLConnection? = null

            try {
                connection = (
                        URL("$baseUrl/profile").openConnection()
                                as HttpURLConnection
                        ).apply {
                        requestMethod = method
                        connectTimeout = 10000
                        readTimeout = 15000

                        setRequestProperty(
                            "Accept",
                            "application/json"
                        )

                        setRequestProperty(
                            "Authorization",
                            "Bearer $token"
                        )

                        if (body != null) {
                            doOutput = true

                            setRequestProperty(
                                "Content-Type",
                                "application/json"
                            )

                            outputStream.use {
                                it.write(
                                    body.toByteArray(Charsets.UTF_8)
                                )
                            }
                        }
                    }

                val status = connection.responseCode

                val stream = if (status in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

                val response = stream
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    .orEmpty()

                if (status !in 200..299) {
                    val message = try {
                        JSONObject(response).optString(
                            "detail",
                            "HTTP $status"
                        )
                    } catch (_: Exception) {
                        "HTTP $status"
                    }

                    throw Exception(message)
                }

                runOnUiThread {
                    try {
                        onSuccess(response)
                    } catch (e: Exception) {
                        profileStatus.text =
                            "Could not read profile: ${e.message}"
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    profileStatus.text =
                        "Request failed: ${e.message}"
                }
            } finally {
                connection?.disconnect()
            }
        }
    }
}
