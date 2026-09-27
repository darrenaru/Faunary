package com.faunary.app.ui.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.faunary.app.data.IosPhotoProcessor
import com.faunary.app.ui.platform.topViewController
import com.faunary.app.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import platform.Foundation.writeToFile
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.darwin.NSObject

/**
 * Opens the system camera (the photo library on the simulator, which has no camera). [onCaptured] gets
 * the path of the saved JPEG, still in its original orientation; the review screen normalises it.
 */
@Composable
fun rememberIosCamera(onCaptured: (path: String) -> Unit, onCancelled: () -> Unit = {}): () -> Unit {
    val photos = koinInject<IosPhotoProcessor>()
    val scope = rememberCoroutineScope()
    val currentOnCaptured by rememberUpdatedState(onCaptured)
    val currentOnCancelled by rememberUpdatedState(onCancelled)
    // Kept for the composable's lifetime: UIImagePickerController holds its delegate weakly.
    val delegate = remember {
        PickerDelegate(
            onImage = { image ->
                scope.launch {
                    val path = withContext(Dispatchers.Default) {
                        photos.newCapturePath().takeIf { UIImageJPEGRepresentation(image, CAPTURE_QUALITY)?.writeToFile(it, atomically = true) == true }
                    }
                    if (path != null) currentOnCaptured(path) else {
                        Log.w("FaunaryCamera", "could not save the photo")
                        currentOnCancelled()
                    }
                }
            },
            onCancel = { currentOnCancelled() },
        )
    }
    return remember {
        {
            val camera = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
            val picker = UIImagePickerController().apply {
                sourceType = if (UIImagePickerController.isSourceTypeAvailable(camera)) camera
                else UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
                this.delegate = delegate
            }
            topViewController()?.presentViewController(picker, animated = true, completion = null)
        }
    }
}

private class PickerDelegate(
    private val onImage: (UIImage) -> Unit,
    private val onCancel: () -> Unit,
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
    override fun imagePickerController(picker: UIImagePickerController, didFinishPickingMediaWithInfo: Map<Any?, *>) {
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        picker.dismissViewControllerAnimated(true, completion = null)
        if (image != null) onImage(image) else onCancel()
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
        onCancel()
    }
}

/** High quality: this file is re-encoded once more when the find is stored. */
private const val CAPTURE_QUALITY = 0.95
