package com.monstock.app.ui

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.ListenerRegistration
import com.monstock.app.R
import com.monstock.app.adapter.OrderAdapter
import com.monstock.app.adapter.SellProductAdapter
import com.monstock.app.databinding.DialogCartBinding
import com.monstock.app.databinding.DialogColorPickerBinding
import com.monstock.app.databinding.DialogOrdersBinding
import com.monstock.app.databinding.DialogSellBinding
import com.monstock.app.databinding.DialogSettingsBinding
import com.monstock.app.databinding.DialogThemePickerBinding
import com.monstock.app.databinding.FragmentSellBinding
import com.monstock.app.model.Order
import com.monstock.app.model.OrderLine
import com.monstock.app.model.Product
import com.monstock.app.model.Sale
import com.monstock.app.util.BackgroundPrefs
import com.monstock.app.util.CardStylePrefs
import com.monstock.app.util.CurrencyFormatter
import com.monstock.app.util.EmployeeSession
import com.monstock.app.util.FirebaseRepo
import com.monstock.app.util.OrderButtonPrefs
import com.monstock.app.util.ShopPrefs
import com.monstock.app.util.ThemePrefs

class SellFragment : Fragment() {

    private var _binding: FragmentSellBinding? = null
    private val binding get() = _binding!!
    private lateinit var repo: FirebaseRepo
    private lateinit var adapter: SellProductAdapter
    private var productsListener: ListenerRegistration? = null
    private var salesListener: ListenerRegistration? = null
    private var ordersListener: ListenerRegistration? = null

    private var latestProducts: List<Product> = emptyList()
    private var salesCountByProductId: Map<String, Long> = emptyMap()
    private var latestOrders: List<Order> = emptyList()

    // Index alphabétique : (lettre, position du premier produit de cette lettre dans la grille affichée).
    private var alphaPositions: List<Pair<String, Int>> = emptyList()
    private val alphaPalette = listOf(
        "#FF5252", "#FFAB40", "#FFEB3B", "#69F0AE", "#40C4FF",
        "#B388FF", "#FF80AB", "#18FFFF", "#B2FF59", "#FF9E80"
    )
    private fun alphaColor(i: Int): Int = Color.parseColor(alphaPalette[i % alphaPalette.size])

    // Panier en cours de construction : plusieurs produits peuvent être ajoutés avant de
    // valider la commande en une seule fois.
    private val draftCart = mutableListOf<OrderLine>()

    // Tant que la fenêtre des commandes en attente est ouverte, on la tient à jour en direct.
    private var ordersDialogAdapter: OrderAdapter? = null

    private val pickBackground = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                val bitmap = MediaStore.Images.Media.getBitmap(requireContext().contentResolver, uri)
                BackgroundPrefs.saveCustomBackground(requireContext(), "home", bitmap)
                binding.ivBackground.setImageBitmap(bitmap)
            } catch (e: Exception) {
                // Ignoré : l'utilisateur peut réessayer
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSellBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val shopCode = ShopPrefs.getShopCode(requireContext()) ?: return
        repo = FirebaseRepo(shopCode)

        BackgroundPrefs.applyBackground(requireContext(), "home", binding.ivBackground)
        binding.cardOrders.setCardBackgroundColor(OrderButtonPrefs.getColor(requireContext()))
        binding.btnSettings.setOnClickListener { showSettingsDialog() }
        binding.btnValidateCart.setOnClickListener { showCartDialog() }
        updateCartButton()

        adapter = SellProductAdapter(emptyList()) { showSellDialog(it) }
        binding.recyclerView.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.recyclerView.adapter = adapter
        setupAlphaIndexTouch()

        productsListener = repo.listenProducts { products ->
            latestProducts = products
            refreshList()
        }
        salesListener = repo.listenSales { sales ->
            salesCountByProductId = sales.groupBy { it.productId }.mapValues { (_, list) -> list.sumOf { it.quantity } }
            refreshList()
        }

        binding.btnOrders.setOnClickListener { showOrdersDialog() }
        ordersListener = repo.listenOrders { orders ->
            latestOrders = orders
            if (orders.isEmpty()) {
                binding.tvOrdersBadge.visibility = View.GONE
            } else {
                binding.tvOrdersBadge.visibility = View.VISIBLE
                binding.tvOrdersBadge.text = orders.size.toString()
            }
            // Si la fenêtre des commandes est ouverte, on la met à jour tout de suite
            // (sinon une commande "prise" ou "annulée" restait affichée jusqu'à réouverture).
            ordersDialogAdapter?.updateData(orders)
        }
    }

    /** Les produits les plus vendus apparaissent en premier ; les jamais vendus restent en bas. */
    private fun refreshList() {
        val inStock = latestProducts.filter { it.quantity > 0 }
        val sorted = inStock.sortedWith(
            compareByDescending<Product> { salesCountByProductId[it.id] ?: 0L }
                .thenBy { it.name.lowercase() }
        )
        adapter.updateData(sorted)
        binding.tvEmpty.visibility = if (sorted.isEmpty()) View.VISIBLE else View.GONE
        updateAlphaIndex(sorted)
    }

    /** Première lettre du nom, sans accent et en majuscule ("Norvège" -> "N", "épices" -> "E"). */
    private fun initialOf(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return "#"
        val decomposed = java.text.Normalizer.normalize(trimmed.substring(0, 1), java.text.Normalizer.Form.NFD)
        val c = decomposed.firstOrNull { it.isLetterOrDigit() } ?: return "#"
        return c.uppercaseChar().toString()
    }

    /**
     * Raccourcis à droite de la grille : seulement les lettres par lesquelles commencent les produits
     * (pas de A à Z complet). Toucher ou glisser le doigt sur une lettre saute au premier produit
     * qui commence par cette lettre.
     */
    private fun updateAlphaIndex(sorted: List<Product>) {
        val firstPositions = linkedMapOf<String, Int>()
        sorted.forEachIndexed { index, product ->
            val letter = initialOf(product.name)
            if (!firstPositions.containsKey(letter)) firstPositions[letter] = index
        }
        alphaPositions = firstPositions.entries.sortedBy { it.key }.map { it.key to it.value }

        val strip = binding.llAlphaIndex
        strip.removeAllViews()
        if (alphaPositions.size < 2) {
            strip.visibility = View.GONE
            return
        }
        val density = resources.displayMetrics.density
        // Beaucoup de lettres : on réduit un peu la taille pour que la bande tienne à l'écran.
        val letterSize = if (alphaPositions.size > 14) 12f else 16f
        val letterPad = if (alphaPositions.size > 14) 2 else 5
        alphaPositions.forEachIndexed { i, (letter, _) ->
            strip.addView(TextView(requireContext()).apply {
                text = letter
                textSize = letterSize
                gravity = android.view.Gravity.CENTER
                setTextColor(alphaColor(i))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setPadding(0, (letterPad * density).toInt(), 0, (letterPad * density).toInt())
            })
        }
        strip.visibility = View.VISIBLE
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun setupAlphaIndexTouch() {
        val strip = binding.llAlphaIndex
        strip.setOnTouchListener { v, event ->
            if (alphaPositions.isEmpty()) return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val height = v.height.toFloat().coerceAtLeast(1f)
                    val index = ((event.y / height) * alphaPositions.size).toInt()
                        .coerceIn(0, alphaPositions.size - 1)
                    val (letter, position) = alphaPositions[index]
                    (binding.recyclerView.layoutManager as? GridLayoutManager)
                        ?.scrollToPositionWithOffset(position, 0)
                    binding.tvAlphaBubble.text = letter
                    binding.tvAlphaBubble.backgroundTintList =
                        android.content.res.ColorStateList.valueOf(alphaColor(index))
                    binding.tvAlphaBubble.visibility = View.VISIBLE
                    for (i in 0 until strip.childCount) {
                        (strip.getChildAt(i) as? TextView)?.apply {
                            setTextColor(if (i == index) Color.WHITE else alphaColor(i))
                            textSize = if (i == index) 22f else (if (alphaPositions.size > 14) 12f else 16f)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    binding.tvAlphaBubble.visibility = View.GONE
                    for (i in 0 until strip.childCount) {
                        (strip.getChildAt(i) as? TextView)?.apply {
                            setTextColor(alphaColor(i))
                            textSize = if (alphaPositions.size > 14) 12f else 16f
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun updateCartButton() {
        if (draftCart.isEmpty()) {
            binding.btnValidateCart.visibility = View.GONE
        } else {
            val total = draftCart.sumOf { it.unitPrice * it.quantity }
            binding.btnValidateCart.text = "🛒 Valider (${draftCart.size}) — ${CurrencyFormatter.format(total)}"
            binding.btnValidateCart.visibility = View.VISIBLE
        }
    }

    /**
     * Fenêtre de vente d'un produit. Le gros bouton ✅ VENDRE conclut la vente tout de suite.
     * Deux façons de le mettre en attente plutôt que de le vendre tout de suite :
     * - 🕑 Commande : ce produit seul est envoyé directement dans la liste d'attente ⏰.
     * - 🛒 Panier : ce produit rejoint le panier en cours, pour former une commande à plusieurs
     *   produits une fois que le client a fini de choisir (bouton 🛒 flottant en bas).
     */
    private fun showSellDialog(product: Product) {
        val dialogBinding = DialogSellBinding.inflate(layoutInflater)
        dialogBinding.tvAvailableStock.text = "En stock : ${product.quantity} unité(s)"
        dialogBinding.tvUnitPrice.text = "Prix unitaire : ${CurrencyFormatter.format(product.price)}"
        dialogBinding.etQuantitySold.setText("1")

        // Profil client : 👨 sélectionné par défaut, l'utilisateur peut choisir un autre avant de valider.
        var selectedProfile = "👨🏿"
        val profileViews = listOf(
            dialogBinding.tvProfileMan, dialogBinding.tvProfileWoman,
            dialogBinding.tvProfileBoy, dialogBinding.tvProfileGirl
        )
        fun selectProfile(view: android.widget.TextView) {
            selectedProfile = view.text.toString()
            profileViews.forEach { it.background = if (it == view) ContextCompat.getDrawable(requireContext(), R.drawable.bg_profile_selected) else null }
        }
        profileViews.forEach { view -> view.setOnClickListener { selectProfile(view) } }

        // Produits dérivés (suppléments) : on peut les parcourir et les ajouter ici, sans quitter la
        // fenêtre ni passer par le panier. Touche = +1 (dans la limite du stock), appui long = retirer.
        val derivedCounts = mutableMapOf<String, Long>()
        // Tous les articles ajoutables en plus du produit principal : dérivés ET produits normaux,
        // rangés dans deux onglets (🧩 Dérivés / 🛍️ Produits), avec les mêmes quantités partagées.
        val derivedProducts = latestProducts
            .filter { it.quantity > 0 && it.id != product.id }
            .sortedBy { it.name.lowercase() }
        var showDerivedTab = true

        fun derivedTotal(): Double = derivedProducts.sumOf { (derivedCounts[it.id] ?: 0L) * it.price }

        fun updateTotal() {
            val qty = dialogBinding.etQuantitySold.text.toString().toLongOrNull() ?: 0L
            dialogBinding.tvTotalPrice.text = "Total : ${CurrencyFormatter.format(qty * product.price + derivedTotal())}"
        }
        updateTotal()

        val density = resources.displayMetrics.density
        fun styleDerivedRow(row: TextView, item: Product) {
            val count = derivedCounts[item.id] ?: 0L
            val label = if (count > 0) "✓ ×$count  ${item.name}" else item.name
            row.text = "$label\n${CurrencyFormatter.format(item.price)}"
            row.setBackgroundColor(Color.parseColor(if (count > 0) "#554CAF50" else "#22FFFFFF"))
        }
        fun renderExtrasList() {
            val list = dialogBinding.llDerivedList
            list.removeAllViews()
            val selectedColor = Color.parseColor("#554CAF50")
            dialogBinding.tvTabDerived.setBackgroundColor(if (showDerivedTab) selectedColor else Color.parseColor("#22FFFFFF"))
            dialogBinding.tvTabOthers.setBackgroundColor(if (!showDerivedTab) selectedColor else Color.parseColor("#22FFFFFF"))
            val shown = derivedProducts.filter { it.isDerived == showDerivedTab }
            if (shown.isEmpty()) {
                list.addView(TextView(requireContext()).apply {
                    text = if (showDerivedTab)
                        "Aucun dérivé pour l'instant. Coche « Dérivé » sur un produit dans Stock."
                    else "Aucun autre produit en stock."
                    textSize = 10f
                    setTextColor(Color.parseColor("#9E9E9E"))
                })
                return
            }
            shown.forEach { item ->
                val row = TextView(requireContext()).apply {
                    textSize = 12f
                    setPadding((8 * density).toInt(), (6 * density).toInt(), (8 * density).toInt(), (6 * density).toInt())
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = (4 * density).toInt() }
                }
                styleDerivedRow(row, item)
                row.setOnClickListener {
                    val current = derivedCounts[item.id] ?: 0L
                    if (current < item.quantity) {
                        derivedCounts[item.id] = current + 1
                        styleDerivedRow(row, item)
                        updateTotal()
                    } else {
                        android.widget.Toast.makeText(requireContext(), "Stock maximum atteint pour ${item.name}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                row.setOnLongClickListener {
                    derivedCounts.remove(item.id)
                    styleDerivedRow(row, item)
                    updateTotal()
                    true
                }
                list.addView(row)
            }
        }
        dialogBinding.tvTabDerived.setOnClickListener { showDerivedTab = true; renderExtrasList() }
        dialogBinding.tvTabOthers.setOnClickListener { showDerivedTab = false; renderExtrasList() }
        // Au départ : l'onglet Dérivés, sauf s'il est vide et qu'il y a d'autres produits.
        if (derivedProducts.none { it.isDerived } && derivedProducts.isNotEmpty()) showDerivedTab = false
        renderExtrasList()

        dialogBinding.btnMinus.setOnClickListener {
            val current = dialogBinding.etQuantitySold.text.toString().toLongOrNull() ?: 1L
            if (current > 1) dialogBinding.etQuantitySold.setText((current - 1).toString())
        }
        dialogBinding.btnPlus.setOnClickListener {
            val current = dialogBinding.etQuantitySold.text.toString().toLongOrNull() ?: 0L
            if (current < product.quantity) dialogBinding.etQuantitySold.setText((current + 1).toString())
        }
        dialogBinding.etQuantitySold.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { updateTotal() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Vendre : ${product.name}")
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.cancel, null)
            .create()

        fun currentPaymentMethod(): String = when (dialogBinding.rgPayment.checkedRadioButtonId) {
            dialogBinding.rbOrangeMoney.id -> "Orange Money"
            dialogBinding.rbWave.id -> "Wave"
            else -> "Espèces"
        }

        fun derivedLines(): List<OrderLine> = derivedProducts.mapNotNull { item ->
            val count = derivedCounts[item.id] ?: 0L
            if (count > 0) OrderLine(item.id, item.name, count, item.price, item.costPrice, selectedProfile, currentPaymentMethod()) else null
        }
        fun extrasLabel(): String {
            val n = derivedCounts.values.sum()
            return if (n > 0) " + $n article(s) en plus" else ""
        }

        dialogBinding.btnSellNow.setOnClickListener {
            val qtySold = dialogBinding.etQuantitySold.text.toString().toLongOrNull() ?: 0L
            val paymentMethod = currentPaymentMethod()
            if (qtySold in 1..product.quantity) {
                val employee = EmployeeSession.getCurrentName(requireContext())
                val onSaleError: (String) -> Unit = { msg ->
                    android.widget.Toast.makeText(requireContext(), msg, android.widget.Toast.LENGTH_LONG).show()
                }
                repo.recordSale(
                    product, qtySold, paymentMethod,
                    employeeName = employee,
                    customerProfile = selectedProfile,
                    onError = onSaleError
                )
                // Chaque dérivé sélectionné est vendu avec le même client, le même paiement et le même employé.
                derivedProducts.forEach { item ->
                    val count = derivedCounts[item.id] ?: 0L
                    if (count in 1..item.quantity) {
                        repo.recordSale(
                            item, count, paymentMethod,
                            employeeName = employee,
                            customerProfile = selectedProfile,
                            onError = onSaleError
                        )
                    }
                }
                android.widget.Toast.makeText(
                    requireContext(),
                    "✅ Vente réussie : ${product.name} x$qtySold${extrasLabel()}",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                dialog.dismiss()
            }
        }

        // Commande simple : un seul produit, envoyé directement dans la liste d'attente ⏰.
        dialogBinding.btnOrderSingle.setOnClickListener {
            val qty = dialogBinding.etQuantitySold.text.toString().toLongOrNull() ?: 0L
            if (qty in 1..product.quantity) {
                repo.addOrder(
                    listOf(OrderLine(product.id, product.name, qty, product.price, product.costPrice, selectedProfile, currentPaymentMethod())) + derivedLines(),
                    onError = { msg ->
                        android.widget.Toast.makeText(requireContext(), msg, android.widget.Toast.LENGTH_LONG).show()
                    },
                    onSuccess = {
                        android.widget.Toast.makeText(
                            requireContext(),
                            "✅ Commande enregistrée : ${product.name} x$qty${extrasLabel()}",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                )
                dialog.dismiss()
            }
        }

        // Panier : ce produit rejoint une commande à plusieurs produits, à valider plus tard.
        dialogBinding.btnAddToCart.setOnClickListener {
            val qty = dialogBinding.etQuantitySold.text.toString().toLongOrNull() ?: 0L
            if (qty in 1..product.quantity) {
                draftCart.add(OrderLine(product.id, product.name, qty, product.price, product.costPrice, selectedProfile, currentPaymentMethod()))
                draftCart.addAll(derivedLines())
                updateCartButton()
                android.widget.Toast.makeText(
                    requireContext(),
                    "🛒 Ajouté au panier : ${product.name} x$qty${extrasLabel()}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    /** Résumé du panier en cours : un ou plusieurs produits, à valider en une seule commande. */
    private fun showCartDialog() {
        val dialogBinding = DialogCartBinding.inflate(layoutInflater)
        dialogBinding.llCartItems.removeAllViews()
        draftCart.forEach { line ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            val nameView = TextView(requireContext()).apply {
                text = "${line.productName} x${line.quantity}"
                textSize = 15f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val priceView = TextView(requireContext()).apply {
                text = CurrencyFormatter.format(line.unitPrice * line.quantity)
                textSize = 15f
                setTextColor(Color.parseColor("#666666"))
            }
            row.addView(nameView)
            row.addView(priceView)
            dialogBinding.llCartItems.addView(row)
        }
        val total = draftCart.sumOf { it.unitPrice * it.quantity }
        dialogBinding.tvCartTotal.text = "Total : ${CurrencyFormatter.format(total)}"

        AlertDialog.Builder(requireContext())
            .setTitle("Commande en cours")
            .setView(dialogBinding.root)
            .setPositiveButton("Valider la commande") { _, _ ->
                val itemsToSend = draftCart.toList()
                repo.addOrder(
                    itemsToSend,
                    onError = { msg ->
                        android.widget.Toast.makeText(requireContext(), msg, android.widget.Toast.LENGTH_LONG).show()
                    },
                    onSuccess = {
                        android.widget.Toast.makeText(
                            requireContext(),
                            "✅ Commande envoyée (${itemsToSend.size} article(s))",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                )
                draftCart.clear()
                updateCartButton()
            }
            .setNegativeButton("Vider le panier") { _, _ ->
                draftCart.clear()
                updateCartButton()
            }
            .setNeutralButton("Continuer les achats", null)
            .show()
    }

    private fun showOrdersDialog() {
        val dialogBinding = DialogOrdersBinding.inflate(layoutInflater)
        val ordersAdapter = OrderAdapter(
            latestOrders,
            onCancel = { order ->
                repo.cancelOrder(order.id) { msg ->
                    android.widget.Toast.makeText(requireContext(), msg, android.widget.Toast.LENGTH_LONG).show()
                }
                android.widget.Toast.makeText(
                    requireContext(),
                    "✅ Commande annulée",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            },
            onTake = { order ->
                // Les quantités viennent des données déjà en mémoire (pas d'appel réseau) :
                // ça marche donc même sans connexion, et la commande disparaît tout de suite.
                val quantities = order.items.associate { line ->
                    line.productId to (latestProducts.firstOrNull { it.id == line.productId }?.quantity ?: 0L)
                }
                repo.takeOrder(
                    order, quantities,
                    employeeName = EmployeeSession.getCurrentName(requireContext()),
                    onError = { msg ->
                        android.widget.Toast.makeText(requireContext(), msg, android.widget.Toast.LENGTH_LONG).show()
                    },
                    onSuccess = {
                        val summary = order.items.joinToString(", ") { "${it.productName} x${it.quantity}" }
                        android.widget.Toast.makeText(
                            requireContext(),
                            "✅ Achat réalisé : $summary",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                )
            }
        )
        ordersDialogAdapter = ordersAdapter
        dialogBinding.recyclerViewOrders.layoutManager = LinearLayoutManager(requireContext())
        dialogBinding.recyclerViewOrders.adapter = ordersAdapter
        dialogBinding.tvOrdersEmpty.visibility = if (latestOrders.isEmpty()) View.VISIBLE else View.GONE

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Commandes en attente")
            .setView(dialogBinding.root)
            .setNegativeButton("Fermer", null)
            .create()
        dialog.setOnDismissListener { ordersDialogAdapter = null }
        dialog.show()
    }

    private fun showSettingsDialog() {
        val dialogBinding = DialogSettingsBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Réglages")
            .setView(dialogBinding.root)
            .setNegativeButton("Fermer", null)
            .create()

        dialogBinding.optBackground.setOnClickListener {
            dialog.dismiss()
            pickBackground.launch("image/*")
        }
        dialogBinding.optBackgroundColor.setOnClickListener {
            dialog.dismiss()
            showColorPickerDialog { color ->
                BackgroundPrefs.saveCustomColor(requireContext(), "sell", color)
                BackgroundPrefs.applyBackground(requireContext(), "sell", binding.ivBackground)
            }
        }
        dialogBinding.optTheme.setOnClickListener {
            dialog.dismiss()
            showThemePickerDialog()
        }
        dialogBinding.optNameColor.setOnClickListener {
            dialog.dismiss()
            showColorPickerDialog { color ->
                CardStylePrefs.setNameColor(requireContext(), color)
                adapter.refresh()
            }
        }
        dialogBinding.optPriceColor.setOnClickListener {
            dialog.dismiss()
            showColorPickerDialog { color ->
                CardStylePrefs.setPriceColor(requireContext(), color)
                adapter.refresh()
            }
        }
        dialogBinding.optOrdersColor.setOnClickListener {
            dialog.dismiss()
            showColorPickerDialog { color ->
                OrderButtonPrefs.setColor(requireContext(), color)
                binding.cardOrders.setCardBackgroundColor(color)
            }
        }
        dialog.show()
    }

    private fun showThemePickerDialog() {
        val dialogBinding = DialogThemePickerBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Couleur de l'application")
            .setView(dialogBinding.root)
            .setNegativeButton("Fermer", null)
            .create()

        val swatches = listOf(
            dialogBinding.swatchBlue to ThemePrefs.AppTheme.BLUE,
            dialogBinding.swatchGreen to ThemePrefs.AppTheme.GREEN,
            dialogBinding.swatchOrange to ThemePrefs.AppTheme.ORANGE,
            dialogBinding.swatchPurple to ThemePrefs.AppTheme.PURPLE,
            dialogBinding.swatchRed to ThemePrefs.AppTheme.RED,
            dialogBinding.swatchTeal to ThemePrefs.AppTheme.TEAL
        )
        swatches.forEach { (view, theme) ->
            view.setOnClickListener {
                ThemePrefs.setSelectedTheme(requireContext(), theme)
                dialog.dismiss()
                requireActivity().recreate()
            }
        }
        dialog.show()
    }

    private fun showColorPickerDialog(onColorChosen: (Int) -> Unit) {
        val dialogBinding = DialogColorPickerBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Choisir une couleur")
            .setView(dialogBinding.root)
            .setNegativeButton("Fermer", null)
            .create()

        val swatches = listOf(
            dialogBinding.swatchBlack to Color.parseColor("#000000"),
            dialogBinding.swatchGray to Color.parseColor("#666666"),
            dialogBinding.swatchBlue2 to Color.parseColor("#1565C0"),
            dialogBinding.swatchGreen2 to Color.parseColor("#2E7D32"),
            dialogBinding.swatchOrange2 to Color.parseColor("#EF6C00"),
            dialogBinding.swatchPurple2 to Color.parseColor("#6A1B9A"),
            dialogBinding.swatchRed2 to Color.parseColor("#C62828"),
            dialogBinding.swatchTeal2 to Color.parseColor("#00838F")
        )
        swatches.forEach { (view, color) ->
            view.setOnClickListener {
                onColorChosen(color)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        productsListener?.remove()
        salesListener?.remove()
        ordersListener?.remove()
        ordersDialogAdapter = null
        _binding = null
    }
}
