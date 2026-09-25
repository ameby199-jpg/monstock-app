package com.monstock.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.monstock.app.databinding.FragmentLoginBinding
import com.monstock.app.model.Employee
import com.monstock.app.util.EmployeeSession
import com.monstock.app.util.FirebaseRepo
import com.monstock.app.util.ShopPrefs

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    /** Appelé une fois la connexion réussie, pour que MainActivity affiche l'app normale. */
    var onLoginSuccess: (() -> Unit)? = null

    private var isBootstrap = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val shopCode = ShopPrefs.getShopCode(requireContext()) ?: return
        val repo = FirebaseRepo(shopCode)

        checkFirstLaunch(repo)

        binding.btnLogin.setOnClickListener {
            val code = binding.etEmployeeCode.text.toString().trim()
            if (code.isEmpty()) {
                showError("Entre un code.")
                return@setOnClickListener
            }
            if (isBootstrap) {
                val name = binding.etEmployeeName.text.toString().trim()
                if (name.isEmpty()) {
                    showError("Entre ton nom.")
                    return@setOnClickListener
                }
                repo.addEmployee(name, code, "responsable") { msg -> showError(msg) }
                EmployeeSession.login(requireContext(), Employee(name = name, code = code, role = "responsable"))
                onLoginSuccess?.invoke()
            } else {
                repo.getEmployeesOnce(
                    onResult = { employees ->
                        val match = employees.firstOrNull { it.code == code }
                        if (match != null) {
                            EmployeeSession.login(requireContext(), match)
                            onLoginSuccess?.invoke()
                        } else {
                            showError("Code incorrect.")
                        }
                    },
                    onError = { msg -> showError(msg) }
                )
            }
        }
    }

    /** S'il n'y a encore aucun employé enregistré, on propose de créer le compte responsable. */
    private fun checkFirstLaunch(repo: FirebaseRepo) {
        repo.getEmployeesOnce(
            onResult = { employees ->
                if (employees.isEmpty()) {
                    isBootstrap = true
                    binding.tvLoginTitle.text = "Bienvenue"
                    binding.tvLoginSubtitle.text = "Aucun compte pour l'instant : crée ton code responsable."
                    binding.etEmployeeName.visibility = View.VISIBLE
                    binding.btnLogin.text = "Créer mon compte"
                }
            },
            onError = { }
        )
    }

    private fun showError(msg: String) {
        binding.tvLoginError.text = msg
        binding.tvLoginError.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
