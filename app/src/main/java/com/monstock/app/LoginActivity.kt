package com.monstock.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.monstock.app.databinding.ActivityLoginBinding
import com.monstock.app.util.ShopPrefs

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        // Si déjà connecté, aller directement à l'écran principal
        if (auth.currentUser != null && ShopPrefs.getShopCode(this) != null) {
            goToMain()
            return
        }

        binding.btnLogin.setOnClickListener { doAuth(isRegister = false) }
        binding.btnRegister.setOnClickListener { doAuth(isRegister = true) }
    }

    private fun doAuth(isRegister: Boolean) {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val shopCode = binding.etShopCode.text.toString().trim()

        if (email.isEmpty() || password.length < 6 || shopCode.isEmpty()) {
            binding.tvError.text = "Remplis tous les champs (mot de passe : 6 caractères min., code boutique requis)."
            return
        }

        val task = if (isRegister) {
            auth.createUserWithEmailAndPassword(email, password)
        } else {
            auth.signInWithEmailAndPassword(email, password)
        }

        task.addOnSuccessListener {
            ShopPrefs.setShopCode(this, shopCode)
            goToMain()
        }.addOnFailureListener { e ->
            binding.tvError.text = e.localizedMessage ?: "Erreur de connexion"
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
