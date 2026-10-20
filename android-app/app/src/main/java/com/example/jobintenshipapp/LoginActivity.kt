
package com.example.jobintenshipapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class LoginActivity : AppCompatActivity() {

    private lateinit var nameInput: EditText
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var authStatus: TextView
    private lateinit var loginButton: Button
    private lateinit var registerButton: Button

    private val baseUrl = "http://10.176.105.95:8000"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val preferences = getSharedPreferences("CareerConnect", MODE_PRIVATE)
        if (!preferences.getString("access_token", null).isNullOrBlank()) {
            openHome()
            return
        }

        setContentView(R.layout.activity_login)

        nameInput = findViewById(R.id.nameInput)
        emailInput = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        authStatus = findViewById(R.id.authStatus)
        loginButton = findViewById(R.id.loginButton)
        registerButton = findViewById(R.id.registerButton)

        loginButton.setOnClickListener { authenticate(false) }
        registerButton.setOnClickListener { authenticate(true) }
    }

    private fun authenticate(isRegister: Boolean) {
        val name = nameInput.text.toString().trim()
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString()

        if (email.isBlank() || password.isBlank()) {
            Toast.makeText(this, "Enter email and password", Toast.LENGTH_SHORT).show()
            return
        }

        if (isRegister && name.isBlank()) {
            Toast.makeText(this, "Enter your name", Toast.LENGTH_SHORT).show()
            return
        }

        loginButton.isEnabled = false
        registerButton.isEnabled = false
        authStatus.text = "Please wait..."

        thread {
            var connection: HttpURLConnection? = null

            try {
                val endpoint = if (isRegister) "/auth/register" else "/auth/login"
                val body = JSONObject().apply {
                    if (isRegister) put("name", name)
                    put("email", email)
                    put("password", password)
                }

                connection = (URL("$baseUrl$endpoint").openConnection()
                        as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 10000
                    readTimeout = 10000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                }

                connection.outputStream.use {
                    it.write(body.toString().toByteArray(Charsets.UTF_8))
                }

                val status = connection.responseCode
                val stream = if (status in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

                val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

                if (status !in 200..299) {
                    val message = try {
                        JSONObject(response).optString("detail", "HTTP $status")
                    } catch (_: Exception) {
                        "HTTP $status"
                    }
                    throw Exception(message)
                }

                if (isRegister) {
                    runOnUiThread {
                        authStatus.text = "Account created. Please log in."
                        passwordInput.text.clear()
                        Toast.makeText(
                            this,
                            "Registration successful. Please log in.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    val token = JSONObject(response).optString("access_token")
                    if (token.isBlank()) {
                        throw Exception("Login response did not contain an access token")
                    }

                    getSharedPreferences("CareerConnect", MODE_PRIVATE)
                        .edit()
                        .putString("access_token", token)
                        .apply()

                    runOnUiThread { openHome() }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    authStatus.text = e.message ?: "Request failed"
                    Toast.makeText(
                        this,
                        authStatus.text,
                        Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                connection?.disconnect()
                runOnUiThread {
                    loginButton.isEnabled = true
                    registerButton.isEnabled = true
                }
            }
        }
    }

    private fun openHome() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
