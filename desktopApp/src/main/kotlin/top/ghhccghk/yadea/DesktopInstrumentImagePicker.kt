package top.ghhccghk.yadea

import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import top.ghhccghk.yadea.instrumentskin.InstrumentImagePicker

class DesktopInstrumentImagePicker : InstrumentImagePicker {
    override val canPick: Boolean = true

    override fun pick(onResult: (String?) -> Unit) {
        val chooser = JFileChooser().apply {
            dialogTitle = "选择仪表图片"
            fileFilter = FileNameExtensionFilter(
                "图片文件",
                "jpg",
                "jpeg",
                "png",
                "webp",
                "bmp",
            )
        }
        val result = chooser.showOpenDialog(null)
        onResult(
            if (result == JFileChooser.APPROVE_OPTION) {
                chooser.selectedFile?.absolutePath
            } else {
                null
            },
        )
    }
}
