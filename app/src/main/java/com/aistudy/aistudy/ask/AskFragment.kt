package com.aistudy.aistudy.ask

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.aistudy.aistudy.BuildConfig
import com.aistudy.aistudy.R
import com.aistudy.aistudy.answer.AnswerActivity
import com.aistudy.aistudy.auth.LoginActivity
import com.aistudy.aistudy.database.AppDatabase
import com.aistudy.aistudy.database.Profile
import com.aistudy.aistudy.database.QuestionAnswer
import com.google.android.material.button.MaterialButton
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AskFragment : Fragment() {

    companion object {
        private const val TAG = "AskFragment"
        private const val XP_PER_QUESTION = 50

        private fun maskKey(key: String): String {
            return if (key.length > 8) "${key.substring(0, 4)}...${key.substring(key.length - 4)}" else "***"
        }

        private fun truncate(text: String, max: Int = 100): String {
            return if (text.length > max) "${text.substring(0, max)}..." else text
        }

        private fun buildPrompt(question: String, level: String?, style: String?): String {
            val levelText = when (level) {
                "junior" -> "middle school student (Grades 6-9)"
                "uni" -> "university student"
                else -> "high school student (Grades 10-12)"
            }
            val styleText = when (style) {
                "short" -> "Give a concise, quick answer with fewer words."
                "detailed" -> "Give an in-depth answer with examples and thorough explanations."
                else -> "Give a step by step explanation, with clear steps and one main idea each."
            }
            return "You are AI Study Mentor, a study tutor in English for a $levelText.\n" +
                styleText + "\n" +
                "Always answer in English. Put math formulas in the \"expr\" field of the matching step.\n" +
                "Classify the subject correctly (math/science/code/history/language/other).\n" +
                "Also rate difficulty as easy, medium, or hard for this student.\n" +
                "If an image is attached, read the problem from the image carefully.\n\n" +
                "Return ONLY valid JSON (no markdown) with this structure:\n" +
                "{\n" +
                "  \"subject\": \"math|science|code|history|language|other\",\n" +
                "  \"difficulty\": \"easy|medium|hard\",\n" +
                "  \"steps\": [{\"title\": \"optional\", \"body\": \"required\", \"expr\": \"optional\"}],\n" +
                "  \"finalAnswer\": \"string\"\n" +
                "}\n\n" +
                "Question: " + question
        }

        private fun extractJson(raw: String): String {
            var text = raw.trim()
            if (text.startsWith("```json")) {
                text = text.removePrefix("```json").trim()
            } else if (text.startsWith("```")) {
                text = text.removePrefix("```").trim()
            }
            if (text.endsWith("```")) {
                text = text.removeSuffix("```").trim()
            }
            return text
        }

        private fun isValidSubject(subject: String): Boolean {
            return subject == "math" ||
                subject == "science" ||
                subject == "code" ||
                subject == "history" ||
                subject == "language" ||
                subject == "other"
        }

        private fun isValidDifficulty(difficulty: String): Boolean {
            return difficulty == "easy" ||
                difficulty == "medium" ||
                difficulty == "hard"
        }

        private fun encodeFileBase64(file: File): String {
            FileInputStream(file).use { input ->
                ByteArrayOutputStream().use { out ->
                    val buf = ByteArray(8192)
                    var n = input.read(buf)
                    while (n >= 0) {
                        out.write(buf, 0, n)
                        n = input.read(buf)
                    }
                    return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                }
            }
        }
    }

    private lateinit var editQuestion: EditText
    private lateinit var btnSend: MaterialButton
    private lateinit var btnCamera: MaterialButton
    private lateinit var btnGallery: MaterialButton
    private lateinit var progressLoading: ProgressBar
    private lateinit var layoutImagePreview: LinearLayout
    private lateinit var imgPreview: ImageView

    private var selectedImageFile: File? = null
    private var selectedMimeType: String? = null
    private var cameraUri: Uri? = null
    private var cameraFile: File? = null

    private lateinit var requestCameraPermission: ActivityResultLauncher<String>
    private lateinit var requestGalleryPermission: ActivityResultLauncher<String>
    private lateinit var takePicture: ActivityResultLauncher<Uri>
    private lateinit var pickImage: ActivityResultLauncher<String>

    private var executor: ExecutorService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestCameraPermission = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                launchCamera()
            } else {
                Toast.makeText(requireContext(), R.string.ask_permission_camera, Toast.LENGTH_SHORT).show()
            }
        }

        requestGalleryPermission = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                pickImage.launch("image/*")
            } else {
                Toast.makeText(requireContext(), R.string.ask_permission_gallery, Toast.LENGTH_SHORT).show()
            }
        }

        takePicture = registerForActivityResult(
            ActivityResultContracts.TakePicture()
        ) { success ->
            val file = cameraFile
            if (success && file != null && file.exists()) {
                setSelectedImage(file, "image/jpeg")
            } else {
                Toast.makeText(requireContext(), R.string.ask_image_error, Toast.LENGTH_SHORT).show()
            }
        }

        pickImage = registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri == null) return@registerForActivityResult
            try {
                val copied = copyUriToQaImages(uri)
                var mime = requireContext().contentResolver.getType(uri)
                if (mime == null || !mime.startsWith("image/")) {
                    mime = "image/jpeg"
                }
                setSelectedImage(copied, mime)
            } catch (e: Exception) {
                Log.e(TAG, "gallery copy failed", e)
                Toast.makeText(requireContext(), R.string.ask_image_error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_ask, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        editQuestion = view.findViewById(R.id.edit_question)
        btnSend = view.findViewById(R.id.btn_send)
        btnCamera = view.findViewById(R.id.btn_camera)
        btnGallery = view.findViewById(R.id.btn_gallery)
        progressLoading = view.findViewById(R.id.progress_loading)
        layoutImagePreview = view.findViewById(R.id.layout_image_preview)
        imgPreview = view.findViewById(R.id.img_preview)
        executor = Executors.newSingleThreadExecutor()

        btnSend.setOnClickListener { submitQuestion() }
        btnCamera.setOnClickListener { ensureCameraPermission() }
        btnGallery.setOnClickListener { ensureGalleryPermission() }
        view.findViewById<View>(R.id.btn_remove_image).setOnClickListener { clearSelectedImage() }

        view.findViewById<View>(R.id.btn_suggestion_1).setOnClickListener {
            editQuestion.setText(getString(R.string.ask_suggestion_1))
        }
        view.findViewById<View>(R.id.btn_suggestion_2).setOnClickListener {
            editQuestion.setText(getString(R.string.ask_suggestion_2))
        }
        view.findViewById<View>(R.id.btn_suggestion_3).setOnClickListener {
            editQuestion.setText(getString(R.string.ask_suggestion_3))
        }
        view.findViewById<View>(R.id.btn_suggestion_4).setOnClickListener {
            editQuestion.setText(getString(R.string.ask_suggestion_4))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        executor?.shutdown()
        executor = null
    }

    private fun ensureCameraPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            launchCamera()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun ensureGalleryPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_MEDIA_IMAGES)
            == PackageManager.PERMISSION_GRANTED
        ) {
            pickImage.launch("image/*")
        } else {
            requestGalleryPermission.launch(Manifest.permission.READ_MEDIA_IMAGES)
        }
    }

    private fun launchCamera() {
        try {
            val dir = File(requireContext().cacheDir, "camera")
            if (!dir.exists() && !dir.mkdirs()) {
                throw Exception("cannot create camera cache")
            }
            val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
            cameraFile = file
            val uri = FileProvider.getUriForFile(
                requireContext(),
                requireContext().packageName + ".fileprovider",
                file
            )
            cameraUri = uri
            takePicture.launch(uri)
        } catch (e: Exception) {
            Log.e(TAG, "launch camera failed", e)
            Toast.makeText(requireContext(), R.string.ask_image_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyUriToQaImages(uri: Uri): File {
        val dir = File(requireContext().filesDir, "qa_images")
        if (!dir.exists() && !dir.mkdirs()) {
            throw Exception("cannot create qa_images")
        }
        val out = File(dir, "qa_${System.currentTimeMillis()}.jpg")
        val input = requireContext().contentResolver.openInputStream(uri)
        input.use { ins ->
            FileOutputStream(out).use { fos ->
                if (ins == null) throw Exception("null input stream")
                val buf = ByteArray(8192)
                var n = ins.read(buf)
                while (n >= 0) {
                    fos.write(buf, 0, n)
                    n = ins.read(buf)
                }
            }
        }
        return out
    }

    private fun persistCameraToQaImages(source: File): File {
        val dir = File(requireContext().filesDir, "qa_images")
        if (!dir.exists() && !dir.mkdirs()) {
            throw Exception("cannot create qa_images")
        }
        val out = File(dir, "qa_${System.currentTimeMillis()}.jpg")
        FileInputStream(source).use { input ->
            FileOutputStream(out).use { fos ->
                val buf = ByteArray(8192)
                var n = input.read(buf)
                while (n >= 0) {
                    fos.write(buf, 0, n)
                    n = input.read(buf)
                }
            }
        }
        return out
    }

    private fun setSelectedImage(file: File, mimeType: String) {
        selectedImageFile = file
        selectedMimeType = mimeType
        imgPreview.setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
        layoutImagePreview.visibility = View.VISIBLE
    }

    private fun clearSelectedImage() {
        selectedImageFile = null
        selectedMimeType = null
        imgPreview.setImageDrawable(null)
        layoutImagePreview.visibility = View.GONE
    }

    private fun setLoading(loading: Boolean) {
        btnSend.isEnabled = !loading
        btnCamera.isEnabled = !loading
        btnGallery.isEnabled = !loading
        btnSend.setText(if (loading) R.string.ask_sending else R.string.ask_send)
        progressLoading.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun submitQuestion() {
        var question = editQuestion.text.toString().trim()
        val hasImage = selectedImageFile?.exists() == true
        if (TextUtils.isEmpty(question) && !hasImage) {
            Toast.makeText(requireContext(), R.string.ask_empty_both, Toast.LENGTH_SHORT).show()
            return
        }
        if (TextUtils.isEmpty(question)) {
            question = getString(R.string.ask_image_default_question)
        }

        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey.startsWith("YOUR_")) {
            Log.w(TAG, "submit blocked: api key missing or placeholder")
            Toast.makeText(requireContext(), R.string.ask_error_key, Toast.LENGTH_LONG).show()
            return
        }

        val activity = requireActivity()
        val userId = activity.getSharedPreferences(LoginActivity.PREFS_AUTH, android.content.Context.MODE_PRIVATE)
            .getLong(LoginActivity.KEY_USER_ID, -1)
        if (userId == -1L) return

        val db = AppDatabase.getInstance(activity)
        val profile = db.profileDao().getByUserId(userId) ?: Profile().apply { this.userId = userId }
        val prompt = buildPrompt(question, profile.level, profile.style)

        setLoading(true)
        Log.d(
            TAG, "submit start questionLength=${question.length}" +
                " hasImage=$hasImage" +
                " apiKey=${maskKey(BuildConfig.GEMINI_API_KEY)}"
        )

        val questionFinal = question
        val imageFile = selectedImageFile
        val mimeType = selectedMimeType
        val useImage = hasImage

        executor?.execute {
            try {
                var imageBase64: String? = null
                var imagePath: String? = null
                var mime = mimeType

                if (useImage && imageFile != null) {
                    val parent = imageFile.parentFile
                    val persisted = if (parent != null && "qa_images" == parent.name) {
                        imageFile
                    } else {
                        persistCameraToQaImages(imageFile)
                    }
                    imagePath = persisted.absolutePath
                    val encoded = encodeFileBase64(persisted)
                    imageBase64 = encoded
                    if (mime.isNullOrEmpty()) {
                        mime = "image/jpeg"
                    }
                    Log.d(TAG, "image ready path=$imagePath base64Length=${encoded.length}")
                }

                Log.d(TAG, "calling Gemini...")
                val rawResponse = GeminiClient.ask(apiKey, prompt, imageBase64, mime)
                Log.d(TAG, "raw response preview: ${truncate(rawResponse, 200)}")

                val jsonText = extractJson(rawResponse)
                val jsonObj = JSONObject(jsonText)

                var subject = jsonObj.optString("subject", "other")
                if (!isValidSubject(subject)) {
                    Log.w(TAG, "invalid subject from AI: $subject, fallback to other")
                    subject = "other"
                }

                var difficulty = jsonObj.optString("difficulty", "medium")
                if (!isValidDifficulty(difficulty)) {
                    difficulty = "medium"
                }

                val stepsArray = jsonObj.optJSONArray("steps") ?: JSONArray()
                val finalAnswer = jsonObj.optString("finalAnswer", "")

                Log.d(
                    TAG, "parsed subject=$subject difficulty=$difficulty" +
                        " steps=${stepsArray.length()}" +
                        " finalAnswerLength=${finalAnswer.length}"
                )

                val qa = QuestionAnswer().apply {
                    this.userId = userId
                    this.subject = subject
                    this.question = questionFinal
                    this.steps = stepsArray.toString()
                    this.finalAnswer = finalAnswer
                    this.createdAt = System.currentTimeMillis()
                    this.difficulty = difficulty
                    this.extrasJson = null
                    this.reviewedAt = 0
                    this.imagePath = imagePath
                }

                val qaId = db.questionAnswerDao().insert(qa)
                profile.xp += XP_PER_QUESTION
                db.profileDao().update(profile)

                Log.d(TAG, "saved qaId=$qaId imagePath=$imagePath")

                activity.runOnUiThread {
                    setLoading(false)
                    clearSelectedImage()
                    editQuestion.setText("")
                    val intent = Intent(activity, AnswerActivity::class.java).apply {
                        putExtra(AnswerActivity.EXTRA_QA_ID, qaId)
                    }
                    startActivity(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "submit failed", e)
                activity.runOnUiThread {
                    setLoading(false)
                    Toast.makeText(activity, R.string.ask_error_network, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
