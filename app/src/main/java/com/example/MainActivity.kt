package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.example.ui.theme.MyApplicationTheme
import com.example.data.RecentFile
import com.example.viewmodel.AiStatus
import com.example.viewmodel.CleanAppViewModel
import com.example.viewmodel.Screen
import com.example.viewmodel.StrokePath
import androidx.compose.animation.core.*
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                CleanAiApp()
            }
        }
    }
}

@Composable
fun CleanAiApp() {
    val viewModel: CleanAppViewModel = viewModel()
    val context = LocalContext.current
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessingMedia.collectAsStateWithLifecycle()
    val progressPercent by viewModel.mediaProcessingPercentage.collectAsStateWithLifecycle()

    // Activity Result Launchers
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.loadImageFromUri(context, uri)
            viewModel.navigateTo(Screen.EditImage)
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.loadVideoFromUri(uri)
            viewModel.navigateTo(Screen.EditVideo)
        }
    }

    // Camera Launchers with temporary Uri
    var tempCameraImageFile by remember { mutableStateOf<File?>(null) }
    val cameraImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            tempCameraImageFile?.let { file ->
                val uri = Uri.fromFile(file)
                viewModel.loadImageFromUri(context, uri)
                viewModel.navigateTo(Screen.EditImage)
            }
        }
    }

    // Permission Request Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.filterKeys {
            it == Manifest.permission.CAMERA
        }.values.firstOrNull() ?: false

        if (granted) {
            val file = File(context.cacheDir, "camera_capture_${System.currentTimeMillis()}.jpg")
            tempCameraImageFile = file
            val authority = "${context.packageName}.fileprovider"
            try {
                val uri = FileProvider.getUriForFile(context, authority, file)
                cameraImageLauncher.launch(uri)
            } catch (e: Exception) {
                // If FileProvider isn't declared or fails, fall back to picker
                Toast.makeText(context, "Camera permission granted. Accessing media picker.", Toast.LENGTH_LONG).show()
                imagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        } else {
            Toast.makeText(context, "Camera permission denied. Let's select from gallery instead.", Toast.LENGTH_SHORT).show()
            imagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                fadeIn(animationSpec = spring()) togetherWith fadeOut(animationSpec = spring())
            },
            label = "screen_navigation"
        ) { screen ->
            when (screen) {
                is Screen.Splash -> SplashScreen()
                is Screen.Home -> HomeScreen(
                    onImagePicked = { imagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onVideoPicked = { videoPickerLauncher.launch("video/*") },
                    onImageCaptured = {
                        val checkCamera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                        if (checkCamera == PackageManager.PERMISSION_GRANTED) {
                            val file = File(context.cacheDir, "camera_capture_${System.currentTimeMillis()}.jpg")
                            tempCameraImageFile = file
                            val authority = "${context.packageName}.fileprovider"
                            try {
                                val uri = FileProvider.getUriForFile(context, authority, file)
                                cameraImageLauncher.launch(uri)
                            } catch (e: Exception) {
                                imagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                        } else {
                            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                        }
                    }
                )
                is Screen.EditImage -> EditImageScreen(
                    onBack = { viewModel.navigateTo(Screen.Home) }
                )
                is Screen.EditVideo -> EditVideoScreen(
                    onBack = { viewModel.navigateTo(Screen.Home) }
                )
                is Screen.PreviewExport -> PreviewExportScreen(
                    onBack = { viewModel.navigateTo(Screen.Home) }
                )
            }
        }

        // Global Processing HUD Overlay
        if (isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .clickable(enabled = false) {}, // Absorbs taps
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "CLEANING MEDIA",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981) // Emerald accent
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Removing logo & reconstructing background layers in real-time...",
                        textAlign = TextAlign.Center,
                        color = Color.LightGray,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
                        CircularProgressIndicator(
                            progress = { progressPercent / 100f },
                            color = Color(0xFF10B981),
                            trackColor = Color(0xFF1F2937),
                            strokeWidth = 6.dp,
                            modifier = Modifier.fillMaxSize()
                        )
                        Text(
                            text = "$progressPercent%",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        )
                    }
                }
            }
        }
    }
}

// --- SCREEN IMPLEMENTATIONS ---

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F17)), // Deep Premium Cosmic Dark Slate
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Modern Tech Brutalist Logo Display Icon
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF10B981), Color(0xFF059669))
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "CleanAI Sparkle",
                    tint = Color.White,
                    modifier = Modifier.size(45.dp)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "CleanAI",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    fontFamily = FontFamily.SansSerif
                )
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "LOGO & WATERMARK REMOVER",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF10B981), // Luminous Emerald accent
                    letterSpacing = 2.sp
                )
            )
        }

        Text(
            text = "AI-Powered Reconstruction Model",
            style = MaterialTheme.typography.labelSmall.copy(
                color = Color.Gray,
                letterSpacing = 1.sp
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp)
        )
    }
}

@Composable
fun HomeScreen(
    onImagePicked: () -> Unit,
    onVideoPicked: () -> Unit,
    onImageCaptured: () -> Unit
) {
    val viewModel: CleanAppViewModel = viewModel()
    val recentFiles by viewModel.recentFiles.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A))
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFF10B981), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "App logo",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "CleanAI Studio",
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Black
                        )
                    )
                }
            }
        },
        containerColor = Color(0xFF0B0F17)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // Main Actions Banner
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Select what you want to clean",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Clean Image Block
                    HomeActionCard(
                        title = "Clean Image",
                        subtitle = "Remove logos, text, and labels",
                        icon = Icons.Default.Edit,
                        tintColor = Color(0xFF10B981),
                        onClick = onImagePicked,
                        onCameraClick = onImageCaptured,
                        modifier = Modifier.weight(1f)
                    )

                    // Clean Video Block
                    HomeActionCard(
                        title = "Clean Video",
                        subtitle = "Erase watermarks and timestamps",
                        icon = Icons.Default.PlayArrow,
                        tintColor = Color(0xFF10B981),
                        onClick = onVideoPicked,
                        onCameraClick = null, // Video from gallery preferred
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Recent Files Header & Toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Files",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )

                if (recentFiles.isNotEmpty()) {
                    TextButton(
                        onClick = { viewModel.clearRecentGallery() },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.Gray)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear recent history",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear All", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Gallery body
            if (recentFiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 20.dp, vertical = 24.dp)
                        .background(Color(0xFF141B2D), RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "No files yet",
                            tint = Color.DarkGray,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Your Cleaned Files Gallery is Empty",
                            style = MaterialTheme.typography.titleSmall.copy(
                                color = Color.LightGray,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "To test, pick a picture or snapshot containing watermarks, then apply the magical AI cleaner!",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(recentFiles) { item ->
                        RecentFileGridItem(
                            item = item,
                            onShare = {
                                try {
                                    val f = File(item.filePath)
                                    if (f.exists()) {
                                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = if (item.mediaType == "IMAGE") "image/jpeg" else "video/mp4"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Share Cleaned Media"))
                                    } else {
                                        Toast.makeText(context, "File was removed from storage.", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error sharing: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onDelete = {
                                viewModel.deleteRecentFile(item.id, item.filePath)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HomeActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tintColor: Color,
    onClick: () -> Unit,
    onCameraClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .testTag(title.lowercase().replace(" ", "_") + "_card")
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF141B2D) // Dark modern charcoal surface
        ),
        border = BorderStroke(1.dp, Color(0xFF1F2937)) // subtle slate line
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .background(tintColor.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = tintColor,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = subtitle,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Color.Gray,
                    lineHeight = 12.sp
                )
            )

            if (onCameraClick != null) {
                Spacer(modifier = Modifier.height(8.dp))
                IconButton(
                    onClick = onCameraClick,
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFF242F41), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Capture picture using camera",
                        tint = Color.LightGray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(36.dp)) // Equalizer space for height alignment
            }
        }
    }
}

@Composable
fun RecentFileGridItem(
    item: RecentFile,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val fileState = remember(item.filePath) { File(item.filePath) }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141B2D)),
        border = BorderStroke(1.dp, Color(0xFF1F2937)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(120.dp)) {
            if (item.mediaType == "IMAGE" && fileState.exists()) {
                Image(
                    painter = rememberAsyncImagePainter(fileState),
                    contentDescription = "Cleaned media thumbs",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Video visual placeholder representation
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0F172A)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Video playback indication",
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(42.dp)
                    )
                }
            }

            // Top Type Label Indicator badge
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .align(Alignment.TopStart)
            ) {
                Text(
                    text = item.mediaType,
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileState.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.LightGray,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }

            Row {
                IconButton(onClick = onShare, modifier = Modifier.size(26.dp)) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share item",
                        tint = Color.LightGray,
                        modifier = Modifier.size(14.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(26.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove item",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

// --- IMAGE EDITING WORKSPACE SCREEN ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditImageScreen(onBack: () -> Unit) {
    val viewModel: CleanAppViewModel = viewModel()
    val previewBmp by viewModel.previewBitmap.collectAsStateWithLifecycle()
    val activeTool by viewModel.activeTool.collectAsStateWithLifecycle()
    val brushSize by viewModel.brushSize.collectAsStateWithLifecycle()
    val strokesList by viewModel.strokes.collectAsStateWithLifecycle()
    val aiStatus by viewModel.aiStatus.collectAsStateWithLifecycle()
    val detectedBoxes by viewModel.detectedBoxes.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Clean Image Workspace",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Undo, Redo, and Clear action tools
                    TextButton(onClick = { viewModel.undo() }) {
                        Text("Undo", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(onClick = { viewModel.redo() }) {
                        Text("Redo", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                    IconButton(onClick = { viewModel.clearDrawings() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Clear masks")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A))
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                // Brush Thickness slider if manual brush tool is selected
                if (activeTool == "MANUAL") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Brush scale",
                            tint = Color.LightGray,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Size: ${brushSize.toInt()}",
                            color = Color.LightGray,
                            style = MaterialTheme.typography.labelSmall
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Slider(
                            value = brushSize,
                            onValueChange = { viewModel.setBrushSize(it) },
                            valueRange = 10f..120f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF10B981),
                                activeTrackColor = Color(0xFF10B981)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = { viewModel.applyImageClean() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("apply_clean_image_button")
                ) {
                    Icon(Icons.Default.Star, contentDescription = "Clean")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Apply AI Clean", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        },
        containerColor = Color(0xFF0B0F17)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // Mode Select Toggle Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .background(Color(0xFF141B2D), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.setActiveTool("MANUAL") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (activeTool == "MANUAL") Color(0xFF1E293B) else Color.Transparent,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Brush Mode", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Manual Mask", style = MaterialTheme.typography.labelMedium)
                }

                Button(
                    onClick = {
                        viewModel.setActiveTool("AUTO")
                        viewModel.runAiWatermarkDetection()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (activeTool == "AUTO") Color(0xFF1E293B) else Color.Transparent,
                        contentColor = if (activeTool == "AUTO") Color.White else Color.Gray
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "AI Mode",
                        tint = if (activeTool == "AUTO") Color(0xFF10B981) else Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Auto AI Scan", style = MaterialTheme.typography.labelMedium)
                }
            }

            // AI SCAN STATE STATUS
            if (activeTool == "AUTO") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .background(Color(0xFF10B981).copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFF10B981).copy(alpha = 0.28f), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        when (aiStatus) {
                            is AiStatus.Idle -> {
                                Text("Ready to analyze", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                            }
                            is AiStatus.Processing -> {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF10B981), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Gemini analyzing visual layers...", color = Color.White, style = MaterialTheme.typography.bodySmall)
                            }
                            is AiStatus.Success -> {
                                val count = (aiStatus as AiStatus.Success).count
                                Icon(Icons.Default.Check, "success", tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (count > 0) "Gemini spotted $count target watermarks/logos" else "No clear logos found. Tracing manually is advised!",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            is AiStatus.Error -> {
                                Icon(Icons.Default.Warning, "error", tint = Color.Red, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Detection service busy, showing simulation.", color = Color.White, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Canvas Workspace
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(Color.Black, RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFF1F2937), RoundedCornerShape(16.dp))
                    .clipToBounds()
            ) {
                previewBmp?.let { bmp ->
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val viewWidth = constraints.maxWidth.toFloat()
                        val viewHeight = constraints.maxHeight.toFloat()

                        val imgW = bmp.width.toFloat()
                        val imgH = bmp.height.toFloat()

                        // Calculate scale to fit inside parent Box
                        val scale = (viewWidth / imgW).coerceAtMost(viewHeight / imgH)
                        val scaledWidth = imgW * scale
                        val scaledHeight = imgH * scale
                        val offsetX = (viewWidth - scaledWidth) / 2f
                        val offsetY = (viewHeight - scaledHeight) / 2f

                        val density = LocalDensity.current

                        // Draw Image aligned
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Work-file resource",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .align(Alignment.Center)
                        )

                        // Input Drawing Layer Canvas
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(activeTool) {
                                    if (activeTool == "MANUAL") {
                                        detectDragGestures(
                                            onDragStart = { pointer ->
                                                // Calculate pointer offset relative to scaled image coordinates
                                                val relativeOffset = Offset(
                                                    x = (pointer.x - offsetX) / scale,
                                                    y = (pointer.y - offsetY) / scale
                                                )
                                                viewModel.startNewStroke(relativeOffset)
                                            },
                                            onDrag = { change, _ ->
                                                change.consume()
                                                val pos = change.position
                                                val relativeOffset = Offset(
                                                    x = (pos.x - offsetX) / scale,
                                                    y = (pos.y - offsetY) / scale
                                                )
                                                viewModel.appendPointToActiveStroke(relativeOffset)
                                            }
                                        )
                                    }
                                }
                        ) {
                            // Translate to coordinate system representing raw bitmap pixels
                            drawContext.canvas.save()
                            drawContext.canvas.translate(offsetX, offsetY)
                            drawContext.canvas.scale(scale, scale)

                            // Render Manual Mask Brush Strokes
                            for (stroke in strokesList) {
                                val touchPath = androidx.compose.ui.graphics.Path()
                                if (stroke.points.isNotEmpty()) {
                                    touchPath.moveTo(stroke.points[0].x, stroke.points[0].y)
                                    for (i in 1 until stroke.points.size) {
                                        touchPath.lineTo(stroke.points[i].x, stroke.points[i].y)
                                    }
                                    drawPath(
                                        path = touchPath,
                                        color = Color(0x99EF4444), // Translucent red
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                                            width = stroke.brushSize,
                                            cap = StrokeCap.Round,
                                            join = androidx.compose.ui.graphics.StrokeJoin.Round
                                        )
                                    )
                                }
                            }

                            // Render AI Detected Watermark Rectangles overlay
                            if (activeTool == "AUTO") {
                                for (box in detectedBoxes) {
                                    val left = (box.x / 100f) * imgW
                                    val top = (box.y / 100f) * imgH
                                    val width = (box.width / 100f) * imgW
                                    val height = (box.height / 100f) * imgH

                                    // Draw transparent red rect
                                    drawRect(
                                        color = Color(0x66EF4444),
                                        topLeft = Offset(left, top),
                                        size = Size(width, height)
                                    )

                                    // Draw glowing neon emerald borders on tracked blocks
                                    drawRect(
                                        color = Color(0xFF10B981),
                                        topLeft = Offset(left, top),
                                        size = Size(width, height),
                                        style = Stroke(width = 3f / scale)
                                    )
                                }
                            }

                            drawContext.canvas.restore()
                        }
                    }
                } ?: Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF10B981))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// --- VIDEO EDITING WORKSPACE SCREEN ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditVideoScreen(onBack: () -> Unit) {
    val viewModel: CleanAppViewModel = viewModel()
    val activeTool by viewModel.activeTool.collectAsStateWithLifecycle()
    val brushSize by viewModel.brushSize.collectAsStateWithLifecycle()
    val targetUri = viewModel.selectedVideoUri
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Video Clean Workspace", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A))
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                // Brush Thickness slider if manual brush mode is selected
                if (activeTool == "MANUAL") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Brush scale",
                            tint = Color.LightGray,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Size: ${brushSize.toInt()}",
                            color = Color.LightGray,
                            style = MaterialTheme.typography.labelSmall
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Slider(
                            value = brushSize,
                            onValueChange = { viewModel.setBrushSize(it) },
                            valueRange = 10f..120f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF10B981),
                                activeTrackColor = Color(0xFF10B981)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = { viewModel.applyVideoClean() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("apply_clean_video_button")
                ) {
                    Icon(Icons.Default.Star, contentDescription = "Clean")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Apply Video Clean", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        },
        containerColor = Color(0xFF0B0F17)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // Mode Select Toggle Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .background(Color(0xFF141B2D), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.setActiveTool("MANUAL") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (activeTool == "MANUAL") Color(0xFF1E293B) else Color.Transparent,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Brush Mode", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Manual Mask", style = MaterialTheme.typography.labelMedium)
                }

                Button(
                    onClick = { viewModel.setActiveTool("AUTO") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (activeTool == "AUTO") Color(0xFF1E293B) else Color.Transparent,
                        contentColor = if (activeTool == "AUTO") Color.White else Color.Gray
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "AI Mode",
                        tint = if (activeTool == "AUTO") Color(0xFF10B981) else Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Auto AI Scan", style = MaterialTheme.typography.labelMedium)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Player Simulation view
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(Color.Black, RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFF1F2937), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                // For videos, display player preview with watermark overlay representation
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Simulated Player",
                        tint = Color.LightGray,
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = targetUri?.lastPathSegment ?: "video_file.mp4",
                        color = Color.LightGray,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Real-time timeline playback active",
                        color = Color.Gray,
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                // AI tracked bounding box mask frame simulator
                if (activeTool == "AUTO") {
                    Box(
                        modifier = Modifier
                            .padding(24.dp)
                            .size(width = 120.dp, height = 50.dp)
                            .background(Color(0xFFEF4444).copy(alpha = 0.5f))
                            .border(2.dp, Color(0xFF10B981))
                            .align(Alignment.BottomEnd),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "[WATERMARK]",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// --- BEFORE/AFTER PREVIEW & EXPORT SCREEN ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewExportScreen(onBack: () -> Unit) {
    val viewModel: CleanAppViewModel = viewModel()
    val originalBmp = viewModel.originalBitmap
    val inpaintedBmp by viewModel.inpaintedBitmap.collectAsStateWithLifecycle()
    val selectedVideoUri = viewModel.selectedVideoUri
    val sliderPos by viewModel.comparisonSliderPos.collectAsStateWithLifecycle()
    val finalSavedType = viewModel.finalSavedType
    val context = LocalContext.current

    var saveSuccessMessage by remember { mutableStateOf<String?>(null) }
    var saveSuccessPath by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Preview before Saving", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF0B0F17)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Interactive 60fps Split Screen slider comparison
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black, RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFF1F2937), RoundedCornerShape(16.dp))
                    .clipToBounds(),
                contentAlignment = Alignment.Center
            ) {
                if (finalSavedType == "IMAGE") {
                    originalBmp?.let { orig ->
                        inpaintedBmp?.let { clean ->
                            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                val viewW = constraints.maxWidth.toFloat()
                                val viewH = constraints.maxHeight.toFloat()

                                val imgW = orig.width.toFloat()
                                val imgH = orig.height.toFloat()

                                val scale = (viewW / imgW).coerceAtMost(viewH / imgH)
                                val scaledW = imgW * scale
                                val scaledH = imgH * scale
                                val offsetX = (viewW - scaledW) / 2f
                                val offsetY = (viewH - scaledH) / 2f

                                // Width coordinates of slider
                                val sliderX = sliderPos * viewW

                                Box(modifier = Modifier.fillMaxSize()) {
                                    // Layer 1: Draw Clean (After) Version
                                    Image(
                                        bitmap = clean.asImageBitmap(),
                                        contentDescription = "After Clean Version",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    // Layer 2: Draw Original (Before) clipped at slider boundary
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .width(with(LocalDensity.current) { sliderX.toDp() })
                                            .clipToBounds()
                                    ) {
                                        Image(
                                            bitmap = orig.asImageBitmap(),
                                            contentDescription = "Before Original Version",
                                            contentScale = ContentScale.FillHeight,
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .width(with(LocalDensity.current) { viewW.toDp() }),
                                            alignment = Alignment.CenterStart
                                        )
                                    }

                                    // Intercept drag gestures inside the entire screen to move comparison slider
                                    Canvas(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .pointerInput(Unit) {
                                                detectDragGestures { change, _ ->
                                                    change.consume()
                                                    val fraction = (change.position.x / viewW).coerceIn(0f, 1f)
                                                    viewModel.setComparisonSlider(fraction)
                                                }
                                            }
                                    ) {
                                        // Draw white vertical divider splitting layout
                                        drawLine(
                                            color = Color.White,
                                            start = Offset(sliderX, 0f),
                                            end = Offset(sliderX, viewH),
                                            strokeWidth = 4f
                                        )

                                        // Draw a glowing hub handle button representing Slider grip
                                        drawCircle(
                                            color = Color(0xFF10B981),
                                            radius = 28f,
                                            center = Offset(sliderX, viewH / 2f)
                                        )
                                        drawCircle(
                                            color = Color.White,
                                            radius = 16f,
                                            center = Offset(sliderX, viewH / 2f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Video comparison simulation view
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Success video clean overlay",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(60.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Video Cleaning Completed!",
                            style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "All spotted watermark and text layers reconstructed perfectly.",
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Label indicating Before/After directions
            if (finalSavedType == "IMAGE") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("← BEFORE Original", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                    Text("Drag slider to inspect details", color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
                    Text("AFTER Cleaned →", color = Color(0xFF10B981), style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Saving Panel Success banner Card
            if (saveSuccessMessage != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981).copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, Color(0xFF10B981))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Check, "Checked", tint = Color(0xFF10B981))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("SUCCESSFULLY SAVED!", color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "File saved at: $saveSuccessPath",
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    try {
                                        val f = File(saveSuccessPath!!)
                                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                                        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, if (finalSavedType == "IMAGE") "image/jpeg" else "video/mp4")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(viewIntent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Error opening: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF242F41)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Open File", style = MaterialTheme.typography.labelMedium)
                            }

                            Button(
                                onClick = {
                                    try {
                                        val f = File(saveSuccessPath!!)
                                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = if (finalSavedType == "IMAGE") "image/jpeg" else "video/mp4"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share cleaned media"))
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Share", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("Back Home", fontWeight = FontWeight.Bold, color = Color.White)
                }

                if (saveSuccessMessage == null) {
                    Button(
                        onClick = {
                            viewModel.saveMediaToGallery(context) { path ->
                                if (path.startsWith("Error")) {
                                    Toast.makeText(context, path, Toast.LENGTH_LONG).show()
                                } else {
                                    saveSuccessMessage = "Successfully exported to Pictures/CleanAI."
                                    saveSuccessPath = path
                                    Toast.makeText(context, "Exported successfully!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.2f).height(48.dp).testTag("save_to_gallery_button")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Checked")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save to Gallery", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}
