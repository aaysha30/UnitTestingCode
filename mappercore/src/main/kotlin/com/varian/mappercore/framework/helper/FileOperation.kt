package com.varian.mappercore.framework.helper

import com.varian.mappercore.constant.RelativePathConstant
import java.io.File
import java.nio.file.Paths

class FileOperation {

    companion object {
        private var currentPath: String? = null

        // ToDo: Remove this method, Added only for Test purpose to set base path
        fun setCurrentBasePath(currentPath: String) {
            this.currentPath = currentPath
        }

        private fun getCurrentBasePath(): String {
            if (currentPath == null) {
                val executingJar = File(
                    FileOperation::class.java.protectionDomain.codeSource.location.toURI().path
                )
                currentPath = executingJar.parentFile.absolutePath
            }
            return currentPath!!
        }

        fun getFullPath(fileName: String): String {
            return Paths.get(getCurrentBasePath(), fileName).toString()
        }

        fun getConfigurationFilePath(): String {
            return getFullPath(RelativePathConstant.CONFIGURATION_PATH)
        }

        fun getImportFilePath(): String {
            return getFullPath(RelativePathConstant.IMPORT_PATH)
        }

        fun getDslDirectoryPath(): String {
            return getFullPath(RelativePathConstant.DSL_PATH)
        }

        fun getFile(fileFullPath: String): File {
            return File(fileFullPath)
        }

        fun getDslDirectory(): File {
            return getFile(getDslDirectoryPath())
        }

        fun getConfigurationFile(): File {
            return getFile(getConfigurationFilePath())
        }

        fun getImportFile(): File {
            return getFile(getImportFilePath())
        }
    }
}
