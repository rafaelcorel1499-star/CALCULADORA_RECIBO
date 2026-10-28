package com.calculofactura.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.min

class MainActivity : AppCompatActivity() {

    // Porcentajes: Linda 30 %, Rafael 30 %, Ricardo paga el valor restante.
    private val lindaPct = 30L
    private val rafaelPct = 30L

    private class Person(
        val key: String,
        val homeAvatar: ImageView,
        val settingsAvatar: ImageView
    )

    private lateinit var persons: List<Person>
    private var pendingKey: String? = null

    private val money = DecimalFormat(
        "#,##0",
        DecimalFormatSymbols(Locale("es", "CO")).apply {
            groupingSeparator = '.'
            decimalSeparator = ','
        }
    )

    private val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val key = pendingKey
        if (uri != null && key != null) {
            saveAvatar(key, uri)
            refreshAvatars()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        persons = listOf(
            Person("linda", findViewById(R.id.avHomeLinda), findViewById(R.id.avSetLinda)),
            Person("rafael", findViewById(R.id.avHomeRafael), findViewById(R.id.avSetRafael)),
            Person("ricardo", findViewById(R.id.avHomeRicardo), findViewById(R.id.avSetRicardo))
        )

        val etLuz = findViewById<EditText>(R.id.etLuz)
        val etAgua = findViewById<EditText>(R.id.etAgua)
        attachMoneyFormatter(etLuz)
        attachMoneyFormatter(etAgua)

        findViewById<View>(R.id.btnCalcular).setOnClickListener {
            hideKeyboard()
            calculate(parseMoney(etLuz), parseMoney(etAgua))
        }

        findViewById<View>(R.id.tabHome).setOnClickListener { showTab(true) }
        findViewById<View>(R.id.tabSettings).setOnClickListener { showTab(false) }

        findViewById<View>(R.id.btnPickLinda).setOnClickListener { pick("linda") }
        findViewById<View>(R.id.btnPickRafael).setOnClickListener { pick("rafael") }
        findViewById<View>(R.id.btnPickRicardo).setOnClickListener { pick("ricardo") }
        findViewById<View>(R.id.btnRemoveLinda).setOnClickListener { removeAvatar("linda") }
        findViewById<View>(R.id.btnRemoveRafael).setOnClickListener { removeAvatar("rafael") }
        findViewById<View>(R.id.btnRemoveRicardo).setOnClickListener { removeAvatar("ricardo") }

        showTab(true)
        refreshAvatars()
    }

    // ------------------------------------------------------------------ Cálculo

    private fun calculate(luz: Long, agua: Long) {
        val total = luz + agua
        if (total <= 0L) {
            Toast.makeText(this, "Ingresa el valor de las facturas", Toast.LENGTH_SHORT).show()
            return
        }
        val linda = (total * lindaPct + 50) / 100
        val rafael = (total * rafaelPct + 50) / 100
        val ricardo = total - linda - rafael
        val ricardoPct = 100 - lindaPct - rafaelPct

        findViewById<TextView>(R.id.tvTotal).text = fmt(total)
        findViewById<TextView>(R.id.tvAmountLinda).text = fmt(linda)
        findViewById<TextView>(R.id.tvAmountRafael).text = fmt(rafael)
        findViewById<TextView>(R.id.tvAmountRicardo).text = fmt(ricardo)
        findViewById<TextView>(R.id.tvPctLinda).text = "$lindaPct%"
        findViewById<TextView>(R.id.tvPctRafael).text = "$rafaelPct%"
        findViewById<TextView>(R.id.tvPctRicardo).text = "Valor restante ($ricardoPct%)"

        val results = findViewById<View>(R.id.resultsContainer)
        results.visibility = View.VISIBLE
        findViewById<View>(R.id.screenHome).post {
            (findViewById<View>(R.id.screenHome) as android.widget.ScrollView)
                .smoothScrollTo(0, results.top - 16)
        }
    }

    private fun fmt(v: Long) = "$ " + money.format(v)

    private fun parseMoney(et: EditText): Long =
        et.text.toString().filter { it.isDigit() }.toLongOrNull() ?: 0L

    private fun attachMoneyFormatter(et: EditText) {
        et.addTextChangedListener(object : TextWatcher {
            private var busy = false
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (busy) return
                busy = true
                val digits = s.toString().filter { it.isDigit() }.take(12)
                val formatted = if (digits.isEmpty()) "" else money.format(digits.toLong())
                if (formatted != s.toString()) {
                    et.setText(formatted)
                    et.setSelection(formatted.length)
                }
                busy = false
            }
        })
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
    }

    // ------------------------------------------------------------------ Pestañas

    private fun showTab(home: Boolean) {
        findViewById<View>(R.id.screenHome).visibility = if (home) View.VISIBLE else View.GONE
        findViewById<View>(R.id.screenSettings).visibility = if (home) View.GONE else View.VISIBLE
        val active = 0xFF0D5BC2.toInt()
        val inactive = 0xFFA0AEC0.toInt()
        findViewById<ImageView>(R.id.tabHomeIcon).setColorFilter(if (home) active else inactive)
        findViewById<TextView>(R.id.tabHomeText).setTextColor(if (home) active else inactive)
        findViewById<ImageView>(R.id.tabSettingsIcon).setColorFilter(if (home) inactive else active)
        findViewById<TextView>(R.id.tabSettingsText).setTextColor(if (home) inactive else active)
        hideKeyboard()
    }

    // ------------------------------------------------------------------ Fotos de perfil

    private fun avatarFile(key: String) = File(filesDir, "avatar_$key.jpg")

    private fun pick(key: String) {
        pendingKey = key
        picker.launch("image/*")
    }

    private fun removeAvatar(key: String) {
        avatarFile(key).delete()
        refreshAvatars()
    }

    private fun refreshAvatars() {
        for (p in persons) {
            val f = avatarFile(p.key)
            val bmp = if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
            for (iv in listOf(p.homeAvatar, p.settingsAvatar)) {
                if (bmp != null) iv.setImageBitmap(bmp) else iv.setImageResource(R.drawable.ic_avatar)
            }
        }
    }

    private fun saveAvatar(key: String, uri: Uri) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / sample > 1200 || bounds.outHeight / sample > 1200) sample *= 2

            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: throw IllegalStateException("No se pudo leer la imagen")

            val orientation = contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            }

            val size = min(bmp.width, bmp.height)
            val x = (bmp.width - size) / 2
            val y = (bmp.height - size) / 2
            val square = Bitmap.createBitmap(bmp, x, y, size, size, matrix, true)
            val finalBmp = Bitmap.createScaledBitmap(square, 400, 400, true)
            FileOutputStream(avatarFile(key)).use { finalBmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo cargar la foto", Toast.LENGTH_SHORT).show()
        }
    }
}
