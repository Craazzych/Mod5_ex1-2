package com.example.module5tasks12

import android.Manifest
import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF3D5AFE),
                    secondary = Color(0xFF00A896),
                    background = Color(0xFFF6F7FB),
                    surface = Color.White,
                    surfaceVariant = Color(0xFFE8ECF8)
                )
            ) {
                Module5TasksApp()
            }
        }
    }
}

data class DiaryEntry(
    val fileName: String,
    val title: String,
    val text: String,
    val timestamp: Long
)

class DiaryRepository(private val context: Context) {
    fun loadEntries(): List<DiaryEntry> {
        return context.filesDir
            .listFiles { file -> file.extension == "txt" && file.name.startsWith("diary_") }
            ?.mapNotNull { file ->
                val lines = file.readLines()
                val title = lines.firstOrNull().orEmpty().removePrefix("title=")
                val text = lines.drop(1).joinToString("\n")
                val timestamp = file.name.removePrefix("diary_").substringBefore("_").toLongOrNull()
                    ?: file.lastModified()
                DiaryEntry(file.name, title, text, timestamp)
            }
            ?.sortedByDescending { it.timestamp }
            .orEmpty()
    }

    fun saveEntry(oldFileName: String?, title: String, text: String): DiaryEntry {
        val timestamp = oldFileName
            ?.removePrefix("diary_")
            ?.substringBefore("_")
            ?.toLongOrNull()
            ?: System.currentTimeMillis()
        val safeTitle = title.ifBlank { "entry" }
            .replace(Regex("[^A-Za-zА-Яа-я0-9_-]"), "_")
            .take(24)
        val fileName = oldFileName ?: "diary_${timestamp}_$safeTitle.txt"
        File(context.filesDir, fileName).writeText("title=$title\n$text")
        return DiaryEntry(fileName, title, text, timestamp)
    }

    fun deleteEntry(fileName: String) {
        File(context.filesDir, fileName).delete()
    }
}

class DiaryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = DiaryRepository(application)
    private val _entries = MutableStateFlow(repository.loadEntries())
    val entries: StateFlow<List<DiaryEntry>> = _entries.asStateFlow()

    fun saveEntry(oldFileName: String?, title: String, text: String) {
        val saved = repository.saveEntry(oldFileName, title, text)
        _entries.update { current ->
            listOf(saved) + current.filterNot { it.fileName == saved.fileName }
        }
    }

    fun deleteEntry(fileName: String) {
        repository.deleteEntry(fileName)
        _entries.update { current -> current.filterNot { it.fileName == fileName } }
    }
}

data class PhotoItem(val file: File, val timestamp: Long = file.lastModified())

class PhotoRepository(private val context: Context) {
    private val picturesDir: File
        get() = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)!!.also { it.mkdirs() }

    fun loadPhotos(): List<PhotoItem> {
        return picturesDir
            .listFiles { file -> file.extension.lowercase() == "jpg" }
            ?.map { PhotoItem(it) }
            ?.sortedByDescending { it.timestamp }
            .orEmpty()
    }

    fun createPhotoFile(): File {
        val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return File(picturesDir, "IMG_$name.jpg")
    }

    fun uriFor(file: File): Uri {
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun exportToGallery(photo: PhotoItem): Boolean {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, photo.file.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Module5Tasks")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        resolver.openOutputStream(uri)?.use { output ->
            photo.file.inputStream().use { input -> input.copyTo(output) }
        } ?: return false
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return true
    }
}

class PhotoViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PhotoRepository(application)
    private var pendingPhoto: File? = null
    private val _photos = MutableStateFlow(repository.loadPhotos())
    val photos: StateFlow<List<PhotoItem>> = _photos.asStateFlow()

    fun createPhotoUri(): Uri {
        val file = repository.createPhotoFile()
        pendingPhoto = file
        return repository.uriFor(file)
    }

    fun onPhotoTaken(success: Boolean) {
        val file = pendingPhoto ?: return
        if (success && file.exists()) {
            _photos.update { current -> listOf(PhotoItem(file)) + current }
        } else {
            file.delete()
        }
        pendingPhoto = null
    }

    fun export(photo: PhotoItem): Boolean = repository.exportToGallery(photo)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Module5TasksApp(
    diaryViewModel: DiaryViewModel = viewModel(),
    photoViewModel: PhotoViewModel = viewModel()
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val entries by diaryViewModel.entries.collectAsState()
    val photos by photoViewModel.photos.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("Модуль 5", fontWeight = FontWeight.Bold)
                            Text("Дневник и фотогалерея", style = MaterialTheme.typography.bodyMedium)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = Color.White
                    )
                )
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Дневник") })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Фото") })
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            if (selectedTab == 0) {
                DiaryRoot(entries, diaryViewModel::saveEntry, diaryViewModel::deleteEntry)
            } else {
                PhotoGalleryRoot(
                    photos = photos,
                    createPhotoUri = photoViewModel::createPhotoUri,
                    onPhotoTaken = photoViewModel::onPhotoTaken,
                    onExport = photoViewModel::export
                )
            }
        }
    }
}

@Composable
fun DiaryRoot(
    entries: List<DiaryEntry>,
    onSave: (String?, String, String) -> Unit,
    onDelete: (String) -> Unit
) {
    var editedEntry by remember { mutableStateOf<DiaryEntry?>(null) }
    var editMode by remember { mutableStateOf(false) }

    if (editMode) {
        DiaryEditScreen(
            entry = editedEntry,
            onSave = onSave,
            onBack = {
                editMode = false
                editedEntry = null
            }
        )
    } else {
        DiaryListScreen(
            entries = entries,
            onCreate = {
                editedEntry = null
                editMode = true
            },
            onOpen = {
                editedEntry = it
                editMode = true
            },
            onDelete = { onDelete(it.fileName) }
        )
    }
}

@Composable
fun DiaryListScreen(
    entries: List<DiaryEntry>,
    onCreate: () -> Unit,
    onOpen: (DiaryEntry) -> Unit,
    onDelete: (DiaryEntry) -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(onClick = onCreate, shape = CircleShape) {
                Icon(Icons.Default.Add, contentDescription = "Новая запись")
            }
        }
    ) { padding ->
        if (entries.isEmpty()) {
            EmptyState(
                title = "У вас пока нет записей",
                subtitle = "Нажмите +, чтобы создать первую",
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(entries, key = { it.fileName }) { entry ->
                    DiaryEntryCard(entry, onOpen, onDelete)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DiaryEntryCard(
    entry: DiaryEntry,
    onOpen: (DiaryEntry) -> Unit,
    onDelete: (DiaryEntry) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val date = remember(entry.timestamp) {
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(entry.timestamp))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onOpen(entry) },
                onLongClick = { menuExpanded = true }
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.title.ifBlank { "Без заголовка" },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    date,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Text(entry.text.take(70), style = MaterialTheme.typography.bodyMedium)
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("Удалить") },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onDelete(entry)
                    }
                )
            }
        }
    }
}

@Composable
fun DiaryEditScreen(
    entry: DiaryEntry?,
    onSave: (String?, String, String) -> Unit,
    onBack: () -> Unit
) {
    var title by remember(entry?.fileName) { mutableStateOf(entry?.title.orEmpty()) }
    var text by remember(entry?.fileName) { mutableStateOf(entry?.text.orEmpty()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Заголовок") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Текст заметки") },
            minLines = 10,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = text.isNotBlank(),
                onClick = {
                    onSave(entry?.fileName, title, text)
                    onBack()
                }
            ) {
                Text("Сохранить")
            }
            OutlinedButton(onClick = onBack) {
                Text("Назад")
            }
        }
    }
}

@Composable
fun PhotoGalleryRoot(
    photos: List<PhotoItem>,
    createPhotoUri: () -> Uri,
    onPhotoTaken: (Boolean) -> Unit,
    onExport: (PhotoItem) -> Boolean
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        onPhotoTaken(success)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pendingAction?.invoke()
        pendingAction = null
    }

    fun takePhoto() {
        val action = {
            cameraLauncher.launch(createPhotoUri())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingAction = action
            permissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            action()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { takePhoto() }, shape = CircleShape) {
                Icon(Icons.Default.CameraAlt, contentDescription = "Сделать фото")
            }
        }
    ) { padding ->
        if (photos.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("У вас пока нет фото")
                Spacer(Modifier.height(12.dp))
                Button(onClick = { takePhoto() }) {
                    Text("Сделать первое фото")
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(photos, key = { it.file.absolutePath }) { photo ->
                    PhotoCell(
                        photo = photo,
                        onExport = {
                            if (onExport(photo)) {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Фото добавлено в галерею")
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PhotoCell(photo: PhotoItem, onExport: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val bitmap = remember(photo.file.absolutePath) {
        BitmapFactory.decodeFile(photo.file.absolutePath)?.asImageBitmap()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = photo.file.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Card(Modifier.fillMaxSize()) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text("Фото")
                }
            }
        }
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.88f))
        ) {
            Icon(Icons.Default.MoreVert, contentDescription = "Меню")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Экспорт в галерею") },
                onClick = {
                    expanded = false
                    onExport()
                }
            )
        }
    }
}

@Composable
fun EmptyState(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
