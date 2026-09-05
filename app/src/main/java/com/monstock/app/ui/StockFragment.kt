package com.monstock.app.ui

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.firebase.firestore.ListenerRegistration
import com.monstock.app.R
import com.monstock.app.adapter.ProductAdapter
import com.monstock.app.databinding.DialogAddProductBinding
import com.monstock.app.databinding.DialogSellBinding
import com.monstock.app.databinding.FragmentStockBinding
import com.monstock.app.model.Product
import com.monstock.app.util.FirebaseRepo
import com.monstock.app.util.ShopPrefs

class StockFragment : Fragment() {

    private var _binding: FragmentStockBinding? = null
    private val binding get() = _binding!!
    private lateinit var repo: FirebaseRepo
    private lateinit var adapter: ProductAdapter
    private var listener: ListenerRegistration? = null

    // Photo en cours de sélection pour le dialogue d'ajout/édition
    private var pendingPhoto: Bitmap? = null
    private var dialogPreviewSetter: ((Bitmap) -> Unit)? = null
    // Produit dont on est en train de changer la photo (édition directe depuis la liste)
    private var productBeingPhotographed: Product? = null

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) onPhotoPicked(bitmap)
    }

    private val pickFromGallery = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                val bitmap = MediaStore.Images.Media.getBitmap(requireContext().contentResolver, uri)
                onPhotoPicked(bitmap)
            } catch (e: Exception) {
                // Ignoré : l'utilisateur peut réessayer
            }
        }
    }

    private fun onPhotoPicked(bitmap: Bitmap) {
        val product = productBeingPhotographed
        if (product != null) {
            repo.updateProductPhoto(product.id, bitmap)
            productBeingPhotographed = null
        } else {
            pendingPhoto = bitmap
            dialogPreviewSetter?.invoke(bitmap)
        }
    }

    private fun showPhotoSourceChooser(onExistingProduct: Product? = null) {
        productBeingPhotographed = onExistingProduct
        val options = arrayOf(getString(R.string.take_photo), getString(R.string.choose_gallery))
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.add_photo)
            .setItems(options) { _, which ->
                if (which == 0) takePicture.launch(null) else pickFromGallery.launch("image/*")
            }
            .show()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStockBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val shopCode = ShopPrefs.getShopCode(requireContext()) ?: return
        repo = FirebaseRepo(shopCode)

        adapter = ProductAdapter(
            items = emptyList(),
            onSell = { showSellDialog(it) },
            onDelete = { repo.deleteProduct(it.id) },
            onPhoto = { showPhotoSourceChooser(it) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.fabAdd.setOnClickListener { showAddDialog() }

        listener = repo.listenProducts { products ->
            adapter.updateData(products)
            binding.tvEmpty.visibility = if (products.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun showAddDialog() {
        pendingPhoto = null
        val dialogBinding = DialogAddProductBinding.inflate(layoutInflater)
        dialogPreviewSetter = { bmp ->
            Glide.with(dialogBinding.ivPhotoPreview).load(bmp).centerCrop().into(dialogBinding.ivPhotoPreview)
        }
        dialogBinding.btnAddPhoto.setOnClickListener { showPhotoSourceChooser(null) }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.add_product)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = dialogBinding.etName.text.toString().trim()
                val qty = dialogBinding.etQuantity.text.toString().toLongOrNull() ?: 0L
                val price = dialogBinding.etPrice.text.toString().toDoubleOrNull() ?: 0.0
                if (name.isNotEmpty()) {
                    repo.addProduct(name, qty, price, pendingPhoto) { msg ->
                        android.widget.Toast.makeText(requireContext(), msg, android.widget.Toast.LENGTH_LONG).show()
                    }
                }
                dialogPreviewSetter = null
            }
            .setNegativeButton(R.string.cancel) { _, _ -> dialogPreviewSetter = null }
            .show()
    }

    private fun showSellDialog(product: Product) {
        val dialogBinding = DialogSellBinding.inflate(layoutInflater)
        AlertDialog.Builder(requireContext())
            .setTitle("Vendre : ${product.name}")
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.sell) { _, _ ->
                val qtySold = dialogBinding.etQuantitySold.text.toString().toLongOrNull() ?: 0L
                val paymentMethod = when (dialogBinding.rgPayment.checkedRadioButtonId) {
                    dialogBinding.rbOrangeMoney.id -> "Orange Money"
                    dialogBinding.rbWave.id -> "Wave"
                    else -> "Espèces"
                }
                if (qtySold in 1..product.quantity) {
                    repo.recordSale(product, qtySold, paymentMethod) { msg ->
                        android.widget.Toast.makeText(requireContext(), msg, android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listener?.remove()
        _binding = null
    }
}
