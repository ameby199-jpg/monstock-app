package com.monstock.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.ImageView
import com.monstock.app.R
import java.io.ByteArrayOutputStream

/**
 * Gère le fond d'écran de chaque onglet (Accueil, Stock, Ventes, Ingrédients, Rapports).
 *
 * Par défaut, chaque écran affiche l'une des 3 photos fournies, en tournant d'une photo
 * à l'autre à chaque ouverture de l'écran (pour que les 3 soient utilisées au fil du temps).
 *
 * L'utilisateur peut aussi choisir sa propre photo pour un écran donné via le bouton 🖼 :
 * cette photo personnalisée est alors sauvegardée sur l'appareil (SharedPreferences, en
 * base64) et prend le pas sur la rotation par défaut pour cet écran, jusqu'à ce qu'il la
 * change ou la retire.
 */
object BackgroundPrefs {

    private const val PREFS_NAME = "monstock_backgrounds"

    /** Les 3 photos fournies, utilisées par défaut en rotation sur chaque écran. */
    private val defaultPhotos = listOf(
        R.drawable.bg_photo1,
        R.drawable.bg_photo2,
        R.drawable.bg_photo3
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Applique le fond d'écran adapté à [screenKey] sur [imageView] :
     * la photo personnalisée si l'utilisateur en a choisi une, sinon la prochaine
     * photo par défaut dans la rotation.
     */
    fun applyBackground(context: Context, screenKey: String, imageView: ImageView) {
        val custom = prefs(context).getString("custom_$screenKey", null)
        if (custom != null) {
            val bitmap = decodeBase64(custom)
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap)
                return
            }
        }
        val index = nextRotationIndex(context, screenKey)
        imageView.setImageResource(defaultPhotos[index])
    }

    private fun nextRotationIndex(context: Context, screenKey: String): Int {
        val p = prefs(context)
        val current = p.getInt("rotation_$screenKey", -1)
        val next = (current + 1) % defaultPhotos.size
        p.edit().putInt("rotation_$screenKey", next).apply()
        return next
    }

    /** Sauvegarde la photo choisie par l'utilisateur comme fond personnalisé pour cet écran. */
    fun saveCustomBackground(context: Context, screenKey: String, bitmap: Bitmap) {
        prefs(context).edit().putString("custom_$screenKey", compressToBase64(bitmap)).apply()
    }

    /** Retire le fond personnalisé pour revenir à la rotation par défaut sur cet écran. */
    fun clearCustomBackground(context: Context, screenKey: String) {
        prefs(context).edit().remove("custom_$screenKey").apply()
    }

    private fun compressToBase64(bitmap: Bitmap): String {
        val maxDim = 900
        val ratio = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height, 1f)
        val resized = if (ratio < 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
        } else bitmap
        val stream = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, 60, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.DEFAULT)
    }

    private fun decodeBase64(base64: String): Bitmap? = try {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) {
        null
    }
}
